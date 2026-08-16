package com.ekkus93.silentcaption.model

import java.io.File
import java.io.InputStream
import java.net.URI

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
    private val files = ModelFileOperations(root)

    fun install(
        entry: ModelManifestEntry,
        cancellation: CancellationProbe = CancellationProbe { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ModelInstallResult {
        root.mkdirs()
        val failure = preflight(entry) ?: downloadAndValidate(entry, cancellation, onProgress)
        return failure ?: ModelInstallResult.Installed(InstalledModel(entry, files.modelFile(entry)))
    }

    fun installed(entry: ModelManifestEntry): InstalledModel? {
        val file = files.modelFile(entry)
        return file.takeIf { files.isValid(entry, it) }?.let { InstalledModel(entry, it) }
    }

    fun delete(entry: ModelManifestEntry, activeModelId: String?): Boolean {
        if (entry.modelId == activeModelId) return false
        val file = files.modelFile(entry)
        return !file.exists() || file.delete()
    }

    private fun preflight(entry: ModelManifestEntry): ModelInstallResult.Failed? =
        if (freeSpace.availableBytes(root) < requiredSpace(entry.sizeBytes)) {
            ModelInstallResult.Failed(ModelInstallFailure.InsufficientSpace)
        } else {
            null
        }

    private fun downloadAndValidate(
        entry: ModelManifestEntry,
        cancellation: CancellationProbe,
        onProgress: (Long, Long) -> Unit,
    ): ModelInstallResult.Failed? {
        val target = files.modelFile(entry)
        val temporary = files.temporaryFile(entry)
        temporary.delete()
        val failure =
            download(entry, temporary, cancellation, onProgress)
                ?: files.validate(entry, temporary)
                ?: files.promote(temporary, target)
        if (failure != null) temporary.delete()
        return failure?.let(ModelInstallResult::Failed)
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
                    copyDownload(input, output, entry.sizeBytes, cancellation, onProgress)
                }
            }
        } catch (_: Exception) {
            ModelInstallFailure.DownloadFailed
        }

    private fun copyDownload(
        input: InputStream,
        output: java.io.OutputStream,
        expectedSize: Long,
        cancellation: CancellationProbe,
        onProgress: (Long, Long) -> Unit,
    ): ModelInstallFailure? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (written <= expectedSize && !cancellation.isCancelled()) {
            val count = input.read(buffer)
            if (count < 0) return null
            output.write(buffer, 0, count)
            written += count
            onProgress(written, expectedSize)
        }
        return if (cancellation.isCancelled()) {
            ModelInstallFailure.Cancelled
        } else {
            ModelInstallFailure.SizeMismatch
        }
    }

    private fun requiredSpace(size: Long): Long =
        if (size > Long.MAX_VALUE / 2) Long.MAX_VALUE else size * 2
}
