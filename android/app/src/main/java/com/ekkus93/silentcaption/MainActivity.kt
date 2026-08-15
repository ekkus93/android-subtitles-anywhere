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
import com.ekkus93.silentcaption.setup.SetupEvaluator
import com.ekkus93.silentcaption.setup.SetupInputs
import com.ekkus93.silentcaption.setup.SetupStatus
import com.ekkus93.silentcaption.ui.theme.SilentCaptionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SilentCaptionTheme {
                SilentCaptionApp()
            }
        }
    }
}

@Composable
fun SilentCaptionApp() {
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Silent Caption setup", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your phone sends media audio to the Silent Caption Bluetooth dongle. The dongle returns digital audio over USB for on-device speech recognition. Raw audio and captions stay local by default; Silent Caption does not require a cloud upload to operate.",
            )
            Text(
                "Bluetooth pairing by itself does not prove media is routed to the dongle. Ready is shown only when Android exposes the required A2DP media-output route.",
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

            if (usbDevice != null && !probe.hasUsbPermission(usbDevice)) {
                Button(onClick = { probe.requestUsbPermission(usbDevice) }) {
                    Text("Allow USB dongle")
                }
            }
            if (probe.notificationsRequired() && !probe.notificationsGranted()) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                ) {
                    Text("Allow notifications")
                }
            }
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                Text("Open Bluetooth settings")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Use Floating/Compact captions")
                Switch(checked = floatingMode, onCheckedChange = { floatingMode = it })
            }
            if (floatingMode && !probe.overlayGranted()) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                ) {
                    Text("Allow display over other apps")
                }
            }
            Button(onClick = { refresh++ }) {
                Text("Refresh setup")
            }
            Text(
                "Speech-model installation is intentionally reported as not ready until SC-320 model management provides a verified installed model.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun statusLabel(status: SetupStatus): String =
    when (status) {
        SetupStatus.Ready -> "Ready —"
        SetupStatus.ActionRequired -> "Action required —"
        SetupStatus.Optional -> "Optional —"
    }
