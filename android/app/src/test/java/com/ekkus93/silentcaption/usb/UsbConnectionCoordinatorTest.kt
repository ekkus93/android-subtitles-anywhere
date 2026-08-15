package com.ekkus93.silentcaption.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbConnectionCoordinatorTest {
    @Test
    fun characterizedCp210xIsPreferredAndRequestsPermission() {
        val other = FakeDevice(UsbDeviceIdentity(1, 0x1234, 0x5678, "Other"))
        val cp210x = FakeDevice(UsbDeviceIdentity(2, 0x10c4, 0xea60, "CP210x"))
        val platform = FakePlatform(listOf(other, cp210x), permitted = false)
        val states = mutableListOf<UsbTransportState>()
        val coordinator = UsbConnectionCoordinator(platform, states::add)
        assertEquals(cp210x.identity, coordinator.discover())
        assertTrue(states.single() is UsbTransportState.PermissionRequired)
        coordinator.requestPermission()
        assertEquals(cp210x.identity, platform.requested?.identity)
    }

    @Test
    fun ambiguousUnsupportedDevicesNeverBecomeReady() {
        val platform = FakePlatform(
            listOf(
                FakeDevice(UsbDeviceIdentity(1, 1, 1, "A")),
                FakeDevice(UsbDeviceIdentity(2, 2, 2, "B")),
            ),
            permitted = true,
        )
        val states = mutableListOf<UsbTransportState>()
        val coordinator = UsbConnectionCoordinator(platform, states::add)
        assertNull(coordinator.discover())
        assertTrue((states.single() as UsbTransportState.Failed).error == UsbTransportError.UNSUPPORTED_DEVICE)
    }

    @Test
    fun denialAndOpenFailureAreExplicit() {
        val device = FakeDevice(UsbDeviceIdentity(7, 0x10c4, 0xea60, "CP210x"))
        val platform = FakePlatform(listOf(device), permitted = false)
        val states = mutableListOf<UsbTransportState>()
        val coordinator = UsbConnectionCoordinator(platform, states::add)
        coordinator.discover()
        assertNull(coordinator.onPermissionResult(false))
        assertEquals(UsbTransportError.PERMISSION_DENIED, (states.last() as UsbTransportState.Failed).error)

        platform.permitted = true
        coordinator.discover()
        platform.openResult = null
        assertNull(coordinator.onPermissionResult(true))
        assertEquals(UsbTransportError.OPEN_FAILED, (states.last() as UsbTransportState.Failed).error)
    }

    @Test
    fun detachClearsSelectedDevice() {
        val device = FakeDevice(UsbDeviceIdentity(9, 0x10c4, 0xea60, "CP210x"))
        val platform = FakePlatform(listOf(device), permitted = true)
        val states = mutableListOf<UsbTransportState>()
        val coordinator = UsbConnectionCoordinator(platform, states::add)
        coordinator.discover()
        coordinator.onDetached(9)
        assertTrue(states.last() is UsbTransportState.Detached)
    }

    private data class FakeDevice(override val identity: UsbDeviceIdentity) : UsbPlatformDevice

    private class FakePlatform(
        private val attached: List<UsbPlatformDevice>,
        var permitted: Boolean,
    ) : UsbPlatform {
        var requested: UsbPlatformDevice? = null
        var openResult: UsbByteTransport? = FakeTransport(attached.firstOrNull()?.identity)
        override fun devices(): List<UsbPlatformDevice> = attached
        override fun hasPermission(device: UsbPlatformDevice): Boolean = permitted
        override fun requestPermission(device: UsbPlatformDevice) { requested = device }
        override fun open(device: UsbPlatformDevice): UsbByteTransport? = openResult
    }

    private class FakeTransport(identity: UsbDeviceIdentity?) : UsbByteTransport {
        override val identity = identity ?: UsbDeviceIdentity(0, 0, 0, null)
        override fun read(destination: ByteArray, timeoutMs: Int): Int = 0
        override fun write(source: ByteArray, timeoutMs: Int): Int = source.size
        override fun close() = Unit
    }
}
