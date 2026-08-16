package com.ekkus93.silentcaption.model

import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

data class ModelManifestEntry(
    val backendId: String,
    val modelId: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val license: String,
    val source: String,
    val languages: List<String>,
    val compatibility: String,
    val performanceGuidance: String,
) {
    init {
        require(backendId.isNotBlank())
        require(modelId.isNotBlank())
        require(version.isNotBlank())
        require(URI(url).scheme.equals("https", ignoreCase = true))
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(sizeBytes > 0)
        require(license.isNotBlank())
        require(source.isNotBlank())
        require(languages.isNotEmpty())
        require(compatibility.isNotBlank())
    }
}

data class InstalledModel(
    val entry: ModelManifestEntry,
    val file: File,
)

sealed interface ModelInstallResult {
    data class Installed(val model: InstalledModel) : ModelInstallResult

    data class Failed(val reason: ModelInstallFailure) : ModelInstallResult
}

enum class ModelInstallFailure {
    InsufficientSpace,
    DownloadFailed,
    SizeMismatch,
    HashMismatch,
    Cancelled,
}

fun interface ModelDownloadSource {
    fun open(url: String): InputStream
}

fun interface FreeSpaceProbe {
    fun availableBytes(directory: File): Long
}

fun interface CancellationProbe {
    fun isCancelled(): Boolean
}

class ModelManager(
    private val root: File,
    private val source: ModelDownloadSource,
    private val freeSpace: FreeSpaceProbe = FreeSpaceProbe { it.usableSpace },
) {
    fun install(
        entry: ModelManifestEntry,
        cancellation: CancellationProbe = CancellationProbe { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ModelInstallResult {
        root.mkdirs()
        if (freeSpace.availableBytes(root) < requiredSpace(entry.sizeBytes)) {
            return ModelInstallResult.Failed(ModelInstallFailure.InsufficientSpace)
        }

        val target = modelFile(entry)
        val temporary = File(root, ".${target.name}.part")
        temporary.delete()
        val downloaded = download(entry, temporary, cancellation, onProgress)
        if (downloaded != null) {
            temporary.delete()
            return ModelInstallResult.Failed(downloaded)
        }
        if (temporary.length() != entry.sizeBytes) {
            temporary.delete()
            return ModelInstallResult.Failed(ModelInstallFailure.SizeMismatch)
        }
        if (!sha256(temporary).equals(entry.sha256, ignoreCase = true)) {
            temporary.delete()
            return ModelInstallResult.Failed(ModelInstallFailure.HashMismatch)
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return ModelInstallResult.Failed(ModelInstallFailure.DownloadFailed)
        }
        return ModelInstallResult.Installed(InstalledModel(entry, target))
    }

    fun installed(entry: ModelManifestEntry): InstalledModel? {
        val file = modelFile(entry)
        return file
            .takeIf { it.isFile && it.length() == entry.sizeBytes }
            ?.takeIf { sha256(it).equals(entry.sha256, ignoreCase = true) }
            ?.let { InstalledModel(entry, it) }
    }

    fun delete(entry: ModelManifestEntry, activeModelId: String?): Boolean {
        if (entry.modelId == activeModelId) return false
        val file = modelFile(entry)
        return !file.exists() || file.delete()
    }

    private fun download(
        entry: ModelManifestEntry,
        temporary: File,
        cancellation: CancellationProbe,
        onProgress: (Long, Long) -> Unit,
    ): ModelInstallFailure? =
        try {
            source.open(entry.url).use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        if (cancellation.isCancelled()) return ModelInstallFailure.Cancelled
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        onProgress(written, entry.sizeBytes)
                        if (written > entry.sizeBytes) return ModelInstallFailure.SizeMismatch
                    }
                }
            }
            null
        } catch (_: Exception) {
            ModelInstallFailure.DownloadFailed
        }

    private fun modelFile(entry: ModelManifestEntry) =
        File(root, "${safe(entry.backendId)}-${safe(entry.modelId)}-${safe(entry.version)}.model")

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun requiredSpace(size: Long): Long =
        if (size > Long.MAX_VALUE / 2) Long.MAX_VALUE else size * 2

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
