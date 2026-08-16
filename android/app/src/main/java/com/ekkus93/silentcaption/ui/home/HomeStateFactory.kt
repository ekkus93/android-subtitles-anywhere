package com.ekkus93.silentcaption.ui.home

import com.ekkus93.silentcaption.session.CaptionSessionState
import com.ekkus93.silentcaption.setup.SetupChecklist
import com.ekkus93.silentcaption.setup.SetupStatus

/** Runtime facts consumed by the live home screen without coupling Compose to platform probes. */
data class HomeRuntimeFacts(
    val setup: SetupChecklist,
    val modelReady: Boolean,
    val backendLabel: String,
    val languageLabel: String,
    val session: CaptionSessionState,
    val firmware: String? = null,
    val protocol: String? = null,
)

object HomeStateFactory {
    fun create(
        facts: HomeRuntimeFacts,
        displayMode: CaptionDisplayMode,
    ): HomeUiState =
        HomeUiState(
            dongle =
                DongleStatus(
                    usbReady = setupReady(facts.setup, "USB"),
                    bluetoothRouteReady = setupReady(facts.setup, "Bluetooth"),
                    firmware = facts.firmware,
                    protocol = facts.protocol,
                ),
            modelReady = facts.modelReady,
            backendLabel = facts.backendLabel,
            languageLabel = facts.languageLabel,
            displayMode = displayMode,
            sessionPhase = facts.session.phase,
            errorDetail = facts.session.detail,
        )

    private fun setupReady(
        checklist: SetupChecklist,
        label: String,
    ): Boolean =
        checklist.items
            .firstOrNull { it.label.contains(label, ignoreCase = true) }
            ?.status == SetupStatus.Ready
}
