package com.ekkus93.silentcaption.model

import java.io.File
import java.security.MessageDigest

internal class ModelFileOperations(
    private val root: File,
) {
    fun modelFile(entry: ModelManifestEntry) = File(
        root,
        "${safe(entry.backendId)}-${safe(entry.modelId)}-${safe(entry.version)}.model",
    )

    fun temporaryFile(entry: ModelManifestEntry) = File(root, ".${modelFile(entry).name}.part")

    fun isValid(
        entry: ModelManifestEntry,
        file: File,
    ): Boolean =
        file.isFile &&
            file.length() == entry.sizeBytes &&
            sha256(file).equals(entry.sha256, ignoreCase = true)

    fun validate(
        entry: ModelManifestEntry,
        file: File,
    ): ModelInstallFailure? =
        when {
            file.length() != entry.sizeBytes -> ModelInstallFailure.SizeMismatch
            !sha256(file).equals(entry.sha256, ignoreCase = true) -> ModelInstallFailure.HashMismatch
            else -> null
        }

    fun promote(
        temporary: File,
        target: File,
    ): ModelInstallFailure? =
        if (temporary.renameTo(target)) {
            null
        } else {
            ModelInstallFailure.DownloadFailed
        }

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
