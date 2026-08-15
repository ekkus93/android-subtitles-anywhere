package com.ekkus93.silentcaption.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class Cp210xSerialTransport private constructor(
    override val identity: UsbDeviceIdentity,
    private val connection: UsbDeviceConnection,
    private val port: UsbSerialPort,
) : UsbByteTransport {
    override fun read(destination: ByteArray, timeoutMs: Int): Int = port.read(destination, timeoutMs)

    override fun write(source: ByteArray, timeoutMs: Int): Int {
        port.write(source, timeoutMs)
        return source.size
    }

    override fun close() {
        runCatching { port.close() }
        connection.close()
    }

    companion object {
        const val BAUD_RATE = 921600

        fun open(device: UsbDevice, connection: UsbDeviceConnection): UsbByteTransport? {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return null
            val port = driver.ports.firstOrNull() ?: return null
            return try {
                port.open(connection)
                port.setParameters(
                    BAUD_RATE,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE,
                )
                // Deliberately leave DTR/RTS deasserted. The prototype uses UART data only;
                // toggling modem-control lines must not be used as part of normal session setup.
                runCatching { port.dtr = false }
                runCatching { port.rts = false }
                Cp210xSerialTransport(
                    identity = UsbDeviceIdentity(
                        device.deviceId,
                        device.vendorId,
                        device.productId,
                        device.productName,
                    ),
                    connection = connection,
                    port = port,
                )
            } catch (error: Exception) {
                runCatching { port.close() }
                null
            }
        }
    }
}
