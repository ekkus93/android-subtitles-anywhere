package com.ekkus93.silentcaption.model

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManagementTest {
    private val payload = "verified-model".toByteArray()
    private val entry =
        ModelManifestEntry(
            backendId = "whisper",
            modelId = "tiny-multilingual",
            version = "1",
            url = "https://models.example.invalid/tiny.bin",
            sha256 = sha256(payload),
            sizeBytes = payload.size.toLong(),
            license = "MIT",
            source = "test fixture",
            languages = listOf("multilingual"),
            compatibility = "v0.1",
            performanceGuidance = "Prefer Tiny on constrained devices.",
        )

    @Test
    fun install_verifies_hash_and_promotes_atomically() = withManager(payload) { manager, root ->
        val result = manager.install(entry)
        assertTrue(result is ModelInstallResult.Installed)
        assertNotNull(manager.installed(entry))
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun corrupt_hash_never_activates_model() = withManager("corrupt".toByteArray()) { manager, root ->
        assertEquals(
            ModelInstallResult.Failed(ModelInstallFailure.SizeMismatch),
            manager.install(entry),
        )
        assertNull(manager.installed(entry))
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun matching_size_corrupt_hash_is_rejected() =
        withManager(ByteArray(payload.size) { 1 }) { manager, _ ->
            assertEquals(
                ModelInstallResult.Failed(ModelInstallFailure.HashMismatch),
                manager.install(entry),
            )
        }

    @Test
    fun low_storage_fails_before_download() {
        var opened = false
        val root = Files.createTempDirectory("models").toFile()
        val manager =
            ModelManager(
                root,
                ModelDownloadSource {
                    opened = true
                    ByteArrayInputStream(payload)
                },
                FreeSpaceProbe { entry.sizeBytes },
            )
        assertEquals(
            ModelInstallResult.Failed(ModelInstallFailure.InsufficientSpace),
            manager.install(entry),
        )
        assertFalse(opened)
    }

    @Test
    fun cancellation_removes_partial_file() = withManager(payload) { manager, root ->
        assertEquals(
            ModelInstallResult.Failed(ModelInstallFailure.Cancelled),
            manager.install(entry, CancellationProbe { true }),
        )
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failed_upgrade_preserves_last_known_good_model() = withManager(payload) { manager, _ ->
        assertTrue(manager.install(entry) is ModelInstallResult.Installed)
        val badUpgrade = entry.copy(version = "2", sha256 = "0".repeat(64))
        assertEquals(
            ModelInstallResult.Failed(ModelInstallFailure.HashMismatch),
            manager.install(badUpgrade),
        )
        assertNotNull(manager.installed(entry))
        assertNull(manager.installed(badUpgrade))
    }

    @Test
    fun active_model_is_protected_from_deletion() = withManager(payload) { manager, _ ->
        assertTrue(manager.install(entry) is ModelInstallResult.Installed)
        assertFalse(manager.delete(entry, entry.modelId))
        assertNotNull(manager.installed(entry))
        assertTrue(manager.delete(entry, null))
        assertNull(manager.installed(entry))
    }

    @Test(expected = IllegalArgumentException::class)
    fun manifest_rejects_non_https_urls() {
        entry.copy(url = "http://models.example.invalid/tiny.bin")
    }

    private fun withManager(
        bytes: ByteArray,
        block: (ModelManager, File) -> Unit,
    ) {
        val root = Files.createTempDirectory("models").toFile()
        try {
            block(
                ModelManager(
                    root,
                    ModelDownloadSource { ByteArrayInputStream(bytes) },
                    FreeSpaceProbe { Long.MAX_VALUE },
                ),
                root,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
