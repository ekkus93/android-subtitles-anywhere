package com.ekkus93.silentcaption.usb

import java.io.Closeable

data class UsbDeviceIdentity(
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val productName: String?,
)

enum class UsbTransportError {
    NO_DEVICE,
    UNSUPPORTED_DEVICE,
    PERMISSION_DENIED,
    OPEN_FAILED,
    DISCONNECTED,
    IO_ERROR,
}

sealed interface UsbTransportState {
    data object Detached : UsbTransportState

    data class PermissionRequired(
        val device: UsbDeviceIdentity,
    ) : UsbTransportState

    data class Ready(
        val device: UsbDeviceIdentity,
    ) : UsbTransportState

    data class Failed(
        val error: UsbTransportError,
        val detail: String,
    ) : UsbTransportState
}

interface UsbByteTransport : Closeable {
    val identity: UsbDeviceIdentity

    fun read(
        destination: ByteArray,
        timeoutMs: Int,
    ): Int

    fun write(
        source: ByteArray,
        timeoutMs: Int,
    ): Int
}

object PrototypeUsbIds {
    const val CP210X_VENDOR_ID = 0x10c4
    const val CP210X_PRODUCT_ID = 0xea60

    fun isCharacterizedBridge(device: UsbDeviceIdentity): Boolean =
        device.vendorId == CP210X_VENDOR_ID && device.productId == CP210X_PRODUCT_ID
}
