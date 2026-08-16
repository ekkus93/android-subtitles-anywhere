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
        val items =
            listOf(
                usbItem(inputs),
                bluetoothItem(inputs),
                modelItem(inputs),
                notificationItem(inputs),
                overlayItem(inputs),
            )
        return SetupChecklist(
            items = items,
            ready = items.none { it.status == SetupStatus.ActionRequired },
        )
    }

    private fun usbItem(inputs: SetupInputs): SetupItem {
        val detail =
            when {
                !inputs.usbAttached -> "Connect the Silent Caption dongle."
                !inputs.usbPermission -> "Allow access to the connected USB dongle."
                else -> "Dongle connected and permitted."
            }
        return requiredItem("USB dongle", detail, inputs.usbAttached && inputs.usbPermission)
    }

    private fun bluetoothItem(inputs: SetupInputs): SetupItem {
        val detail =
            if (inputs.bluetoothRouteReady) {
                "Android currently exposes a Bluetooth A2DP media output route."
            } else {
                "Select the dongle as the media-output route. " +
                    "A Bluetooth connection alone is not enough."
            }
        return requiredItem("Bluetooth media route", detail, inputs.bluetoothRouteReady)
    }

    private fun modelItem(inputs: SetupInputs): SetupItem =
        requiredItem(
            "Speech model",
            if (inputs.modelReady) {
                "Selected speech model is available."
            } else {
                "Install or select a speech model."
            },
            inputs.modelReady,
        )

    private fun notificationItem(inputs: SetupInputs): SetupItem {
        val ready = !inputs.notificationsRequired || inputs.notificationsGranted
        return requiredItem(
            "Notifications",
            if (ready) {
                "Foreground-session notification is available."
            } else {
                "Allow notifications for active caption sessions."
            },
            ready,
        )
    }

    private fun overlayItem(inputs: SetupInputs): SetupItem {
        if (!inputs.floatingModeRequested) {
            return SetupItem(
                "Floating captions",
                "Optional until Floating or Compact mode is selected.",
                SetupStatus.Optional,
            )
        }
        return requiredItem(
            "Floating captions",
            if (inputs.overlayGranted) {
                "Display-over-other-apps access is enabled."
            } else {
                "Allow display over other apps for Floating or Compact captions."
            },
            inputs.overlayGranted,
        )
    }

    private fun requiredItem(
        label: String,
        detail: String,
        ready: Boolean,
    ) = SetupItem(
        label = label,
        detail = detail,
        status = if (ready) SetupStatus.Ready else SetupStatus.ActionRequired,
    )
}
