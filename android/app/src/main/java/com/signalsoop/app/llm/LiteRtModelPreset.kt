package com.signalsoop.app.llm

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import android.content.Context
import java.io.File

/**
 * LiteRT-community `.task` bundles for MediaPipe `tasks-genai` (see cil-graph android/SECURITY.md).
 */
enum class LiteRtModelPreset(
    val title: String,
    val filename: String,
    val canonicalDownloadUrl: String,
    val needsHfAuth: Boolean,
    val approxMb: Int,
    val recommendedRamMb: Int,
    val preferredBackend: LlmInference.Backend?,
    val notes: String,
) {
    SMOLLM_CPU(
        title = "SmolLM-135M (smallest)",
        filename = "SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
        canonicalDownloadUrl =
            "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
        needsHfAuth = false,
        approxMb = 250,
        recommendedRamMb = 3,
        preferredBackend = LlmInference.Backend.CPU,
        notes = "Best for 3 GiB-class RAM devices.",
    ),
    QWEN2_5_05B_CPU(
        title = "Qwen2.5-0.5B Instruct (recommended)",
        filename = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        canonicalDownloadUrl =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        needsHfAuth = false,
        approxMb = 420,
        recommendedRamMb = 4,
        preferredBackend = LlmInference.Backend.CPU,
        notes = "Default balance of quality and compatibility.",
    ),
    GEMMA3_1B_GPU(
        title = "Gemma 3 1B (GPU, gated)",
        filename = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        canonicalDownloadUrl =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        needsHfAuth = true,
        approxMb = 900,
        recommendedRamMb = 6,
        preferredBackend = LlmInference.Backend.GPU,
        notes = "Requires Hugging Face token for download.",
    ),
    ;

    fun target(cacheDir: File): File = File(cacheDir, filename)

    fun targetFile(context: Context): File = ModelStorage.presetFile(context, this)

    companion object {
        val DEFAULT: LiteRtModelPreset = QWEN2_5_05B_CPU

        fun fromOrdinal(i: Int): LiteRtModelPreset {
            val vals = entries.toTypedArray()
            return vals.getOrElse(i.coerceIn(0, vals.lastIndex)) { DEFAULT }
        }
    }
}
