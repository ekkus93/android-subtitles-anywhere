package com.ekkus93.silentcaption.model

import android.content.Context
import java.io.File

object DefaultModels {
    val whisperTinyMultilingual =
        ModelManifestEntry(
            backendId = "whisper-tiny-multilingual",
            modelId = "ggml-tiny.bin",
            version = "1.9.1",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            sizeBytes = 77_691_713,
            license = "MIT",
            source = "whisper.cpp model distribution",
            languages = listOf("multilingual"),
            compatibility = "whisper.cpp 1.9.1",
            performanceGuidance = "Default multilingual model for constrained mobile devices.",
        )
}

class AndroidModelReadiness(
    context: Context,
) {
    private val manager =
        ModelManager(
            root = File(context.filesDir, "models"),
            source = ModelDownloadSource { error("readiness probe does not download models") },
        )

    fun whisperTinyInstalled(): Boolean = manager.installed(DefaultModels.whisperTinyMultilingual) != null
}
