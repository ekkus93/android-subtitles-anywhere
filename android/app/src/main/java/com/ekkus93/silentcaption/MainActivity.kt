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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekkus93.silentcaption.setup.AndroidSetupProbe
import com.ekkus93.silentcaption.setup.SetupChecklist
import com.ekkus93.silentcaption.setup.SetupEvaluator
import com.ekkus93.silentcaption.setup.SetupInputs
import com.ekkus93.silentcaption.setup.SetupStatus
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

private data class SetupUiState(
    val checklist: SetupChecklist,
    val floatingMode: Boolean,
    val showUsbPermission: Boolean,
    val showNotificationPermission: Boolean,
    val showOverlayPermission: Boolean,
)

private data class SetupUiActions(
    val onFloatingModeChanged: (Boolean) -> Unit,
    val onUsbPermission: () -> Unit,
    val onNotificationPermission: () -> Unit,
    val onBluetoothSettings: () -> Unit,
    val onOverlayPermission: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
private fun silentCaptionApp() {
    val context = LocalContext.current
    val probe = remember { AndroidSetupProbe(context) }
    var refresh by remember { mutableStateOf(0) }
    var floatingMode by remember { mutableStateOf(false) }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh++
        }
    val usbDevice = probe.attachedUsbDevice()
    val checklist =
        SetupEvaluator.evaluate(
            SetupInputs(
                usbAttached = usbDevice != null,
                usbPermission = probe.hasUsbPermission(usbDevice),
                bluetoothRouteReady = probe.bluetoothMediaRouteReady(),
                modelReady = false,
                notificationsRequired = probe.notificationsRequired(),
                notificationsGranted = probe.notificationsGranted(),
                floatingModeRequested = floatingMode,
                overlayGranted = probe.overlayGranted(),
            ),
        )
    refresh.hashCode()

    setupScreen(
        state =
            SetupUiState(
                checklist = checklist,
                floatingMode = floatingMode,
                showUsbPermission = usbDevice != null && !probe.hasUsbPermission(usbDevice),
                showNotificationPermission =
                    probe.notificationsRequired() && !probe.notificationsGranted(),
                showOverlayPermission = floatingMode && !probe.overlayGranted(),
            ),
        actions =
            SetupUiActions(
                onFloatingModeChanged = { floatingMode = it },
                onUsbPermission = { usbDevice?.let(probe::requestUsbPermission) },
                onNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onOverlayPermission = {
                    val packageUri = Uri.parse("package:${context.packageName}")
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
                    )
                },
                onRefresh = { refresh++ },
            ),
    )
}

@Composable
private fun setupScreen(
    state: SetupUiState,
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
            setupIntroduction(state.checklist)
            setupActions(state, actions)
            captionModeControls(state, actions)
            Button(onClick = actions.onRefresh) { Text("Refresh setup") }
            Text(
                "Speech-model installation remains not ready until SC-320 model management " +
                    "provides a verified installed model.",
                style = MaterialTheme.typography.bodySmall,
            )
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
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        if (checklist.ready) "Ready" else "Setup required",
        style = MaterialTheme.typography.titleLarge,
    )
    checklist.items.forEach { item ->
        Text(
            text = "${statusLabel(item.status)} ${item.label}: ${item.detail}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun setupActions(
    state: SetupUiState,
    actions: SetupUiActions,
) {
    if (state.showUsbPermission) {
        Button(onClick = actions.onUsbPermission) { Text("Allow USB dongle") }
    }
    if (state.showNotificationPermission) {
        Button(onClick = actions.onNotificationPermission) { Text("Allow notifications") }
    }
    Button(onClick = actions.onBluetoothSettings) { Text("Open Bluetooth settings") }
}

@Composable
private fun captionModeControls(
    state: SetupUiState,
    actions: SetupUiActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Use Floating/Compact captions")
        Switch(
            checked = state.floatingMode,
            onCheckedChange = actions.onFloatingModeChanged,
        )
    }
    if (state.showOverlayPermission) {
        Button(onClick = actions.onOverlayPermission) {
            Text("Allow display over other apps")
        }
    }
}

private fun statusLabel(status: SetupStatus): String =
    when (status) {
        SetupStatus.Ready -> "Ready —"
        SetupStatus.ActionRequired -> "Action required —"
        SetupStatus.Optional -> "Optional —"
    }
