package com.slide.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slide.asr.WhisperModel
import com.slide.core.layout.Key
import com.slide.core.layout.KeyType
import com.slide.core.layout.KeyboardLanguages
import com.slide.core.layout.Layouts
import com.slide.core.settings.BOARD_OFFSET_RANGE
import com.slide.core.settings.ClassicKeyboard
import com.slide.core.settings.KeyboardSettings
import com.slide.core.settings.KeyboardSize
import com.slide.core.settings.SettingsRepository
import com.slide.core.theme.ColourChoice
import com.slide.core.theme.Colours
import com.slide.core.theme.KeyboardColours
import com.slide.engine.lexicon.UserDictionaryStore
import com.slide.ime.view.HexBoard
import com.slide.ime.view.KeyGeometry
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(applicationContext)
        // A staged APK is the biggest file Slide ever writes and nothing else deletes it, so drop
        // whatever an earlier install left behind. Runs off the main thread and skips itself if a
        // download is somehow already going.
        UpdateManager.sweepStagedUpdates(applicationContext)

        setContent {
            SlideAppTheme {
                SetupScreen(repository)
            }
        }
    }

    override fun onStop() {
        // A rotation gets a replacement activity immediately and keeps the process-owned transfer.
        // Home, Back, or switching apps means the user has abandoned this foreground install flow;
        // cancel before a completed download can put Package Installer over the unrelated app.
        if (!isChangingConfigurations) UpdateManager.cancelInstall()
        super.onStop()
    }
}

/** A placement read as where the board sits, the same words the keyboard's own panel uses. */
private fun placementLabel(dp: Float): String = when {
    dp > 0f -> "${dp.roundToInt()} dp higher"
    dp < 0f -> "${(-dp).roundToInt()} dp lower"
    else -> "at the bottom"
}

@Composable
private fun SlideAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SetupScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistedSettings by repository.settings.collectAsState(initial = null)
    val settings = persistedSettings ?: KeyboardSettings()

    // Sliders update visually under the finger and persist once the gesture settles. Writing
    // DataStore on every pixel of movement makes an otherwise simple settings screen feel laggy.
    var keyHeight by remember(settings.keyHeightScale) { mutableFloatStateOf(settings.keyHeightScale) }
    // The keyboard-size stop moves under the finger and persists once the gesture settles, like the
    // other sliders. It is a ladder position, not a size, so the state is the integer the setting
    // stores and the geometry derives everything else from it.
    var keyboardSizeStep by remember(settings.keyboardSizeStep) {
        mutableIntStateOf(settings.keyboardSizeStep)
    }
    var portraitPlacement by remember(settings.portraitBoardOffsetDp) {
        mutableFloatStateOf(settings.portraitBoardOffsetDp)
    }
    var landscapePlacement by remember(settings.landscapeBoardOffsetDp) {
        mutableFloatStateOf(settings.landscapeBoardOffsetDp)
    }
    var hapticStrength by remember(settings.hapticStrength) { mutableFloatStateOf(settings.hapticStrength) }
    var soundVolume by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume) }
    // The palette every preview on this screen draws with, resolved once from the one stored colour
    // choice — the same resolve the IME paints the board from and the Colours screen previews, so no
    // preview here can disagree with the keyboard.
    val boardColours = remember(settings.colourChoice) {
        KeyboardColours.resolve(settings.colourChoice)
    }
    // Which screen the activity is showing. The Colours picker is a full screen rather than a card,
    // so the hue strip, the lightness slider and the live palette all have room to breathe.
    var showColours by rememberSaveable { mutableStateOf(false) }

    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var selected by remember { mutableStateOf(isKeyboardSelected(context)) }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Rotation must not silently drop the update the user is looking at, and — more to the point —
    // must not re-offer the download button while the previous one is still running.
    var availableUpdate by rememberSaveable(stateSaver = UpdateInfoSaver) {
        mutableStateOf<UpdateInfo?>(null)
    }
    var offeredUpdateIncludesPrereleases by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // The download belongs to the process, not to this composition: rotating away neither cancels
    // it nor starts a second one, and the screen that happens to exist when it finishes reports it.
    val downloading by UpdateManager.isDownloading.collectAsState()
    val downloadedBytes by UpdateManager.downloadedBytes.collectAsState()
    val installOutcome by UpdateManager.outcome.collectAsState()
    // The automatic check runs without the user asking, so its failure is reported inline in the
    // Updates card rather than as a modal dialog that would greet them on every offline open.
    var autoCheckFailed by remember { mutableStateOf(false) }
    var manualCheckRequest by remember { mutableIntStateOf(0) }
    var handledManualCheckRequest by remember { mutableIntStateOf(0) }
    var activeUpdateCheck by remember { mutableStateOf<Any?>(null) }
    val checkingForUpdates = activeUpdateCheck != null
    LaunchedEffect(installOutcome) {
        when (val finished = installOutcome) {
            null -> return@LaunchedEffect
            InstallOutcome.Handed -> availableUpdate = null
            InstallOutcome.NeedsPermission -> {
                availableUpdate = null
                updateMessage = "Allow Polykeyboard to install apps, then tap Check now again to " +
                    "finish updating."
            }
            // The button is disabled while a download runs, so this is a stale tap at worst. The
            // dialog is already saying "Downloading…"; leave it be.
            InstallOutcome.AlreadyRunning -> Unit
            InstallOutcome.Cancelled -> {
                availableUpdate = null
                updateMessage = "Update download canceled."
            }
            is InstallOutcome.Failed -> {
                availableUpdate = null
                updateMessage = "Update download failed: " +
                    (finished.message ?: "unknown error") +
                    "\n\nYou can also download the APK from the Polykeyboard releases page on " +
                    "GitHub and install it by hand."
            }
        }
        UpdateManager.consumeOutcome()
    }
    var showClearLearnedDataConfirmation by remember { mutableStateOf(false) }
    var clearingLearnedData by remember { mutableStateOf(false) }
    var privacyMessage by remember { mutableStateOf<String?>(null) }
    var showThirdPartyNotices by remember { mutableStateOf(false) }
    val thirdPartyNotices = remember {
        runCatching {
            val notices = context.assets.open("THIRD_PARTY_NOTICES.txt")
                .bufferedReader().use { it.readText() }
            val swipeTerms = context.assets.open("swipe/FUTO_MODEL_LICENSE.md")
                .bufferedReader().use { it.readText() }
            "$notices\n\nFull FUTO Swipe model terms\n---------------------------\n\n$swipeTerms"
        }.getOrElse { "Licences and notices could not be loaded." }
    }

    LaunchedEffect(
        persistedSettings != null,
        settings.updateChecksEnabled,
        settings.includeAlphaUpdates,
        manualCheckRequest,
    ) {
        val manual = manualCheckRequest != handledManualCheckRequest
        handledManualCheckRequest = manualCheckRequest
        activeUpdateCheck = null
        // Initial UI defaults must not invalidate a saved offer before the persisted channel is
        // known. An active install always keeps the release it was started with after rotation.
        if (persistedSettings == null || UpdateManager.hasActiveInstall()) return@LaunchedEffect
        if (!settings.updateChecksEnabled) {
            availableUpdate = null
            offeredUpdateIncludesPrereleases = null
            autoCheckFailed = false
            return@LaunchedEffect
        }
        if (availableUpdate != null &&
            offeredUpdateIncludesPrereleases == settings.includeAlphaUpdates
        ) return@LaunchedEffect
        // A completed response can arrive just before a changed channel reaches this screen.
        // Such an idle offer must be replaced even though it is already visible.
        availableUpdate = null
        offeredUpdateIncludesPrereleases = null

        val check = Any()
        activeUpdateCheck = check
        try {
            val result = runCatchingCancellable {
                UpdateManager.check(context, settings.includeAlphaUpdates)
            }
            // A replaced effect may finish its blocking network call after the new one starts.
            // Its result cannot replace the current channel's offer or an active install dialog.
            if (activeUpdateCheck !== check || UpdateManager.hasActiveInstall()) {
                return@LaunchedEffect
            }
            result.onSuccess {
                availableUpdate = it
                offeredUpdateIncludesPrereleases = if (it != null) settings.includeAlphaUpdates else null
                autoCheckFailed = false
                if (manual) {
                    updateMessage = if (it == null) {
                        "You already have the newest selected release."
                    } else {
                        null
                    }
                }
            }.onFailure {
                if (manual) {
                    updateMessage = "Could not check for updates: ${it.message ?: "network error"}"
                } else {
                    autoCheckFailed = true
                }
            }
        } finally {
            if (activeUpdateCheck === check) activeUpdateCheck = null
        }
    }

    // Both checks change outside the app, so re-read them every time we come back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isKeyboardEnabled(context)
                selected = isKeyboardSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The Colours screen is the one colour control. It writes the same stored choice the keyboard
    // resolves its single palette from, so what the preview shows is what the board will draw.
    if (showColours) {
        ColoursScreen(
            settings = settings,
            onSelectPreset = { id ->
                scope.launch { repository.update { it.copy(colourPresetId = id) } }
            },
            onSeed = { hue, saturation, lightness ->
                scope.launch {
                    repository.update {
                        it.copy(
                            colourPresetId = Colours.ID_CUSTOM,
                            colourSeedHue = hue,
                            colourSeedSaturation = saturation,
                            colourSeedLightness = lightness,
                        )
                    }
                }
            },
            onReset = { scope.launch { repository.update { it.copy(colourPresetId = Colours.ID_JADE) } } },
            onBack = { showColours = false },
        )
        return
    }

    Scaffold(modifier = Modifier.imePadding()) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Polykeyboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "A gesture-first keyboard with fully on-device voice typing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StepCard(
                step = 1,
                title = "Enable Polykeyboard",
                subtitle = if (enabled) "Enabled in system settings." else "Turn Polykeyboard on in the keyboard list.",
                done = enabled,
                actionLabel = "Open keyboard settings",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
            )

            StepCard(
                step = 2,
                title = "Switch to Polykeyboard",
                subtitle = if (selected) "Polykeyboard is your active keyboard." else "Pick Polykeyboard from the input method picker.",
                done = selected,
                actionLabel = "Choose input method",
                enabledAction = enabled,
                onAction = {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                },
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Colours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paint the whole keyboard — the keys, the gesture trail and the popups — from " +
                            "one palette. Pick a preset or dial in your own seed colour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val current = boardColours
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PalettePreview(colours = listOf(
                            current.palette.key,
                            current.palette.specialKey,
                            current.palette.enterKey,
                            current.palette.label,
                        ), size = 28.dp)
                    }
                    Button(onClick = { showColours = true }) { Text("Choose colours") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Languages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Which language the keyboard types in. The globe key on the board cycles " +
                            "these, and both write the same choice, so the keyboard and this card " +
                            "never disagree about it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardLanguages.SWITCHABLE.forEach { entry ->
                            // Nothing has been chosen until a tag is stored, so a fresh install shows
                            // no chip selected rather than claiming a language the keyboard picked
                            // for itself.
                            val selected = settings.languageTag.isNotEmpty() &&
                                KeyboardLanguages.resolve(settings.languageTag) == entry.language
                            LanguageChip(
                                label = entry.label,
                                selected = selected,
                                onClick = {
                                    scope.launch {
                                        repository.update { it.copy(languageTag = entry.tag) }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Keyboard", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

                    SettingSwitch("Gesture typing", settings.gestureTypingEnabled) { value ->
                        scope.launch { repository.update { it.copy(gestureTypingEnabled = value) } }
                    }
                    SettingSwitch(
                        label = "Suggestion strip",
                        checked = settings.suggestionsEnabled,
                        description = "Shows word candidates and is required for autocorrection.",
                    ) { value ->
                        scope.launch { repository.update { it.copy(suggestionsEnabled = value) } }
                    }
                    SettingSwitch(
                        label = "Autocorrection",
                        checked = settings.autocorrectEnabled,
                        description = if (settings.suggestionsEnabled) {
                            "Corrects likely misspellings when you finish a word."
                        } else if (settings.autocorrectEnabled) {
                            "Paused while the suggestion strip is off; it will resume when the strip is on."
                        } else {
                            "Turn on the suggestion strip to enable autocorrection."
                        },
                        enabled = settings.suggestionsEnabled,
                    ) { value ->
                        scope.launch { repository.update { it.copy(autocorrectEnabled = value) } }
                    }
                    SettingSwitch("Block offensive words", settings.blockOffensiveWords) { value ->
                        scope.launch { repository.update { it.copy(blockOffensiveWords = value) } }
                    }
                    SettingSwitch("Auto-capitalization", settings.autoCapitalize) { value ->
                        scope.launch { repository.update { it.copy(autoCapitalize = value) } }
                    }
                    SettingSwitch(
                        label = "Double-space period",
                        checked = settings.doubleSpacePeriod,
                        description = "Press Space twice to finish a sentence with a period.",
                    ) { value ->
                        scope.launch { repository.update { it.copy(doubleSpacePeriod = value) } }
                    }
                    SettingSlider(
                        label = "Keyboard size",
                        value = keyboardSizeStep.toFloat(),
                        valueLabel = keyboardSizeLabel(keyboardSizeStep),
                        valueRange = KeyboardSize.MIN_STEP.toFloat()..KeyboardSize.MAX_STEP.toFloat(),
                        // Five stops, so three positions sit between the two ends.
                        steps = KeyboardSize.MAX_STEP - KeyboardSize.MIN_STEP - 1,
                        onValueChange = { keyboardSizeStep = it.roundToInt() },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(keyboardSizeStep = keyboardSizeStep) }
                            }
                        },
                    )
                    Text(
                        "Resizes the hex board itself. Portrait has the whole width already, so its " +
                            "hexagons grow taller; landscape takes more of the screen, so the two " +
                            "halves reach further in. Default draws the board as it was approved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The preview resolves its colours the way the Colours screen does — through the
                    // one stored choice — and asks the board's own geometry for its shape, so what
                    // is shown is the board the keyboard will draw, not a picture of one.
                    KeyboardSizePreview(step = keyboardSizeStep, colours = boardColours)
                    // The one stock control the hex board cannot answer sits under a heading that
                    // says so: the honeycomb has no number row, so its digits live behind ?123, and
                    // until this heading existed the control sat beside the ones that do work with
                    // nothing to distinguish it. Nothing is removed, so the group is a label rather
                    // than a loss. "Keyboard height" has left the group: a hexagon has no row to
                    // stretch, but the board still has a size, so that slider now scales the
                    // honeycomb and works on both boards.
                    Text(
                        ClassicKeyboard.GROUP_TITLE,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    // The note is shown only while the classic controls cannot act, and the controls
                    // themselves stay enabled. Disabling them was wrong twice over: it removed them
                    // from assistive technology, which the instrumented settings-panel test caught,
                    // and it turned a stored setting into a dead one — a height chosen here still
                    // applies the moment the classic board is up.
                    if (!ClassicKeyboard.controlsApply()) {
                        Text(
                            ClassicKeyboard.HEX_BOARD_REASON,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingSwitch(
                        label = "Number row",
                        checked = settings.showNumberRow,
                    ) { value ->
                        scope.launch { repository.update { it.copy(showNumberRow = value) } }
                    }
                    SettingSlider(
                        label = "Keyboard height",
                        value = keyHeight,
                        valueLabel = "${(keyHeight * 100).roundToInt()}%",
                        valueRange = 0.7f..1.4f,
                        steps = 6,
                        // Above 100% there is nothing left for it to do on the honeycomb: the keys
                        // already span the screen's width, so a taller frame was only empty space
                        // above the board. It stays enabled because it is the control that makes the
                        // keys smaller, and the reason is on the screen rather than in a guess. The
                        // size ladder is what makes them larger.
                        description = "Above 100% the honeycomb cannot get bigger: the keys already " +
                            "span the screen's width. Use Keyboard size to make them larger, or this " +
                            "one to make them smaller.",
                        onValueChange = { keyHeight = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(keyHeightScale = keyHeight) }
                            }
                        },
                    )
                    SettingSlider(
                        label = "Portrait placement",
                        value = portraitPlacement,
                        valueLabel = placementLabel(portraitPlacement),
                        valueRange = BOARD_OFFSET_RANGE,
                        steps = 7,
                        onValueChange = { portraitPlacement = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(portraitBoardOffsetDp = portraitPlacement) }
                            }
                        },
                    )
                    SettingSlider(
                        label = "Landscape placement",
                        value = landscapePlacement,
                        valueLabel = placementLabel(landscapePlacement),
                        valueRange = BOARD_OFFSET_RANGE,
                        steps = 7,
                        onValueChange = { landscapePlacement = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(landscapeBoardOffsetDp = landscapePlacement) }
                            }
                        },
                    )
                    SettingSwitch("Key borders", settings.showKeyBorders) { value ->
                        scope.launch { repository.update { it.copy(showKeyBorders = value) } }
                    }
                    SettingSwitch("Key popup preview", settings.showKeyPreview) { value ->
                        scope.launch { repository.update { it.copy(showKeyPreview = value) } }
                    }
                    SettingSwitch("Haptic feedback", settings.hapticEnabled) { value ->
                        scope.launch { repository.update { it.copy(hapticEnabled = value) } }
                    }
                    SettingSlider(
                        label = "Haptic strength",
                        value = hapticStrength,
                        valueLabel = "${(hapticStrength * 100).roundToInt()}%",
                        valueRange = 0.1f..1f,
                        steps = 8,
                        enabled = settings.hapticEnabled,
                        onValueChange = { hapticStrength = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(hapticStrength = hapticStrength) }
                            }
                        },
                    )
                    SettingSwitch("Sound on keypress", settings.soundEnabled) { value ->
                        scope.launch { repository.update { it.copy(soundEnabled = value) } }
                    }
                    SettingSlider(
                        label = "Keypress volume",
                        value = soundVolume,
                        valueLabel = "${(soundVolume * 100).roundToInt()}%",
                        valueRange = 0.1f..1f,
                        steps = 8,
                        enabled = settings.soundEnabled,
                        onValueChange = { soundVolume = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.update { it.copy(soundVolume = soundVolume) }
                            }
                        },
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy and learned data", style = MaterialTheme.typography.titleMedium)
                    SettingSwitch(
                        label = "Incognito mode",
                        checked = settings.incognitoModeEnabled,
                        description = "Stops Polykeyboard from learning new words, phrases, and per-key touch calibration. Existing learned data stays until you clear it.",
                    ) { value ->
                        scope.launch {
                            repository.update { it.copy(incognitoModeEnabled = value) }
                        }
                    }
                    Text(
                        "Polykeyboard keeps learned words, phrases, and per-key touch calibration only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !clearingLearnedData,
                        onClick = { showClearLearnedDataConfirmation = true },
                    ) {
                        Text(if (clearingLearnedData) "Clearing…" else "Clear learned data")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Voice typing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Speech is recognised on this device. Microphone audio is kept only in " +
                            "memory while Polykeyboard transcribes it; audio and transcripts are " +
                            "not sent to a server by Polykeyboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Resolved through the stored setting so this card can never disagree with
                    // the model the keyboard actually loads; unknown values fall back to Default.
                    SettingSwitch(
                        "Microphone button",
                        settings.micButtonEnabled,
                    ) { value -> scope.launch { repository.update { it.copy(micButtonEnabled = value) } } }
                    Text(
                        "Shows the microphone at the left end of the bar above the keys. Switched " +
                            "off, the suggestions keep that width and nothing starts a recording " +
                            "from the keyboard; the model below stays set up either way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val model = WhisperModel.fromId(settings.voiceModelId)
                    Text(model.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${model.description} Polykeyboard packages one verified model and runs " +
                            "it entirely on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Updates", style = MaterialTheme.typography.titleMedium)
                    Text("Polykeyboard contacts GitHub when settings opens with update checks enabled, when you enable checks or change prerelease inclusion, and when you tap Check now. Android always confirms installation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingSwitch("Check GitHub for updates", settings.updateChecksEnabled) { value -> scope.launch { repository.update { it.copy(updateChecksEnabled = value) } } }
                    if (settings.updateChecksEnabled && autoCheckFailed) {
                        Text(
                            "The automatic update check could not reach GitHub. Check your " +
                                "connection, then tap Check now to retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (settings.updateChecksEnabled) {
                        SettingSwitch("Include prereleases", settings.includeAlphaUpdates) { value -> scope.launch { repository.update { it.copy(includeAlphaUpdates = value) } } }
                        Button(
                            enabled = !checkingForUpdates && !downloading,
                            onClick = { manualCheckRequest++ },
                        ) { Text(if (checkingForUpdates) "Checking…" else "Check now") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text(
                        AboutCopy.PITCH,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        AboutCopy.FORK_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Polykeyboard is Apache-2.0 licensed and includes open-source software and " +
                            "public language data. Gesture typing is powered by FUTO Swipe technology.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showThirdPartyNotices = true }) {
                        Text("Licences and notices")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try it", style = MaterialTheme.typography.titleMedium)
                    var text by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type here") },
                    )
                    SelectionContainer {
                        Text(
                            "Swipe across the letters to type a word, then tap an alternative in " +
                                "the suggestion bar if it guessed wrong. Tap the microphone to dictate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (downloading) UpdateManager.cancelInstall()
                availableUpdate = null
            },
            title = { Text("Polykeyboard ${update.version} is available") },
            text = {
                Text(
                    if (downloading) {
                        val downloadedMb = downloadedBytes / BYTES_PER_MB
                        val totalMb = update.apkSize / BYTES_PER_MB
                        val percent =
                            if (update.apkSize > 0) downloadedBytes * 100 / update.apkSize else 0
                        "Downloading Polykeyboard ${update.version}: $downloadedMb of $totalMb MB " +
                            "($percent%). Android will ask you to confirm the installation."
                    } else {
                        update.notes.ifBlank { "A newer signed Polykeyboard release is available." }
                    },
                )
            },
            confirmButton = {
                Button(
                    enabled = !downloading,
                    // Handed to the manager rather than launched here: the result arrives through
                    // UpdateManager.outcome, which survives this composition.
                    onClick = { UpdateManager.install(context, update) },
                ) { Text(if (downloading) "Downloading…" else "Download and install") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        if (downloading) UpdateManager.cancelInstall()
                        availableUpdate = null
                    },
                ) { Text(if (downloading) "Cancel download" else "Not now") }
            },
        )
    }
    updateMessage?.let { message -> AlertDialog(onDismissRequest = { updateMessage = null }, title = { Text("Updates") }, text = { Text(message) }, confirmButton = { Button(onClick = { updateMessage = null }) { Text("OK") } }) }
    if (showClearLearnedDataConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!clearingLearnedData) showClearLearnedDataConfirmation = false
            },
            title = { Text("Clear learned data?") },
            text = {
                Text(
                    "Polykeyboard will forget the personal words, word pairs, and per-key touch " +
                        "calibration it learned from your typing. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    enabled = !clearingLearnedData,
                    onClick = {
                        scope.launch {
                            clearingLearnedData = true
                            try {
                                // Marker creation and epoch publication are one privacy-critical
                                // operation. Once the user confirms, leaving this screen must not
                                // cancel between the durable marker and clearing the live IME.
                                val deletionRequested = withContext(NonCancellable) {
                                    val requested = withContext(Dispatchers.IO) {
                                        UserDictionaryStore(context.applicationContext)
                                            .requestDeletion()
                                    }
                                    if (requested) repository.notifyLearnedDataCleared()
                                    requested
                                }
                                if (!deletionRequested) {
                                    throw IOException("the clear request could not be stored safely")
                                }
                                showClearLearnedDataConfirmation = false
                                privacyMessage =
                                    "Learned words, phrases, and per-key touch calibration were cleared."
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                showClearLearnedDataConfirmation = false
                                privacyMessage = "Could not clear learned data: " +
                                    (error.message ?: "unknown error")
                            } finally {
                                clearingLearnedData = false
                            }
                        }
                    },
                ) { Text(if (clearingLearnedData) "Clearing…" else "Clear") }
            },
            dismissButton = {
                Button(
                    enabled = !clearingLearnedData,
                    onClick = { showClearLearnedDataConfirmation = false },
                ) { Text("Cancel") }
            },
        )
    }
    privacyMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { privacyMessage = null },
            title = { Text("Learned data") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { privacyMessage = null }) { Text("OK") }
            },
        )
    }
    if (showThirdPartyNotices) {
        AlertDialog(
            onDismissRequest = { showThirdPartyNotices = false },
            title = { Text("Licences and notices") },
            text = {
                SelectionContainer {
                    Text(
                        thirdPartyNotices,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showThirdPartyNotices = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun StepCard(
    step: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String,
    enabledAction: Boolean = true,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else step.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction, enabled = enabledAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.38f,
                    ),
                )
            }
        }
        // The row owns the toggle semantics and the full touch target. A null callback keeps this
        // visual control from becoming a second TalkBack stop.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    /**
     * Why the control cannot do more, when it cannot. A control that has reached its limit stays
     * enabled and says so here rather than being greyed out, which removes it from assistive
     * technology and turns a stored setting into a dead one.
     */
    description: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.38f,
                ),
            )
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
}

@Composable
private fun ThemeSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * A language the keyboard ships, as a chip; the selected one is filled.
 *
 * The same control as the Colours screen's swatches, for the same reason: picking one of a small,
 * fixed set is not a switch or a slider, and a chip shows what the alternatives are while it is
 * being read.
 */
@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row of palette swatches; the same colours the keyboard resolves, side by side. */
@Composable
private fun PalettePreview(colours: List<Int>, size: Dp) {
    colours.forEach { argb ->
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(argb), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

/**
 * The keyboard-size preview: the hex board at the slider's stop, drawn by the keyboard's own
 * geometry.
 *
 * Nothing here is an illustration. [HexBoard.sizing] gives the step the sizing the keyboard derives,
 * [HexBoard.cappedBoardHeight] gives the frame the keyboard would hand the board on each
 * orientation's screen, and [HexBoard.place] with [KeyGeometry.hexShapeFor] hand back the caps the
 * board is actually drawn from — so the picture is the arithmetic the IME runs, shown small. The
 * colours come from the one resolved palette, the way the Colours screen's swatches do, and the
 * letters sit where the geometry placed them.
 *
 * Both orientations are shown because the step spends them differently: portrait has no width left
 * to give, so its hexagons grow taller and its box grows with them; landscape's share of the screen
 * height is what the step multiplies, so its board visibly takes more of the width it is fitted to.
 */
@Composable
private fun KeyboardSizePreview(step: Int, colours: KeyboardColours) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoardPreview(
            label = "Portrait",
            sizing = HexBoard.sizing(HexBoard.Orientation.PORTRAIT, step),
            displayWidth = PORTRAIT_PREVIEW_WIDTH,
            displayHeight = PORTRAIT_PREVIEW_HEIGHT,
            colours = colours,
        )
        BoardPreview(
            label = "Landscape",
            sizing = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE, step),
            displayWidth = LANDSCAPE_PREVIEW_WIDTH,
            displayHeight = LANDSCAPE_PREVIEW_HEIGHT,
            colours = colours,
        )
    }
}

/** One orientation's board, at [sizing], in a box whose proportions are the board's own. */
@Composable
private fun BoardPreview(
    label: String,
    sizing: HexBoard.Sizing,
    displayWidth: Float,
    displayHeight: Float,
    colours: KeyboardColours,
) {
    // The frame the keyboard would give the board on that screen — the natural block against the
    // display-height share, resolved by the same call [KeyboardView] measures with — so the box is
    // as tall as the board is and a bigger step draws a bigger board rather than a rescaled picture
    // of one.
    val availableHeight = HexBoard.cappedBoardHeight(displayWidth, displayHeight, sizing)
    val textMeasurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(displayWidth / availableHeight),
        ) {
            val placement = HexBoard.place(Layouts.HexQwertyEn, size.width, size.height, sizing)
            val boardBackground = Color(colours.palette.background)
            placement.keys.forEach { placed ->
                val vertices = KeyGeometry.hexShapeFor(placed).vertices()
                val path = Path().apply {
                    moveTo(vertices.first().x, vertices.first().y)
                    for (index in 1 until vertices.size) {
                        lineTo(vertices[index].x, vertices[index].y)
                    }
                    close()
                }
                drawPath(path, Color(hexFillFor(placed.key, colours)))
                // The keyboard separates neighbouring caps by stroking them in the board's own
                // background colour, because caps in a row share an edge exactly; drawing the same
                // seam is what makes the preview read as the honeycomb instead of a filled slab.
                drawPath(path, boardBackground, style = Stroke(width = PREVIEW_SEAM_PX))

                val text = placed.key.outputText.takeIf { it.length == 1 && !it[0].isWhitespace() }
                    ?: return@forEach
                val measured = textMeasurer.measure(
                    text.uppercase(),
                    style = TextStyle(
                        color = Color(colours.palette.label),
                        // Half the row pitch, in the canvas's own pixels, so the letters keep their
                        // proportion to the key however large the preview box is.
                        fontSize = (placed.height * 0.5f).toSp(),
                    ),
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        placed.centerX - measured.size.width / 2f,
                        placed.centerY - measured.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** The hex board's fills, read from the palette exactly as the keyboard's renderer reads them. */
private fun hexFillFor(key: Key, colours: KeyboardColours): Int = when (key.type) {
    KeyType.ENTER -> colours.palette.enterKey
    KeyType.CHARACTER, KeyType.SPACE -> colours.palette.key
    else -> colours.palette.specialKey
}

/**
 * The stop's name and the size it draws at.
 *
 * The percentage is read from the ladder's own multiplier rather than written down beside it, so a
 * label cannot come to disagree with the board it is describing; the step is clamped to the ladder
 * first, which is what an out-of-range value would have been stored as.
 */
private fun keyboardSizeLabel(step: Int): String {
    val name = when (KeyboardSize.clampStep(step)) {
        KeyboardSize.MIN_STEP -> "Smallest"
        KeyboardSize.DEFAULT_STEP - 1 -> "Smaller"
        KeyboardSize.DEFAULT_STEP -> "Default"
        KeyboardSize.DEFAULT_STEP + 1 -> "Larger"
        else -> "Largest"
    }
    return "$name · ${(HexBoard.sizeMultiplier(step) * 100).roundToInt()}%"
}

/**
 * The Colours screen — the keyboard's one colour control.
 *
 * Everything here writes the stored choice the IME resolves its single palette from, and the live
 * preview runs through the same [KeyboardColours], so the swatches are not an illustration of the
 * palette: they are the palette the board and the chrome will draw with.
 */
@Composable
private fun ColoursScreen(
    settings: KeyboardSettings,
    onSelectPreset: (String) -> Unit,
    onSeed: (Float, Float, Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    // Sliders move under the finger and persist once the gesture settles, like the other settings.
    var hue by remember(settings.colourSeedHue) { mutableFloatStateOf(settings.colourSeedHue) }
    var saturation by remember(settings.colourSeedSaturation) {
        mutableFloatStateOf(settings.colourSeedSaturation)
    }
    var lightness by remember(settings.colourSeedLightness) {
        mutableFloatStateOf(settings.colourSeedLightness)
    }
    // Touching a seed control shows the custom seed straight away, without writing to the store on
    // every pixel; the write lands when the gesture finishes.
    var editingCustom by remember(settings.colourPresetId) {
        mutableStateOf(settings.colourPresetId == Colours.ID_CUSTOM)
    }

    val previewChoice = if (editingCustom) {
        ColourChoice(Colours.ID_CUSTOM, hue, saturation, lightness)
    } else {
        settings.colourChoice
    }
    val colours = remember(previewChoice) { KeyboardColours.resolve(previewChoice) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text(
                "Colours",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "The keys and everything around them — the gesture trail, the popups and the search " +
                "bar — resolve from one palette, so they always match.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preset", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Colours.presets.size) { index ->
                        val preset = Colours.presets[index]
                        ThemeSwatch(
                            color = Color(preset.palette.key),
                            label = preset.name,
                            selected = settings.colourPresetId == preset.id,
                            onClick = { onSelectPreset(preset.id) },
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Custom seed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Drag along the hue, then set how saturated and how light the seed is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSlider(
                    label = "Hue",
                    value = hue,
                    valueLabel = "${hue.roundToInt()}°",
                    valueRange = Colours.HUE_MIN..Colours.HUE_MAX,
                    steps = 11,
                    onValueChange = {
                        hue = it
                        editingCustom = true
                    },
                    onValueChangeFinished = { onSeed(hue, saturation, lightness) },
                )
                SettingSlider(
                    label = "Saturation",
                    value = saturation,
                    valueLabel = "${(saturation * 100).roundToInt()}%",
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = {
                        saturation = it
                        editingCustom = true
                    },
                    onValueChangeFinished = { onSeed(hue, saturation, lightness) },
                )
                SettingSlider(
                    label = "Lightness",
                    value = lightness,
                    valueLabel = "${(lightness * 100).roundToInt()}%",
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = {
                        lightness = it
                        editingCustom = true
                    },
                    onValueChangeFinished = { onSeed(hue, saturation, lightness) },
                )
                Button(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Reset to jade")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preview", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewSwatch("Normal key", colours.palette.key)
                    PreviewSwatch("Special key", colours.palette.specialKey)
                    PreviewSwatch("Enter", colours.palette.enterKey)
                    PreviewSwatch("Label", colours.palette.label)
                }
            }
        }
    }
}

/** One labelled swatch in the Colours preview. */
@Composable
private fun PreviewSwatch(label: String, argb: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(argb), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Keeps the offered release across a configuration change instead of re-checking GitHub for it. */
private val UpdateInfoSaver = listSaver<UpdateInfo?, Any>(
    save = { update ->
        update?.let {
            listOf<Any>(
                it.version,
                UpdateManager.boundReleaseNotes(it.notes),
                it.apkUrl,
                it.apkSha256,
                it.apkSize,
            )
        }
            ?: emptyList()
    },
    restore = { saved ->
        if (saved.size < 5) {
            null
        } else {
            UpdateInfo(
                version = saved[0] as String,
                notes = saved[1] as String,
                apkUrl = saved[2] as String,
                apkSha256 = saved[3] as String,
                apkSize = saved[4] as Long,
            )
        }
    },
)

/**
 * [runCatching] for suspending work.
 *
 * `runCatching` catches [CancellationException] along with everything else, which turns "the user
 * left" into a failure dialog and, worse, leaves the coroutine believing it is still alive. The
 * catch stays as wide as `runCatching`'s otherwise: a failure to reach GitHub should be reported
 * whatever its class, and narrowing to [Exception] would turn an [Error] into a dead process.
 */
private suspend inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    Result.failure(t)
}

private const val BYTES_PER_MB = 1_000_000L

/**
 * The screens the size preview draws its two boards on, in the units the geometry works in.
 *
 * A real phone's display: 1080 x 2400 portrait, 2400 x 1080 landscape. The geometry is asked for the
 * frame the keyboard would give the board on those screens, so the preview boxes are the boards'
 * own proportions — and the step's effect is the board growing, not the picture being stretched.
 */
private const val PORTRAIT_PREVIEW_WIDTH = 1080f
private const val PORTRAIT_PREVIEW_HEIGHT = 2400f
private const val LANDSCAPE_PREVIEW_WIDTH = 2400f
private const val LANDSCAPE_PREVIEW_HEIGHT = 1080f

/** Cap separation in the preview, on the preview's own scale — the keyboard's 1.5 px seam. */
private const val PREVIEW_SEAM_PX = 1.5f

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return inputMethodBelongsToPackage(current, context.packageName)
}
