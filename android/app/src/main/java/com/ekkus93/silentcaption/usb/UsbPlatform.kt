package com.ekkus93.silentcaption.usb

interface UsbPlatformDevice {
    val identity: UsbDeviceIdentity
}

interface UsbPlatform {
    fun devices(): List<UsbPlatformDevice>
    fun hasPermission(device: UsbPlatformDevice): Boolean
    fun requestPermission(device: UsbPlatformDevice)
    fun open(device: UsbPlatformDevice): UsbByteTransport?
}

class UsbConnectionCoordinator(
    private val platform: UsbPlatform,
    private val onState: (UsbTransportState) -> Unit,
) {
    private var selected: UsbPlatformDevice? = null

    fun discover(): UsbDeviceIdentity? {
        val devices = platform.devices()
        val characterized = devices.firstOrNull { PrototypeUsbIds.isCharacterizedBridge(it.identity) }
        val device = characterized ?: devices.singleOrNull()
        if (device == null) {
            onState(
                if (devices.isEmpty()) {
                    UsbTransportState.Failed(UsbTransportError.NO_DEVICE, "No USB device attached")
                } else {
                    UsbTransportState.Failed(
                        UsbTransportError.UNSUPPORTED_DEVICE,
                        "No characterized CP210x bridge; explicit user selection is required",
                    )
                },
            )
            return null
        }
        selected = device
        if (!platform.hasPermission(device)) {
            onState(UsbTransportState.PermissionRequired(device.identity))
            return device.identity
        }
        return device.identity
    }

    fun requestPermission() {
        val device = selected ?: return
        platform.requestPermission(device)
    }

    fun onPermissionResult(granted: Boolean): UsbByteTransport? {
        val device = selected ?: return null
        if (!granted) {
            onState(UsbTransportState.Failed(UsbTransportError.PERMISSION_DENIED, "USB permission denied"))
            return null
        }
        val transport = platform.open(device)
        if (transport == null) {
            onState(UsbTransportState.Failed(UsbTransportError.OPEN_FAILED, "Unable to open USB serial device"))
            return null
        }
        onState(UsbTransportState.Ready(device.identity))
        return transport
    }

    fun onDetached(deviceId: Int) {
        if (selected?.identity?.deviceId == deviceId) {
            selected = null
            onState(UsbTransportState.Detached)
        }
    }
}
