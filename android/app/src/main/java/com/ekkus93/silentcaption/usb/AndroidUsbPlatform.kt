package com.ekkus93.silentcaption.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

class AndroidUsbPlatform(
    context: Context,
    private val serialFactory: (UsbDevice, UsbDeviceConnection) -> UsbByteTransport?,
) : UsbPlatform {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    override fun devices(): List<UsbPlatformDevice> =
        manager.deviceList.values.map(::AndroidUsbDevice)

    override fun hasPermission(device: UsbPlatformDevice): Boolean =
        manager.hasPermission((device as AndroidUsbDevice).device)

    override fun requestPermission(device: UsbPlatformDevice) {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.requestPermission((device as AndroidUsbDevice).device, pendingIntent)
    }

    override fun open(device: UsbPlatformDevice): UsbByteTransport? {
        val androidDevice = (device as AndroidUsbDevice).device
        if (!manager.hasPermission(androidDevice)) return null
        val connection = manager.openDevice(androidDevice) ?: return null
        return serialFactory(androidDevice, connection) ?: run {
            connection.close()
            null
        }
    }

    private class AndroidUsbDevice(val device: UsbDevice) : UsbPlatformDevice {
        override val identity = UsbDeviceIdentity(
            deviceId = device.deviceId,
            vendorId = device.vendorId,
            productId = device.productId,
            productName = device.productName,
        )
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.ekkus93.silentcaption.USB_PERMISSION"
    }
}
