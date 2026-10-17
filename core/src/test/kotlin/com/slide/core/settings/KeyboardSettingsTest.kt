package com.slide.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSettingsTest {

    /** What the repository falls back to when no setting has ever been read. */
    private val unreadableSettings = KeyboardSettings(
        incognitoModeEnabled = true,
        learnedDataClearEpoch = KeyboardSettings.LEARNED_DATA_EPOCH_UNKNOWN,
    )

    @Test
    fun `autocorrection is active only when both controls are enabled`() {
        assertTrue(KeyboardSettings().isAutocorrectionActive)
        assertFalse(
            KeyboardSettings(suggestionsEnabled = false, autocorrectEnabled = true)
                .isAutocorrectionActive,
        )
        assertFalse(
            KeyboardSettings(suggestionsEnabled = true, autocorrectEnabled = false)
                .isAutocorrectionActive,
        )
        assertFalse(
            KeyboardSettings(suggestionsEnabled = false, autocorrectEnabled = false)
                .isAutocorrectionActive,
        )
    }

    @Test
    fun `privacy controls start without changing or clearing user data`() {
        val settings = KeyboardSettings()

        assertFalse(settings.incognitoModeEnabled)
        assertEquals(0L, settings.learnedDataClearEpoch)
    }

    @Test
    fun `legacy migration copies settings but never recent emoji or unknown private data`() {
        val theme = stringPreferencesKey("theme_id")
        val epoch = longPreferencesKey("learned_data_clear_epoch")
        val recentEmoji = stringPreferencesKey("recent_emoji")
        val unknownUsage = stringPreferencesKey("future_usage_history")
        val legacy = mutablePreferencesOf(
            theme to "midnight",
            epoch to 9L,
            recentEmoji to "🙂\u001F❤️",
            unknownUsage to "private",
        )

        val migrated = migrateLegacySettings(legacy, emptyPreferences())

        assertEquals("midnight", migrated[theme])
        assertEquals(9L, migrated[epoch])
        assertEquals(null, migrated[recentEmoji])
        assertEquals(null, migrated[unknownUsage])
        assertEquals(
            true,
            migrated[booleanPreferencesKey("legacy_settings_migration_complete")],
        )
    }

    @Test
    fun `legacy migration never overwrites a setting already written to the new store`() {
        val theme = stringPreferencesKey("theme_id")
        val legacy = mutablePreferencesOf(theme to "legacy")
        val current = mutablePreferencesOf(theme to "current")

        val migrated = migrateLegacySettings(legacy, current)

        assertEquals("current", migrated[theme])
    }

    @Test
    fun `legacy migration skips an allowlisted name stored under the wrong type`() {
        val malformedTheme = booleanPreferencesKey("theme_id")
        val canonicalTheme = stringPreferencesKey("theme_id")
        val malformedEpoch = stringPreferencesKey("learned_data_clear_epoch")
        val canonicalEpoch = longPreferencesKey("learned_data_clear_epoch")
        val legacy = mutablePreferencesOf(
            malformedTheme to true,
            malformedEpoch to "nine",
        )

        val migrated = migrateLegacySettings(legacy, emptyPreferences())

        assertEquals(null, migrated[canonicalTheme])
        assertEquals(null, migrated[canonicalEpoch])
    }

    @Test
    fun `corruption reset cannot reimport the stale pre-upgrade settings snapshot`() {
        val theme = stringPreferencesKey("theme_id")
        val incognito = booleanPreferencesKey("incognito_mode_enabled")
        val recentEmoji = stringPreferencesKey("recent_emoji")
        val unknownUsage = stringPreferencesKey("future_usage_history")
        val legacy = mutablePreferencesOf(
            theme to "legacy",
            incognito to false,
            recentEmoji to "🙂\u001F❤️",
            unknownUsage to "private",
        )

        val firstMigration = migrateLegacySettings(legacy, emptyPreferences())
        val cleanedLegacy = removeMigratedLegacySettings(legacy)
        // Model the new DataStore's corruption handler replacing its contents with empty prefs.
        val afterCorruption = migrateLegacySettings(cleanedLegacy, emptyPreferences())

        assertEquals("legacy", firstMigration[theme])
        assertEquals(false, firstMigration[incognito])
        assertEquals(null, cleanedLegacy[theme])
        assertEquals(null, cleanedLegacy[incognito])
        assertEquals("🙂\u001F❤️", cleanedLegacy[recentEmoji])
        assertEquals("private", cleanedLegacy[unknownUsage])
        assertEquals(null, afterCorruption[theme])
        assertEquals(null, afterCorruption[incognito])
        assertEquals(true, afterCorruption[booleanPreferencesKey("legacy_settings_migration_complete")])
    }

    @Test
    fun `restart retries legacy scrub after migration output committed before cleanup`() {
        val theme = stringPreferencesKey("theme_id")
        val legacy = mutablePreferencesOf(theme to "legacy")
        val current = migrateLegacySettings(legacy, emptyPreferences()).toMutablePreferences().apply {
            this[theme] = "newer"
        }.toPreferences()

        // Model a process death after DataStore committed migrate(), but before cleanUp().
        assertTrue(legacySettingsMigrationRequired(legacy, current))
        val retried = migrateLegacySettings(legacy, current)
        assertEquals("newer", retried[theme])

        val cleaned = removeMigratedLegacySettings(legacy)
        assertFalse(legacySettingsMigrationRequired(cleaned, retried))
    }

    @Test
    fun `clear waits for an in-flight recent emoji migration and wins`() = runBlocking {
        val gate = RecentEmojiMigrationGate()
        var legacyHistory: String? = "🙂"
        var privateHistory: String? = null
        val legacyRead = CompletableDeferred<Unit>()
        val continueMigration = CompletableDeferred<Unit>()

        val migration = async {
            gate.migrate {
                val captured = legacyHistory
                legacyRead.complete(Unit)
                continueMigration.await()
                if (privateHistory == null) privateHistory = captured
                legacyHistory = null
                true
            }
        }
        legacyRead.await()

        val clear = async {
            gate.clear {
                legacyHistory = null
                privateHistory = null
            }
        }
        yield()
        assertFalse("clear must wait for the migration transaction", clear.isCompleted)

        continueMigration.complete(Unit)
        migration.await()
        clear.await()

        assertEquals(null, legacyHistory)
        assertEquals(null, privateHistory)
    }

    // region Surviving preference read failures

    /**
     * The defaults this used to fall back to carry `learnedDataClearEpoch = 0`, and the IME reads
     * a change in that number as a request to throw away everything it has learned. One unreadable
     * moment must not look like the user asking for that.
     */
    @Test
    fun `a read failure keeps the settings already read and never rewinds the clear epoch`() =
        runBlocking {
            val stored = KeyboardSettings(themeId = "midnight", learnedDataClearEpoch = 7L)
            val changed = stored.copy(hapticEnabled = false)
            var subscriptions = 0
            val failures = mutableListOf<Throwable>()

            val settings = flow {
                if (subscriptions++ == 0) {
                    emit(stored)
                    throw IOException("the preferences file went away mid-read")
                }
                emit(changed)
            }.survivingReadFailures(
                fallback = { unreadableSettings },
                onFailure = { failures += it },
                retryDelayMillis = { 0L },
            )

            val seen = settings.take(2).toList()

            // The failure neither ended the flow nor replaced what had been read: the upstream was
            // resubscribed and the next real change still arrived.
            assertEquals(listOf(stored, changed), seen)
            assertEquals(2, subscriptions)
            assertEquals(1, failures.size)
            assertTrue(seen.none { it.learnedDataClearEpoch != 7L })
        }

    /** With nothing read yet there is no user state to keep, and no baseline to disturb. */
    @Test
    fun `only a first read failure falls back to defaults`() = runBlocking {
        val stored = KeyboardSettings(themeId = "midnight", learnedDataClearEpoch = 7L)
        var subscriptions = 0

        val settings = flow {
            if (subscriptions++ == 0) throw IOException("the preferences file is missing")
            emit(stored)
        }.survivingReadFailures(
            fallback = { unreadableSettings },
            onFailure = {},
            retryDelayMillis = { 0L },
        )

        assertEquals(listOf(unreadableSettings, stored), settings.take(2).toList())
    }

    /**
     * The fallback is followed by a retry that succeeds, and the real epoch it then reads is
     * usually larger than nothing — so a fallback carrying a plain `0` would make the first
     * successful read look like the user asking to clear everything they have taught the
     * keyboard. The sentinel is what stops the retry from being read as a request.
     */
    @Test
    fun `the fallback never carries an authoritative clear epoch`() = runBlocking {
        val stored = KeyboardSettings(themeId = "midnight", learnedDataClearEpoch = 7L)
        var subscriptions = 0

        val settings = flow {
            if (subscriptions++ == 0) throw IOException("the preferences file is missing")
            emit(stored)
        }.survivingReadFailures(
            fallback = { unreadableSettings },
            onFailure = {},
            retryDelayMillis = { 0L },
        )

        val seen = settings.take(2).toList()

        assertEquals(KeyboardSettings.LEARNED_DATA_EPOCH_UNKNOWN, seen.first().learnedDataClearEpoch)
        assertTrue(
            "an unread epoch has to be negative to be unmistakable",
            seen.first().learnedDataClearEpoch < 0L,
        )
        assertEquals(7L, seen.last().learnedDataClearEpoch)
    }

    /**
     * A ceiling that is never climbed back down turns one bad afternoon into a session that takes
     * half a minute to notice every later change.
     */
    @Test
    fun `the backoff starts again after a successful read`() = runBlocking {
        val delays = mutableListOf<Long>()
        var subscriptions = 0

        val settings = flow {
            when (subscriptions++) {
                0, 1 -> throw IOException("the preferences file is busy")
                // Reads once, then fails again: the read is what has to reset the count.
                2 -> {
                    emit(KeyboardSettings(themeId = "midnight"))
                    throw IOException("the preferences file is busy again")
                }
                else -> emit(KeyboardSettings(themeId = "dusk"))
            }
        }.survivingReadFailures(
            fallback = { unreadableSettings },
            onFailure = {},
            retryDelayMillis = { attempt ->
                delays += attempt
                0L
            },
        )

        // The fallback, the read that succeeds, and the read after the failure that follows it.
        settings.take(3).toList()

        assertEquals(listOf(0L, 1L, 0L), delays)
    }

    /** A bug in the mapping is not a bad file, and hiding it behind a retry loop would be worse. */
    @Test
    fun `a failure that is not an IO failure still propagates`() {
        val settings = flow<KeyboardSettings> { throw IllegalStateException("not an IO problem") }
            .survivingReadFailures(
                fallback = { KeyboardSettings() },
                onFailure = {},
                retryDelayMillis = { 0L },
            )

        assertThrows(IllegalStateException::class.java) { runBlocking { settings.toList() } }
    }

    @Test
    fun `retries back off and then stop growing`() {
        assertEquals(250L, preferenceRetryDelayMillis(0))
        assertEquals(500L, preferenceRetryDelayMillis(1))
        assertEquals(32_000L, preferenceRetryDelayMillis(7))
        assertEquals(32_000L, preferenceRetryDelayMillis(1_000))
    }

    // endregion

    // region Persistence round trip

    @Test
    fun `nonfinite stored numbers use their defaults without losing other settings`() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val preferences = mutablePreferencesOf(
                floatPreferencesKey("key_height_scale") to invalid,
                floatPreferencesKey("bottom_padding_dp") to invalid,
                floatPreferencesKey("haptic_strength") to invalid,
                floatPreferencesKey("sound_volume") to invalid,
                booleanPreferencesKey("incognito_mode_enabled") to true,
                longPreferencesKey("learned_data_clear_epoch") to 12L,
            )

            assertEquals(
                KeyboardSettings(incognitoModeEnabled = true, learnedDataClearEpoch = 12L),
                preferences.toKeyboardSettings(),
            )
        }
    }

    @Test
    fun `stored numbers stay within keyboard and feedback limits`() {
        val tooLow = mutablePreferencesOf(
            floatPreferencesKey("key_height_scale") to -1f,
            floatPreferencesKey("portrait_board_offset_dp") to -100f,
            floatPreferencesKey("bottom_padding_dp") to -10f,
            floatPreferencesKey("haptic_strength") to -1f,
            floatPreferencesKey("sound_volume") to -1f,
        ).toKeyboardSettings()
        assertEquals(0.7f, tooLow.keyHeightScale, 0f)
        assertEquals(-64f, tooLow.portraitBoardOffsetDp, 0f)
        // Nothing has been set for landscape, so it takes the single value this install was carrying —
        // and -10 is inside the range, so it arrives as itself rather than clamped to the floor.
        assertEquals(-10f, tooLow.landscapeBoardOffsetDp, 0f)
        assertEquals(0f, tooLow.hapticStrength, 0f)
        assertEquals(0f, tooLow.soundVolume, 0f)

        val tooHigh = mutablePreferencesOf(
            floatPreferencesKey("key_height_scale") to Float.MAX_VALUE,
            floatPreferencesKey("portrait_board_offset_dp") to Float.MAX_VALUE,
            floatPreferencesKey("landscape_board_offset_dp") to Float.MAX_VALUE,
            floatPreferencesKey("haptic_strength") to Float.MAX_VALUE,
            floatPreferencesKey("sound_volume") to Float.MAX_VALUE,
        ).toKeyboardSettings()
        assertEquals(1.4f, tooHigh.keyHeightScale, 0f)
        assertEquals(64f, tooHigh.portraitBoardOffsetDp, 0f)
        assertEquals(64f, tooHigh.landscapeBoardOffsetDp, 0f)
        assertEquals(1f, tooHigh.hapticStrength, 0f)
        assertEquals(1f, tooHigh.soundVolume, 0f)
    }

    @Test
    fun `the keyboard size stop round-trips and a stored value outside the ladder lands on a stop`() {
        // The step is stored as the integer stop under one key, in the same file as every other
        // setting — which is what makes it live to a running keyboard and eligible for backup.
        val key = intPreferencesKey("keyboard_size_step")
        val written = mutablePreferencesOf()
        written.writeKeyboardSettings(KeyboardSettings(keyboardSizeStep = KeyboardSize.MAX_STEP))
        assertEquals(KeyboardSize.MAX_STEP, written[key])
        assertEquals(KeyboardSize.MAX_STEP, written.toPreferences().toKeyboardSettings().keyboardSizeStep)

        // A value outside the ladder — hand-edited, restored from an older backup, or written by
        // something that only knows the key — is held to the nearest stop rather than accepted: the
        // keyboard must not resize to a size nobody chose, and the largest stop is the ceiling.
        assertEquals(KeyboardSize.MAX_STEP, mutablePreferencesOf(key to 7).toKeyboardSettings().keyboardSizeStep)
        assertEquals(KeyboardSize.MIN_STEP, mutablePreferencesOf(key to -9).toKeyboardSettings().keyboardSizeStep)
        assertEquals(KeyboardSize.DEFAULT_STEP, mutablePreferencesOf(key to 0).toKeyboardSettings().keyboardSizeStep)

        // Never stored at all, the board is the approved one.
        assertEquals(KeyboardSize.DEFAULT_STEP, mutablePreferencesOf().toKeyboardSettings().keyboardSizeStep)
        assertEquals(KeyboardSize.DEFAULT_STEP, KeyboardSettings().keyboardSizeStep)
    }

    @Test
    fun `every settings field survives a write and read round trip`() {
        val defaults = KeyboardSettings()
        val modified = KeyboardSettings(
            themeId = "midnight",
            colourPresetId = "cobalt",
            colourSeedHue = 200f,
            colourSeedSaturation = 0.7f,
            colourSeedLightness = 0.4f,
            followSystemDarkMode = false,
            languageTag = "es-ES",
            showKeyBorders = true,
            showKeyPreview = false,
            // The digit row is off by default now, so its non-default value is on.
            showNumberRow = true,
            keyHeightScale = 1.3f,
            portraitBoardOffsetDp = 24f,
            landscapeBoardOffsetDp = -32f,
            hapticEnabled = false,
            hapticStrength = 0.9f,
            soundEnabled = true,
            soundVolume = 0.8f,
            gestureTypingEnabled = false,
            suggestionsEnabled = false,
            autocorrectEnabled = false,
            incognitoModeEnabled = true,
            learnedDataClearEpoch = 7L,
            blockOffensiveWords = false,
            voiceModelId = "BaseEn",
            autoCapitalize = false,
            doubleSpacePeriod = false,
            emojiSkinTone = 3,
            keyboardSizeStep = KeyboardSize.MAX_STEP,
            updateChecksEnabled = true,
            includeAlphaUpdates = true,
            micButtonEnabled = false,
        )

        // Every constructor field must differ from its default here, or the round trip below
        // could pass without exercising the field at all. Reflection walks the backing fields,
        // so adding a new setting fails this test until the field is varied above — and then
        // the round trip fails until the write, read, and backup lists all know about it.
        KeyboardSettings::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                assertNotEquals(
                    "Field '${field.name}' must be given a non-default value in this test",
                    field.get(defaults),
                    field.get(modified),
                )
            }

        val written = mutablePreferencesOf()
        written.writeKeyboardSettings(modified)
        assertEquals(modified, written.toPreferences().toKeyboardSettings())
    }

    @Test
    fun `every stored settings key is registered as backup eligible`() {
        val written = mutablePreferencesOf()
        written.writeKeyboardSettings(KeyboardSettings())
        written.asMap().forEach { (key, value) ->
            assertTrue(
                "Key '${key.name}' is missing from isBackupEligibleSetting",
                isBackupEligibleSetting(key.name, value),
            )
        }
    }

    // endregion

    @Test
    fun `the two placements are separate, and the one before them seeds both`() {
        // Written and read back independently: tuning one orientation must not move the other.
        val written = mutablePreferencesOf().apply {
            writeKeyboardSettings(KeyboardSettings().copy(portraitBoardOffsetDp = 40f, landscapeBoardOffsetDp = -24f))
        }
        val read = written.toKeyboardSettings()
        assertEquals(40f, read.portraitBoardOffsetDp, 0f)
        assertEquals(-24f, read.landscapeBoardOffsetDp, 0f)
        assertEquals("the orientation picks the number", 40f, read.boardOffsetDp(landscape = false), 0f)
        assertEquals(-24f, read.boardOffsetDp(landscape = true), 0f)

        // An install from before the split: one number, no orientation. It seeds both rather than being
        // dropped, because the space under the keys was chosen for the hand.
        // The stored names are spelled out: they are what an installed copy already holds, and a rename
        // here is a migration that silently stops working.
        val wasSingle = floatPreferencesKey("bottom_padding_dp")
        val wasPortrait = floatPreferencesKey("portrait_board_offset_dp")
        val older = mutablePreferencesOf(wasSingle to 24f).toKeyboardSettings()
        assertEquals(24f, older.portraitBoardOffsetDp, 0f)
        assertEquals(24f, older.landscapeBoardOffsetDp, 0f)

        // And once the split has been used, the old value stops speaking for either of them.
        val both = mutablePreferencesOf(wasSingle to 24f, wasPortrait to 8f).toKeyboardSettings()
        assertEquals(8f, both.portraitBoardOffsetDp, 0f)
        assertEquals(24f, both.landscapeBoardOffsetDp, 0f)
    }
}
