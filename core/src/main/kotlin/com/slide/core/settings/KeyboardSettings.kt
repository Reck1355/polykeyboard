package com.slide.core.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStore
import com.slide.core.emoji.EmojiData
import com.slide.core.theme.ColourChoice
import com.slide.core.theme.Colours
import com.slide.core.theme.Themes
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User-facing keyboard preferences. */
data class KeyboardSettings(
    val themeId: String = Themes.ID_DYNAMIC,
    /**
     * The keyboard's colour choice: [Colours.ID_JADE] by default, a built-in preset id, or
     * [Colours.ID_CUSTOM] for the seed below.
     *
     * This is the single colour control. The hex board and every chrome surface resolve their
     * colours from it (see [colourChoice]), so a fresh install shows jade and nothing else has to
     * agree on a colour separately.
     */
    val colourPresetId: String = Colours.ID_JADE,
    /** Custom seed hue in 0..360, read only while [colourPresetId] is [Colours.ID_CUSTOM]. */
    val colourSeedHue: Float = Colours.DEFAULT_SEED_HUE,
    /** Custom seed saturation in 0..1. */
    val colourSeedSaturation: Float = Colours.DEFAULT_SEED_SATURATION,
    /** Custom seed lightness in 0..1. */
    val colourSeedLightness: Float = Colours.DEFAULT_SEED_LIGHTNESS,
    /**
     * Whether an *unresolvable* [themeId] falls back to Dark when the system is dark, rather
     * than always to Light.
     *
     * This is a resilience policy for a corrupt or legacy theme id, not a user preference:
     * explicit preset selections always win, and Dynamic follows the system on its own.
     * Deliberately not surfaced in any settings UI — a switch here would read as "follow system
     * dark mode" while controlling only a fallback almost nobody ever hits.
     */
    val followSystemDarkMode: Boolean = true,
    /**
     * The language the user chose, as a subtype tag (`en-US`, `es-ES`), or empty before they have
     * chosen one.
     *
     * This is the keyboard's own answer to "which language am I typing in", and it exists because
     * Android's keyboard-language list is not a control a person can find: a subtype declared in
     * `method.xml` is not enabled until the user ticks it in a settings screen three levels deep,
     * so the globe key and the board had nothing to switch between. Empty is not English: it means
     * no choice has been made, and the reported subtype decides — which keeps a fresh install, and
     * an install that has only ever used one language, behaving exactly as before.
     */
    val languageTag: String = "",
    val showKeyBorders: Boolean = false,
    val showKeyPreview: Boolean = true,
    val showNumberRow: Boolean = true,
    /** Multiplier on the default key height, 0.7–1.4. */
    val keyHeightScale: Float = 1f,
    /**
     * The keyboard-size ladder stop — [KeyboardSize.MIN_STEP]..[KeyboardSize.MAX_STEP], with
     * [KeyboardSize.DEFAULT_STEP] the board exactly as it was approved.
     *
     * One stop of the ladder is one tenth of the size. `HexBoard.sizeMultiplier` reads it, so a
     * single multiplier derives both orientations at once: portrait's hexagon height and landscape's
     * share of the display height. It is stored as the integer stop rather than as a multiplier so
     * the control's five positions are exactly recoverable and a hand-edited value can be clamped
     * back to the ladder instead of quietly resizing the board to something nobody chose.
     */
    val keyboardSizeStep: Int = KeyboardSize.DEFAULT_STEP,
    /**
     * Where the portrait board sits, in dp. Positive floats it up off the bottom edge; negative drops it
     * below where it would otherwise sit, by leaving the room above it instead.
     *
     * Separate from portrait's twin because the two boards are different keyboards on a different axis:
     * portrait is a tall single board reaching down to the bottom edge, landscape is a flat split that a
     * thumb rests on from the side, and one number for both means changing one to fix the other. Zero is
     * the bottom edge, which is where a keyboard is expected to be, so neither orientation moves until it
     * is asked to.
     */
    val portraitBoardOffsetDp: Float = 0f,
    /** Where the landscape board sits, in dp. Same axis and same zero as [portraitBoardOffsetDp]. */
    val landscapeBoardOffsetDp: Float = 0f,
    val hapticEnabled: Boolean = true,
    /** 0–1, scaled to a vibration amplitude. */
    val hapticStrength: Float = 0.5f,
    val soundEnabled: Boolean = false,
    val soundVolume: Float = 0.5f,
    val gestureTypingEnabled: Boolean = true,
    /** Shows word candidates above the keys as the user types. */
    val suggestionsEnabled: Boolean = true,
    /**
     * Lets a separator replace a misspelled word with the strip's first candidate.
     *
     * Separate from [suggestionsEnabled] because some people want to see the candidates and pick
     * them by hand without the keyboard ever changing a word on its own.
     */
    val autocorrectEnabled: Boolean = true,
    /**
     * Stops Slide from learning new words and word pairs until the user turns it off again.
     *
     * This is separate from editor-requested incognito mode so the user can ask for the same
     * privacy in any app. Existing learned data remains available until it is explicitly cleared.
     */
    val incognitoModeEnabled: Boolean = false,
    /**
     * Changes whenever the settings app durably requests clearing learned words and word pairs.
     *
     * The IME observes this value so it can also discard its live in-memory copies. It is an epoch
     * rather than a Boolean because every clear action must be observable, including consecutive
     * requests.
     *
     * Two rules go with it, and both exist because the number outlives the process that reads it:
     * - A **negative** value is [LEARNED_DATA_EPOCH_UNKNOWN] — no epoch was read at all. It is
     *   never a clear request and never a baseline to compare later values against.
     * - A non-negative value is authoritative even when it is *lower* than the last one seen. A
     *   corruption reset (see the store's `ReplaceFileCorruptionHandler`) legitimately puts the
     *   epoch back to zero, so a decrease means "start counting again from here", not "clear".
     */
    val learnedDataClearEpoch: Long = 0L,
    /**
     * Withholds slurs and profanity from swipe and suggestion results.
     *
     * On by default, matching Gboard: an accidental obscenity committed into a message is a much
     * worse surprise than having to type a deliberate one by hand.
     */
    val blockOffensiveWords: Boolean = true,
    /**
     * Which packaged speech model voice typing uses, as a `WhisperModel` name.
     *
     * Stored as a plain string rather than the enum so `:core` need not depend on `:asr`; an
     * unrecognised value falls back to the default.
     */
    val voiceModelId: String = "",
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    /**
     * Skin tone applied to every emoji that has one, or [EmojiData.TONE_DEFAULT] for the yellow
     * form. One setting for the whole picker, as in Gboard, rather than a choice per emoji.
     */
    val emojiSkinTone: Int = EmojiData.TONE_DEFAULT,
    /** Explicit opt-in: update checks are the only feature that contacts GitHub. */
    val updateChecksEnabled: Boolean = false,
    /** Alpha builds are intentionally opt-in even while Slide itself is pre-1.0. */
    val includeAlphaUpdates: Boolean = false,
) {
    /** Autocorrection can only operate while the suggestion pipeline and strip are enabled. */
    val isAutocorrectionActive: Boolean
        get() = suggestionsEnabled && autocorrectEnabled

    /** The colour choice these fields make up, in the form the palette resolver consumes. */
    val colourChoice: ColourChoice
        get() = ColourChoice(
            presetId = colourPresetId,
            seedHue = colourSeedHue,
            seedSaturation = colourSeedSaturation,
            seedLightness = colourSeedLightness,
        )

    companion object {
        /**
         * [learnedDataClearEpoch] for settings that stand in for a read that never happened.
         *
         * A stored epoch is a plain counter, so any real number the fallback could carry is a
         * number the IME might later see rise past — and a rise is how the user asks for their
         * learned words to be thrown away. There is no safe non-negative value to invent, so the
         * fallback carries one that cannot be mistaken for an answer: consumers must neither
         * clear on it nor take it as the baseline for the real value that follows.
         */
        const val LEARNED_DATA_EPOCH_UNKNOWN = -1L
    }
}

/**
 * A file damaged by a power cut is replaced with an empty one rather than thrown from for ever.
 *
 * Without this, one truncated write makes every later read fail, and the keyboard is left crashing
 * or running on fallbacks at every start with no way back. Losing preferences is recoverable: the
 * user sets them again.
 *
 * It also costs no learned data, and that is the half worth spelling out, because a reset puts
 * [KeyboardSettings.learnedDataClearEpoch] back to zero underneath a running IME whose baseline is
 * some larger N. The contract consumers implement is therefore:
 * - a **rise** in an authoritative (non-negative) epoch is a clear request;
 * - a **fall** in an authoritative epoch is a corruption reset, and becomes the new baseline
 *   without clearing anything — otherwise the user's next clear, writing 0 to 1, would count as
 *   no change against the old baseline and be ignored by the session it was meant for;
 * - a **negative** epoch is [KeyboardSettings.LEARNED_DATA_EPOCH_UNKNOWN], which only the
 *   never-read fallback below produces, and is neither of those things.
 */
private fun preferencesCorruptionHandler() =
    ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

/** Legacy settings shared a file with private recent-emoji usage; it stays excluded from backup. */
private val Context.legacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_settings",
    corruptionHandler = preferencesCorruptionHandler(),
)

/** Current, non-sensitive settings have their own backup-eligible file. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keyboard_settings",
    corruptionHandler = preferencesCorruptionHandler(),
    produceMigrations = { context ->
        listOf(LegacySettingsMigration(context.legacyDataStore))
    },
)

private class LegacySettingsMigration(
    private val legacyStore: DataStore<Preferences>,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        legacySettingsMigrationRequired(legacyStore.data.first(), currentData)

    override suspend fun migrate(currentData: Preferences): Preferences =
        migrateLegacySettings(legacyStore.data.first(), currentData)

    // Keep the old file for the independent recent-emoji migration, but remove every setting that
    // was eligible for copying. Otherwise a later corruption reset of the new store would lose its
    // completion bit and resurrect this stale pre-upgrade snapshot over newer privacy/preferences.
    override suspend fun cleanUp() {
        legacyStore.updateData(::removeMigratedLegacySettings)
    }
}

/** Copies only known user preferences, never usage history or an unknown future private key. */
internal fun migrateLegacySettings(
    legacy: Preferences,
    current: Preferences,
): Preferences {
    val migrated = current.toMutablePreferences()
    val currentNames = current.asMap().keys.mapTo(mutableSetOf()) { it.name }
    for ((key, value) in legacy.asMap()) {
        if (!isBackupEligibleSetting(key.name, value) || key.name in currentNames) continue
        @Suppress("UNCHECKED_CAST")
        migrated[key as Preferences.Key<Any>] = value
    }
    migrated[LEGACY_SETTINGS_MIGRATION_COMPLETE] = true
    return migrated.toPreferences()
}

/** Removes only settings the migration understands, preserving private/unknown legacy payloads. */
internal fun removeMigratedLegacySettings(legacy: Preferences): Preferences {
    val scrubbed = legacy.toMutablePreferences()
    for ((key, value) in legacy.asMap()) {
        if (!isBackupEligibleSetting(key.name, value)) continue
        @Suppress("UNCHECKED_CAST")
        scrubbed.remove(key as Preferences.Key<Any>)
    }
    return scrubbed.toPreferences()
}

/**
 * Cleanup is a separate DataStore migration phase and can fail after the new data was committed.
 * Retry while any understood legacy setting remains, even when the new store already says its copy
 * completed. [migrateLegacySettings] preserves every current value, so this retry only re-attempts
 * the stale-source scrub and can never overwrite a newer preference.
 */
internal fun legacySettingsMigrationRequired(
    legacy: Preferences,
    current: Preferences,
): Boolean = current[LEGACY_SETTINGS_MIGRATION_COMPLETE] != true ||
    legacy.asMap().any { (key, value) -> isBackupEligibleSetting(key.name, value) }

private val LEGACY_SETTINGS_MIGRATION_COMPLETE =
    booleanPreferencesKey("legacy_settings_migration_complete")

/** Preferences keys are name-based, so validate the erased value before copying the legacy key. */
internal fun isBackupEligibleSetting(name: String, value: Any): Boolean = when (name) {
    "theme_id", "voice_model", "colour_preset_id", "language_tag" -> value is String
    "key_height_scale", "haptic_strength", "sound_volume" -> value is Float
    "portrait_board_offset_dp", "landscape_board_offset_dp" -> value is Float
    // Still eligible although nothing writes it: an installed copy holds it, and a restore from an older
    // backup has to bring it back for `boardOffset` to seed both orientations from it.
    "bottom_padding_dp" -> value is Float
    "colour_seed_hue", "colour_seed_saturation", "colour_seed_lightness" -> value is Float
    "learned_data_clear_epoch" -> value is Long
    "emoji_skin_tone", "keyboard_size_step" -> value is Int
    "follow_system_dark",
    "key_borders",
    "key_preview",
    "number_row",
    "haptic_enabled",
    "sound_enabled",
    "gesture_typing",
    "suggestions_enabled",
    "autocorrect_enabled",
    "incognito_mode_enabled",
    "block_offensive_words",
    "auto_capitalize",
    "double_space_period",
    "update_checks_enabled",
    "include_alpha_updates" -> value is Boolean
    else -> false
}

/** One DataStore per file per process, rooted where Android backup cannot see it. */
private object NoBackupRecentEmojiStore {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()

    @Synchronized
    fun get(context: Context): DataStore<Preferences> {
        val file = File(context.noBackupFilesDir, "datastore/recent_emoji.preferences_pb")
        return stores.getOrPut(file.absolutePath) {
            PreferenceDataStoreFactory.create(
                corruptionHandler = preferencesCorruptionHandler(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = {
                    val directory = checkNotNull(file.parentFile)
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Could not create the no-backup emoji-history directory"
                    }
                    file
                },
            )
        }
    }
}

/**
 * One process-wide gate per recent-emoji file.
 *
 * [SettingsRepository] is created independently by the settings activity and IME. An instance
 * mutex therefore cannot prevent one repository from clearing history while another is midway
 * through copying the legacy value. Keying the gate by the actual no-backup path keeps those
 * repository instances in one transaction without conflating separate test/application roots.
 */
private object RecentEmojiMigrationCoordinator {
    private val gates = mutableMapOf<String, RecentEmojiMigrationGate>()

    @Synchronized
    fun get(context: Context): RecentEmojiMigrationGate {
        val path = File(context.noBackupFilesDir, "datastore/recent_emoji.preferences_pb").absolutePath
        return gates.getOrPut(path, ::RecentEmojiMigrationGate)
    }
}

/** Serializes the legacy copy with destructive clears and remembers only completed migrations. */
internal class RecentEmojiMigrationGate {
    private val mutex = Mutex()

    @Volatile
    private var migrationComplete = false

    suspend fun migrate(block: suspend () -> Boolean) {
        if (migrationComplete) return
        mutex.withLock {
            if (!migrationComplete && block()) migrationComplete = true
        }
    }

    suspend fun clear(block: suspend () -> Unit) {
        mutex.withLock {
            block()
            // A successful clear also makes a later legacy copy unnecessary in this process.
            migrationComplete = true
        }
    }
}

/**
 * Keeps a preference consumer alive, and correct, across read failures.
 *
 * Three things matter here, and the obvious `catch { emit(defaults) }` gets all three wrong. A
 * failure must not **complete** the flow, because a keyboard that stops hearing about setting
 * changes until the service is recreated looks broken. It must not **substitute defaults for
 * settings that were read successfully a moment ago**, because that silently reverts the user's
 * theme, haptics and privacy choices — and rewinds the learned-data clear epoch, which downstream
 * reads as a request. And it must **try again**, because the usual cause is a transient one.
 *
 * So: the last value that was actually read stays the current value, the upstream is resubscribed
 * with a backoff, and [fallback] is used only when nothing has ever been read — where there is no
 * user state to lose and a first value is what makes the keyboard usable at all.
 *
 * Only [IOException] is handled; anything else is a bug rather than a bad file, and is rethrown.
 */
internal fun <T> Flow<T>.survivingReadFailures(
    fallback: () -> T,
    onFailure: (Throwable) -> Unit,
    retryDelayMillis: (attempt: Long) -> Long = ::preferenceRetryDelayMillis,
): Flow<T> = flow {
    var lastKnown: T? = null
    var everRead = false
    // Counted here rather than taken from `retryWhen`, whose own attempt number only ever grows:
    // one bad afternoon early on would otherwise leave every hiccup for the rest of the session
    // waiting the full ceiling. A backoff is for a file that is not coming back, and a successful
    // read is the evidence that this one did.
    var consecutiveFailures = 0L
    emitAll(
        this@survivingReadFailures
            .onEach { value ->
                lastKnown = value
                everRead = true
                consecutiveFailures = 0L
            }
            .retryWhen { cause, _ ->
                if (cause !is IOException) return@retryWhen false
                onFailure(cause)
                if (!everRead) {
                    lastKnown = fallback()
                    everRead = true
                }
                // Emitting the current value is what makes a first-read fallback visible; on any
                // later failure it is the value the consumer already holds, and the deduplication
                // below drops it rather than waking the keyboard for nothing.
                @Suppress("UNCHECKED_CAST")
                emit(lastKnown as T)
                delay(retryDelayMillis(consecutiveFailures++))
                true
            },
    )
}.distinctUntilChanged()

/** Quick enough for a busy-file hiccup, quiet enough not to spin on a file that is truly gone. */
internal fun preferenceRetryDelayMillis(attempt: Long): Long {
    val doublings = attempt.coerceIn(0L, MAX_RETRY_DOUBLINGS).toInt()
    return RETRY_BASE_MILLIS shl doublings
}

private const val RETRY_BASE_MILLIS = 250L
private const val MAX_RETRY_DOUBLINGS = 7L // 250 ms up to 32 s.

/** Reads and writes [KeyboardSettings]. Held by the IME and the settings UI alike. */
class SettingsRepository(private val context: Context) {

    private val recentEmojiStore = NoBackupRecentEmojiStore.get(context.applicationContext)
    private val recentEmojiMigration =
        RecentEmojiMigrationCoordinator.get(context.applicationContext)

    /**
     * Deduplicated because recently-used emoji share this store, and they change on every tap.
     * Without it the keyboard would rebuild its theme and re-lay out its keys each time someone
     * picked an emoji, for a change that has nothing to do with either.
     */
    val settings: Flow<KeyboardSettings> =
        context.dataStore.data
            .map { it.toSettings() }
            .survivingReadFailures(
                // Only reached when no setting has ever been read, so nothing of the user's is
                // being overwritten. Learning stays off until a real value arrives, because a
                // persisted privacy choice that cannot be read has to be assumed restrictive —
                // and the clear epoch is the sentinel rather than 0, so that the real epoch
                // arriving on a successful retry is not mistaken for the user asking for a clear.
                fallback = {
                    KeyboardSettings(
                        incognitoModeEnabled = true,
                        learnedDataClearEpoch = KeyboardSettings.LEARNED_DATA_EPOCH_UNKNOWN,
                    )
                },
                onFailure = { error ->
                    Log.w(TAG, "Could not read the settings; keeping the last known ones", error)
                },
            )

    /**
     * Emoji the user picked recently, most recent first.
     *
     * Usage rather than preference, which is why it sits beside [settings] instead of inside it:
     * nothing in the settings UI shows it, and it changes far more often than anything that does.
     */
    val recentEmoji: Flow<List<String>> = flow {
        // Inside the builder, and so inside the failure handling below: the migration reads the
        // shared settings file, and an unreadable one used to escape as far as the IME's scope,
        // where nothing catches it and the process dies.
        migrateLegacyRecentEmoji()
        emitAll(
            recentEmojiStore.data.map {
                it[Keys.RECENT_EMOJI].orEmpty()
                    .split(RECENT_SEPARATOR)
                    .filter(String::isNotEmpty)
            },
        )
    }.survivingReadFailures(
        fallback = { emptyList() },
        onFailure = { error -> Log.w(TAG, "Could not read the recently-used emoji", error) },
    )

    /**
     * Moves [emoji] to the front of the recent list, trimmed to [MAX_RECENT].
     *
     * Re-picking something already in the list promotes it rather than duplicating it, so the row
     * stays a most-recently-used list and not a history log.
     */
    suspend fun recordEmojiUse(emoji: String) {
        migrateLegacyRecentEmoji()
        try {
            recentEmojiStore.edit { prefs ->
                val current = prefs[Keys.RECENT_EMOJI].orEmpty()
                    .split(RECENT_SEPARATOR)
                    .filter { it.isNotEmpty() && it != emoji }
                prefs[Keys.RECENT_EMOJI] =
                    (listOf(emoji) + current).take(MAX_RECENT).joinToString(RECENT_SEPARATOR)
            }
        } catch (e: IOException) {
            // Called from the IME's scope, which has no exception handler: a full disk must cost
            // the recent-emoji row, not the keyboard.
            Log.w(TAG, "Could not record the emoji just used", e)
        }
    }

    suspend fun clearRecentEmoji() {
        recentEmojiMigration.clear {
            // Remove the source first. If the second write fails, a retry can still clear the
            // private copy, while no later repository instance can resurrect it from legacy data.
            context.legacyDataStore.edit { it.remove(Keys.RECENT_EMOJI) }
            recentEmojiStore.edit { it.remove(Keys.RECENT_EMOJI) }
        }
    }

    /**
     * Moves pre-0.2.1 emoji history out of the legacy settings file exactly once.
     *
     * Once done it is never attempted again, and a failed attempt is not recorded as done: each
     * half is idempotent (copy only into an empty list, then drop the legacy key), so a retry
     * after a transient failure resumes rather than duplicating.
     */
    private suspend fun migrateLegacyRecentEmoji() {
        recentEmojiMigration.migrate {
            try {
                val legacy = context.legacyDataStore.data.first()[Keys.RECENT_EMOJI]
                if (legacy != null) {
                    recentEmojiStore.edit { privatePrefs ->
                        if (privatePrefs[Keys.RECENT_EMOJI].isNullOrEmpty()) {
                            privatePrefs[Keys.RECENT_EMOJI] = legacy
                        }
                    }
                    context.legacyDataStore.edit { it.remove(Keys.RECENT_EMOJI) }
                }
                true
            } catch (e: IOException) {
                Log.w(TAG, "Could not move the legacy emoji history; will retry later", e)
                false
            }
        }
    }

    /**
     * Applies [transform] and saves the result, or logs and leaves the stored settings alone.
     *
     * Callers are `scope.launch` blocks in the IME and the settings screen, neither of which has
     * an exception handler, so a failed write here would take the whole process down. The setting
     * simply does not stick, which the UI shows on its own the moment the flow re-emits.
     */
    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        try {
            context.dataStore.edit { prefs ->
                prefs.writeKeyboardSettings(transform(prefs.toSettings()))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the changed settings", e)
        }
    }

    /** Notifies a running IME that a durable learned-data clear request is waiting. */
    suspend fun notifyLearnedDataCleared() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LEARNED_DATA_CLEAR_EPOCH] ?: 0L
            prefs[Keys.LEARNED_DATA_CLEAR_EPOCH] =
                if (current == Long.MAX_VALUE) 1L else current + 1L
        }
    }

    private fun Preferences.toSettings(): KeyboardSettings = toKeyboardSettings()

    private companion object {
        const val TAG = "SlideSettings"

        /** Two rows on a typical phone, which is as far back as anyone scrolls for a recent. */
        const val MAX_RECENT = 32

        /** A control character, so it can never appear inside an emoji sequence. */
        const val RECENT_SEPARATOR = "\u001F"
    }
}

/**
 * The stored form of every field in [KeyboardSettings].
 *
 * [writeKeyboardSettings], [Preferences.toKeyboardSettings], and [isBackupEligibleSetting] must
 * all know about every field, and they live as file-level functions — rather than inside
 * [SettingsRepository] — so `KeyboardSettingsTest` can round-trip them directly: a new field
 * that misses one of the three lists fails a JVM test instead of silently never persisting.
 */
private object Keys {
    val THEME_ID = stringPreferencesKey("theme_id")
    val COLOUR_PRESET_ID = stringPreferencesKey("colour_preset_id")
    val COLOUR_SEED_HUE = floatPreferencesKey("colour_seed_hue")
    val COLOUR_SEED_SATURATION = floatPreferencesKey("colour_seed_saturation")
    val COLOUR_SEED_LIGHTNESS = floatPreferencesKey("colour_seed_lightness")
    val FOLLOW_SYSTEM_DARK = booleanPreferencesKey("follow_system_dark")
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    val KEY_BORDERS = booleanPreferencesKey("key_borders")
    val KEY_PREVIEW = booleanPreferencesKey("key_preview")
    val NUMBER_ROW = booleanPreferencesKey("number_row")
    val KEY_HEIGHT = floatPreferencesKey("key_height_scale")
    val KEYBOARD_SIZE_STEP = intPreferencesKey("keyboard_size_step")
    val PORTRAIT_BOARD_OFFSET = floatPreferencesKey("portrait_board_offset_dp")
    val LANDSCAPE_BOARD_OFFSET = floatPreferencesKey("landscape_board_offset_dp")

    /** What "space below the keys" was before it became per-orientation; read once, to seed both. */
    val BOTTOM_PADDING = floatPreferencesKey("bottom_padding_dp")
    val HAPTIC = booleanPreferencesKey("haptic_enabled")
    val HAPTIC_STRENGTH = floatPreferencesKey("haptic_strength")
    val SOUND = booleanPreferencesKey("sound_enabled")
    val SOUND_VOLUME = floatPreferencesKey("sound_volume")
    val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
    val SUGGESTIONS = booleanPreferencesKey("suggestions_enabled")
    val AUTOCORRECT = booleanPreferencesKey("autocorrect_enabled")
    val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode_enabled")
    val LEARNED_DATA_CLEAR_EPOCH = longPreferencesKey("learned_data_clear_epoch")
    val BLOCK_OFFENSIVE = booleanPreferencesKey("block_offensive_words")
    val VOICE_MODEL = stringPreferencesKey("voice_model")
    val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
    val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    val EMOJI_SKIN_TONE = intPreferencesKey("emoji_skin_tone")
    val UPDATE_CHECKS = booleanPreferencesKey("update_checks_enabled")
    val INCLUDE_ALPHA_UPDATES = booleanPreferencesKey("include_alpha_updates")
    val RECENT_EMOJI = stringPreferencesKey("recent_emoji")
}

/**
 * The travel a board's placement control offers, in dp: 64 up or 64 down from the bottom edge.
 *
 * One range for both orientations and for both surfaces that draw it. Two surfaces with two ranges is how
 * a control stops meaning what the keyboard does.
 */
val BOARD_OFFSET_RANGE = -64f..64f

/**
 * The placement in force for a board on a landscape screen, or on a portrait one.
 *
 * The orientation picks which of the two numbers applies, and it is picked in one place so the keyboard
 * cannot be reading one while the settings panel is describing the other. Which board is drawn does not
 * come into it: the same screen gets the same answer whichever keyboard is on it.
 */
fun KeyboardSettings.boardOffsetDp(landscape: Boolean): Float =
    if (landscape) landscapeBoardOffsetDp else portraitBoardOffsetDp

internal fun MutablePreferences.writeKeyboardSettings(updated: KeyboardSettings) {
    this[Keys.THEME_ID] = updated.themeId
    this[Keys.COLOUR_PRESET_ID] = updated.colourPresetId
    this[Keys.COLOUR_SEED_HUE] = updated.colourSeedHue
    this[Keys.COLOUR_SEED_SATURATION] = updated.colourSeedSaturation
    this[Keys.COLOUR_SEED_LIGHTNESS] = updated.colourSeedLightness
    this[Keys.FOLLOW_SYSTEM_DARK] = updated.followSystemDarkMode
    this[Keys.LANGUAGE_TAG] = updated.languageTag
    this[Keys.KEY_BORDERS] = updated.showKeyBorders
    this[Keys.KEY_PREVIEW] = updated.showKeyPreview
    this[Keys.NUMBER_ROW] = updated.showNumberRow
    this[Keys.KEY_HEIGHT] = updated.keyHeightScale
    this[Keys.KEYBOARD_SIZE_STEP] = updated.keyboardSizeStep
    this[Keys.PORTRAIT_BOARD_OFFSET] = updated.portraitBoardOffsetDp
    this[Keys.LANDSCAPE_BOARD_OFFSET] = updated.landscapeBoardOffsetDp
    this[Keys.HAPTIC] = updated.hapticEnabled
    this[Keys.HAPTIC_STRENGTH] = updated.hapticStrength
    this[Keys.SOUND] = updated.soundEnabled
    this[Keys.SOUND_VOLUME] = updated.soundVolume
    this[Keys.GESTURE_TYPING] = updated.gestureTypingEnabled
    this[Keys.SUGGESTIONS] = updated.suggestionsEnabled
    this[Keys.AUTOCORRECT] = updated.autocorrectEnabled
    this[Keys.INCOGNITO_MODE] = updated.incognitoModeEnabled
    this[Keys.LEARNED_DATA_CLEAR_EPOCH] = updated.learnedDataClearEpoch
    this[Keys.BLOCK_OFFENSIVE] = updated.blockOffensiveWords
    this[Keys.VOICE_MODEL] = updated.voiceModelId
    this[Keys.AUTO_CAPITALIZE] = updated.autoCapitalize
    this[Keys.DOUBLE_SPACE_PERIOD] = updated.doubleSpacePeriod
    this[Keys.EMOJI_SKIN_TONE] = updated.emojiSkinTone
    this[Keys.UPDATE_CHECKS] = updated.updateChecksEnabled
    this[Keys.INCLUDE_ALPHA_UPDATES] = updated.includeAlphaUpdates
}

internal fun Preferences.toKeyboardSettings(): KeyboardSettings {
    val defaults = KeyboardSettings()
    return KeyboardSettings(
        themeId = this[Keys.THEME_ID] ?: defaults.themeId,
        colourPresetId = this[Keys.COLOUR_PRESET_ID] ?: defaults.colourPresetId,
        colourSeedHue = boundedFloat(Keys.COLOUR_SEED_HUE, defaults.colourSeedHue, Colours.HUE_MIN..Colours.HUE_MAX),
        colourSeedSaturation = boundedFloat(Keys.COLOUR_SEED_SATURATION, defaults.colourSeedSaturation, 0f..1f),
        colourSeedLightness = boundedFloat(Keys.COLOUR_SEED_LIGHTNESS, defaults.colourSeedLightness, 0f..1f),
        followSystemDarkMode = this[Keys.FOLLOW_SYSTEM_DARK] ?: defaults.followSystemDarkMode,
        languageTag = this[Keys.LANGUAGE_TAG] ?: defaults.languageTag,
        showKeyBorders = this[Keys.KEY_BORDERS] ?: defaults.showKeyBorders,
        showKeyPreview = this[Keys.KEY_PREVIEW] ?: defaults.showKeyPreview,
        showNumberRow = this[Keys.NUMBER_ROW] ?: defaults.showNumberRow,
        keyHeightScale = boundedFloat(Keys.KEY_HEIGHT, defaults.keyHeightScale, 0.7f..1.4f),
        keyboardSizeStep = boundedInt(Keys.KEYBOARD_SIZE_STEP, defaults.keyboardSizeStep, KeyboardSize.STEPS),
        // An install from before the split has one number and no orientation; it seeds both, so the
        // space that was chosen for gesture-nav comfort is not silently thrown away on the update.
        portraitBoardOffsetDp = boardOffset(Keys.PORTRAIT_BOARD_OFFSET),
        landscapeBoardOffsetDp = boardOffset(Keys.LANDSCAPE_BOARD_OFFSET),
        hapticEnabled = this[Keys.HAPTIC] ?: defaults.hapticEnabled,
        hapticStrength = boundedFloat(Keys.HAPTIC_STRENGTH, defaults.hapticStrength, 0f..1f),
        soundEnabled = this[Keys.SOUND] ?: defaults.soundEnabled,
        soundVolume = boundedFloat(Keys.SOUND_VOLUME, defaults.soundVolume, 0f..1f),
        gestureTypingEnabled = this[Keys.GESTURE_TYPING] ?: defaults.gestureTypingEnabled,
        suggestionsEnabled = this[Keys.SUGGESTIONS] ?: defaults.suggestionsEnabled,
        autocorrectEnabled = this[Keys.AUTOCORRECT] ?: defaults.autocorrectEnabled,
        incognitoModeEnabled = this[Keys.INCOGNITO_MODE] ?: defaults.incognitoModeEnabled,
        learnedDataClearEpoch =
            this[Keys.LEARNED_DATA_CLEAR_EPOCH] ?: defaults.learnedDataClearEpoch,
        blockOffensiveWords = this[Keys.BLOCK_OFFENSIVE] ?: defaults.blockOffensiveWords,
        voiceModelId = this[Keys.VOICE_MODEL] ?: defaults.voiceModelId,
        autoCapitalize = this[Keys.AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
        doubleSpacePeriod = this[Keys.DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
        emojiSkinTone = this[Keys.EMOJI_SKIN_TONE] ?: defaults.emojiSkinTone,
        updateChecksEnabled = this[Keys.UPDATE_CHECKS] ?: defaults.updateChecksEnabled,
        includeAlphaUpdates = this[Keys.INCLUDE_ALPHA_UPDATES] ?: defaults.includeAlphaUpdates,
    )
}

/** Protobuf accepts NaN/infinity as floats, but layout and slider rounding cannot use them. */
/**
 * One board's placement: its own stored value, or the single "space below the keys" number that used to
 * serve both orientations, so an update does not throw away the space that was chosen for gesture-nav
 * comfort. Absent on both counts means zero, which is the bottom edge.
 */
private fun Preferences.boardOffset(key: Preferences.Key<Float>): Float {
    // Through boundedFloat on both counts. A stored value only reaches a keyboard after being checked for
    // finiteness and range, and the value being carried over from before the split is stored too — reading
    // it raw would let a corrupt one put a NaN straight into the board's geometry.
    val carried = boundedFloat(Keys.BOTTOM_PADDING, 0f, BOARD_OFFSET_RANGE)
    return boundedFloat(key, carried, BOARD_OFFSET_RANGE)
}

private fun Preferences.boundedFloat(
    key: Preferences.Key<Float>,
    fallback: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val value = this[key] ?: return fallback
    return if (value.isFinite()) value.coerceIn(range.start, range.endInclusive) else fallback
}

/**
 * An integer setting is held to its range rather than trusted.
 *
 * The keyboard-size stop is a position on a five-stop ladder, so an out-of-range number — a
 * hand-edited file, a value written by a future build — has to land on the nearest stop the board
 * can actually be drawn at instead of deriving a size nobody asked for.
 */
private fun Preferences.boundedInt(
    key: Preferences.Key<Int>,
    fallback: Int,
    range: IntRange,
): Int = (this[key] ?: fallback).coerceIn(range.first, range.last)
