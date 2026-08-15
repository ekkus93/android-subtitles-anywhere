package com.ekkus93.silentcaption.setup

enum class SetupStatus {
    Ready,
    ActionRequired,
    Optional,
}

data class SetupItem(
    val label: String,
    val detail: String,
    val status: SetupStatus,
)

data class SetupInputs(
    val usbAttached: Boolean,
    val usbPermission: Boolean,
    val bluetoothRouteReady: Boolean,
    val modelReady: Boolean,
    val notificationsRequired: Boolean,
    val notificationsGranted: Boolean,
    val floatingModeRequested: Boolean,
    val overlayGranted: Boolean,
)

data class SetupChecklist(
    val items: List<SetupItem>,
    val ready: Boolean,
)

object SetupEvaluator {
    fun evaluate(inputs: SetupInputs): SetupChecklist {
        val usbReady = inputs.usbAttached && inputs.usbPermission
        val notificationReady = !inputs.notificationsRequired || inputs.notificationsGranted
        val overlayReady = !inputs.floatingModeRequested || inputs.overlayGranted
        val items =
            listOf(
                SetupItem(
                    "USB dongle",
                    when {
                        !inputs.usbAttached -> "Connect the Silent Caption dongle."
                        !inputs.usbPermission -> "Allow access to the connected USB dongle."
                        else -> "Dongle connected and permitted."
                    },
                    if (usbReady) SetupStatus.Ready else SetupStatus.ActionRequired,
                ),
                SetupItem(
                    "Bluetooth media route",
                    if (inputs.bluetoothRouteReady) {
                        "Android currently exposes a Bluetooth A2DP media output route."
                    } else {
                        "Select the dongle as the media-output route. A Bluetooth connection alone is not enough."
                    },
                    if (inputs.bluetoothRouteReady) SetupStatus.Ready else SetupStatus.ActionRequired,
                ),
                SetupItem(
                    "Speech model",
                    if (inputs.modelReady) "Selected speech model is available." else "Install or select a speech model.",
                    if (inputs.modelReady) SetupStatus.Ready else SetupStatus.ActionRequired,
                ),
                SetupItem(
                    "Notifications",
                    if (notificationReady) "Foreground-session notification is available." else "Allow notifications for active caption sessions.",
                    if (notificationReady) SetupStatus.Ready else SetupStatus.ActionRequired,
                ),
                SetupItem(
                    "Floating captions",
                    when {
                        !inputs.floatingModeRequested -> "Optional until Floating or Compact mode is selected."
                        inputs.overlayGranted -> "Display-over-other-apps access is enabled."
                        else -> "Allow display over other apps for Floating or Compact captions."
                    },
                    when {
                        !inputs.floatingModeRequested -> SetupStatus.Optional
                        inputs.overlayGranted -> SetupStatus.Ready
                        else -> SetupStatus.ActionRequired
                    },
                ),
            )
        return SetupChecklist(
            items = items,
            ready = usbReady && inputs.bluetoothRouteReady && inputs.modelReady && notificationReady && overlayReady,
        )
    }
}
