package com.ekkus93.silentcaption.setup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class AndroidSetupProbe(
    private val context: Context,
) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun attachedUsbDevice(): UsbDevice? = usbManager.deviceList.values.firstOrNull()

    fun hasUsbPermission(device: UsbDevice?): Boolean = device != null && usbManager.hasPermission(device)

    fun requestUsbPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        usbManager.requestPermission(device, pendingIntent)
    }

    fun bluetoothMediaRouteReady(): Boolean =
        audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

    fun notificationsRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun notificationsGranted(): Boolean =
        !notificationsRequired() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(context)

    companion object {
        const val ACTION_USB_PERMISSION = "com.ekkus93.silentcaption.USB_PERMISSION"
    }
}
