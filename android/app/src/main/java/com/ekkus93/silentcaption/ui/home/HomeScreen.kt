package com.ekkus93.silentcaption.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekkus93.silentcaption.session.CaptionSessionPhase

enum class CaptionDisplayMode {
    Floating,
    Reader,
    Compact,
}

data class DongleStatus(
    val usbReady: Boolean,
    val bluetoothRouteReady: Boolean,
    val firmware: String? = null,
    val protocol: String? = null,
)

data class HomeUiState(
    val dongle: DongleStatus,
    val modelReady: Boolean,
    val backendLabel: String,
    val languageLabel: String,
    val displayMode: CaptionDisplayMode = CaptionDisplayMode.Reader,
    val sessionPhase: CaptionSessionPhase = CaptionSessionPhase.Unavailable,
    val errorDetail: String? = null,
) {
    val blockers: List<String>
        get() =
            buildList {
                if (!dongle.usbReady) add("USB dongle is not ready")
                if (!dongle.bluetoothRouteReady) add("Bluetooth media route is not ready")
                if (!modelReady) add("Speech model is not installed and verified")
            }

    val canStart: Boolean
        get() = blockers.isEmpty() && sessionPhase == CaptionSessionPhase.Ready
}

data class HomeUiActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onDisplayModeChanged: (CaptionDisplayMode) -> Unit,
)

@Composable
fun homeScreen(
    state: HomeUiState,
    actions: HomeUiActions,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Silent Caption", style = MaterialTheme.typography.headlineMedium)
            dongleCard(state)
            Text("ASR: ${state.backendLabel} · ${state.languageLabel}")
            displayModeSelector(state.displayMode, actions.onDisplayModeChanged)
            sessionStatus(state)
            primaryAction(state, actions)
        }
    }
}

@Composable
private fun dongleCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Dongle", style = MaterialTheme.typography.titleMedium)
            Text("USB: ${readyLabel(state.dongle.usbReady)}")
            Text("Bluetooth/A2DP: ${readyLabel(state.dongle.bluetoothRouteReady)}")
            Text("Firmware: ${state.dongle.firmware ?: "Unavailable"}")
            Text("Protocol: ${state.dongle.protocol ?: "Unavailable"}")
            if (state.blockers.isEmpty()) {
                Text("Ready to caption")
            } else {
                state.blockers.forEach { Text("Blocked: $it") }
            }
        }
    }
}

@Composable
private fun displayModeSelector(
    selected: CaptionDisplayMode,
    onSelected: (CaptionDisplayMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Display mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionDisplayMode.entries.forEach { mode ->
                Button(onClick = { onSelected(mode) }, enabled = mode != selected) {
                    Text(if (mode == selected) "${mode.name} ✓" else mode.name)
                }
            }
        }
    }
}

@Composable
private fun sessionStatus(state: HomeUiState) {
    val text =
        when (state.sessionPhase) {
            CaptionSessionPhase.Listening -> "Listening"
            CaptionSessionPhase.Reconnecting -> "Reconnecting"
            CaptionSessionPhase.Error -> "Error: ${state.errorDetail ?: "Unknown session error"}"
            CaptionSessionPhase.Starting -> "Starting"
            CaptionSessionPhase.Stopping -> "Stopping"
            CaptionSessionPhase.Ready -> "Ready"
            CaptionSessionPhase.Unavailable -> "Unavailable"
        }
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun primaryAction(
    state: HomeUiState,
    actions: HomeUiActions,
) {
    val active =
        state.sessionPhase in
            setOf(
                CaptionSessionPhase.Starting,
                CaptionSessionPhase.Listening,
                CaptionSessionPhase.Reconnecting,
            )
    Button(
        onClick = if (active) actions.onStop else actions.onStart,
        enabled = active || state.canStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (active) "Stop Listening" else "Start Listening")
    }
}

private fun readyLabel(ready: Boolean) = if (ready) "Ready" else "Not ready"
