package com.ekkus93.silentcaption

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekkus93.silentcaption.model.AndroidModelReadiness
import com.ekkus93.silentcaption.overlay.CaptionOverlayController
import com.ekkus93.silentcaption.session.CaptionSessionPhase
import com.ekkus93.silentcaption.session.CaptionSessionState
import com.ekkus93.silentcaption.setup.AndroidSetupProbe
import com.ekkus93.silentcaption.setup.SetupChecklist
import com.ekkus93.silentcaption.setup.SetupEvaluator
import com.ekkus93.silentcaption.setup.SetupInputs
import com.ekkus93.silentcaption.setup.SetupStatus
import com.ekkus93.silentcaption.ui.home.CaptionDisplayMode
import com.ekkus93.silentcaption.ui.home.HomeRuntimeFacts
import com.ekkus93.silentcaption.ui.home.HomeStateFactory
import com.ekkus93.silentcaption.ui.home.HomeUiActions
import com.ekkus93.silentcaption.ui.home.homeScreen
import com.ekkus93.silentcaption.ui.theme.SilentCaptionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SilentCaptionTheme {
                silentCaptionApp()
            }
        }
    }
}

private data class SetupUiActions(
    val onUsbPermission: () -> Unit,
    val onNotificationPermission: () -> Unit,
    val onBluetoothSettings: () -> Unit,
    val onOverlayPermission: () -> Unit,
    val onRefresh: () -> Unit,
)

private data class AppUiState(
    val checklist: SetupChecklist,
    val modelReady: Boolean,
    val displayMode: CaptionDisplayMode,
    val sessionState: CaptionSessionState,
)

@Composable
private fun silentCaptionApp() {
    val context = LocalContext.current
    val probe = remember { AndroidSetupProbe(context) }
    val modelReadiness = remember { AndroidModelReadiness(context) }
    var refresh by remember { mutableStateOf(0) }
    var displayMode by remember { mutableStateOf(CaptionDisplayMode.Reader) }
    var sessionState by remember { mutableStateOf(CaptionSessionState()) }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh++
        }
    val usbDevice = probe.attachedUsbDevice()
    val floatingMode = displayMode != CaptionDisplayMode.Reader
    val modelReady = modelReadiness.whisperTinyInstalled()
    val checklist =
        SetupEvaluator.evaluate(
            SetupInputs(
                usbAttached = usbDevice != null,
                usbPermission = probe.hasUsbPermission(usbDevice),
                bluetoothRouteReady = probe.bluetoothMediaRouteReady(),
                modelReady = modelReady,
                notificationsRequired = probe.notificationsRequired(),
                notificationsGranted = probe.notificationsGranted(),
                floatingModeRequested = floatingMode,
                overlayGranted = probe.overlayGranted(),
            ),
        )
    sessionState = readinessState(sessionState, checklist.ready)
    refresh.hashCode()
    LaunchedEffect(displayMode, checklist.ready) {
        if (displayMode == CaptionDisplayMode.Reader || checklist.ready) {
            CaptionOverlayController.applyDisplayMode(context, displayMode)
        }
    }
    val setupActions =
        setupUiActions(
            usbPermission = { usbDevice?.let(probe::requestUsbPermission) },
            notificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            refresh = { refresh++ },
        )
    appContent(
        AppUiState(checklist, modelReady, displayMode, sessionState),
        setupActions,
        onDisplayModeChanged = { displayMode = it },
    )
}

@Composable
private fun setupUiActions(
    usbPermission: () -> Unit,
    notificationPermission: () -> Unit,
    refresh: () -> Unit,
): SetupUiActions {
    val context = LocalContext.current
    return SetupUiActions(
        onUsbPermission = usbPermission,
        onNotificationPermission = notificationPermission,
        onBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onOverlayPermission = {
            val packageUri = Uri.parse("package:${context.packageName}")
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        },
        onRefresh = refresh,
    )
}

@Composable
private fun appContent(
    state: AppUiState,
    setupActions: SetupUiActions,
    onDisplayModeChanged: (CaptionDisplayMode) -> Unit,
) {
    if (state.checklist.ready) {
        homeScreen(
            state =
                HomeStateFactory.create(
                    facts =
                        HomeRuntimeFacts(
                            setup = state.checklist,
                            modelReady = state.modelReady,
                            backendLabel = "Whisper Tiny multilingual",
                            languageLabel = "Auto",
                            session = state.sessionState,
                        ),
                    displayMode = state.displayMode,
                ),
            actions = HomeUiActions({}, {}, onDisplayModeChanged),
        )
    } else {
        val context = LocalContext.current
        val probe = remember { AndroidSetupProbe(context) }
        val usbDevice = probe.attachedUsbDevice()
        setupScreen(
            checklist = state.checklist,
            showUsbPermission = usbDevice != null && !probe.hasUsbPermission(usbDevice),
            showNotificationPermission = probe.notificationsRequired() && !probe.notificationsGranted(),
            showOverlayPermission =
                state.displayMode != CaptionDisplayMode.Reader && !probe.overlayGranted(),
            actions = setupActions,
        )
    }
}

private fun readinessState(
    current: CaptionSessionState,
    ready: Boolean,
): CaptionSessionState =
    when {
        ready && current.phase == CaptionSessionPhase.Unavailable ->
            current.copy(phase = CaptionSessionPhase.Ready, detail = null)
        !ready && current.phase == CaptionSessionPhase.Ready ->
            current.copy(phase = CaptionSessionPhase.Unavailable, detail = null)
        else -> current
    }

@Composable
private fun setupScreen(
    checklist: SetupChecklist,
    showUsbPermission: Boolean,
    showNotificationPermission: Boolean,
    showOverlayPermission: Boolean,
    actions: SetupUiActions,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            setupIntroduction(checklist)
            if (showUsbPermission) {
                Button(onClick = actions.onUsbPermission) { Text("Allow USB dongle") }
            }
            if (showNotificationPermission) {
                Button(onClick = actions.onNotificationPermission) { Text("Allow notifications") }
            }
            Button(onClick = actions.onBluetoothSettings) { Text("Open Bluetooth settings") }
            if (showOverlayPermission) {
                Button(onClick = actions.onOverlayPermission) { Text("Allow display over other apps") }
            }
            Button(onClick = actions.onRefresh) { Text("Refresh setup") }
        }
    }
}

@Composable
private fun setupIntroduction(checklist: SetupChecklist) {
    Text(text = "Silent Caption setup", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Your phone sends media audio to the Silent Caption Bluetooth dongle. " +
            "The dongle returns digital audio over USB for on-device speech recognition. " +
            "Raw audio and captions stay local by default; no cloud upload is required.",
    )
    Text(
        "Bluetooth pairing alone does not prove media is routed to the dongle. " +
            "Ready requires Android to expose the required A2DP media-output route.",
    )
    Text(
        if (checklist.ready) "Ready" else "Setup required",
        style = MaterialTheme.typography.titleLarge,
    )
    checklist.items.forEach { item ->
        Text("${statusLabel(item.status)} ${item.label}: ${item.detail}")
    }
}

private fun statusLabel(status: SetupStatus): String =
    when (status) {
        SetupStatus.Ready -> "Ready —"
        SetupStatus.ActionRequired -> "Action required —"
        SetupStatus.Optional -> "Optional —"
    }
