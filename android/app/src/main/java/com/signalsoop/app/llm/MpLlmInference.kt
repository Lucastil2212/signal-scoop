package com.signalsoop.app.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.signalsoop.app.assistant.SignalContextBuilder
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val TAG = "MpLlmInference"

/**
 * On-device MediaPipe `tasks-genai` wrapper (from cil-graph android client).
 * One session per request to avoid unbounded JNI state.
 */
class MpLlmInference {
    private var engine: LlmInference? = null
    private var loadedPreset: LiteRtModelPreset = LiteRtModelPreset.DEFAULT

    fun isLoaded(): Boolean = engine != null

    fun load(context: Context, preset: LiteRtModelPreset, hintedPath: ModelDiskLocation?) {
        release()
        loadedPreset = preset
        val file = resolveModelFile(context, preset, hintedPath)
        val inferenceOptions =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(preset.maxContextTokens)
                .apply { preset.preferredBackend?.let { setPreferredBackend(it) } }
                .build()

        engine =
            try {
                LlmInference.createFromOptions(context, inferenceOptions)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to initialise LlmInference for ${file.absolutePath}", e)
                throw IllegalStateException(
                    "Model load failed — verify the .task file matches MediaPipe tasks-genai.",
                    e,
                )
            }
    }

    suspend fun generateFromScan(
        question: String,
        findings: List<Finding>,
        riskSummary: RiskSummary?,
        analytics: com.signalsoop.app.assistant.ScanAnalytics,
        taskHint: String = "Answer using only the scan facts. Be concise.",
    ): String =
        withContext(Dispatchers.IO) {
            val llm = engine ?: error("Load a .task model before asking questions.")
            val maxInputTokens =
                loadedPreset.maxContextTokens - LiteRtModelPreset.DECODE_TOKEN_RESERVE

            val sessionOpts =
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.35f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .build()

            val session = LlmInferenceSession.createFromOptions(llm, sessionOpts)
            try {
                val prompt =
                    SignalContextBuilder.buildWithinTokenBudget(
                        question = question,
                        findings = findings,
                        riskSummary = riskSummary,
                        analytics = analytics,
                        taskHint = taskHint,
                        countTokens = session::sizeInTokens,
                        maxInputTokens = maxInputTokens,
                    )

                Log.d(TAG, "Prompt tokens=${session.sizeInTokens(prompt)} budget=$maxInputTokens")

                session.addQueryChunk(prompt)
                runGenerate(session)
            } catch (e: Throwable) {
                throw mapInferenceError(e)
            } finally {
                runCatching { session.close() }
            }
        }

    private suspend fun runGenerate(session: LlmInferenceSession): String =
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            val accumulator = StringBuilder()

            fun finishOnce() {
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(accumulator.toString().ifBlank { "(No response generated.)" })
                }
            }

            fun failOnce(t: Throwable) {
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    cont.resumeWith(Result.failure(mapInferenceError(t)))
                }
            }

            try {
                val future =
                    session.generateResponseAsync { partialText, done ->
                        if (!partialText.isNullOrEmpty()) accumulator.append(partialText)
                        if (done) finishOnce()
                    }

                cont.invokeOnCancellation {
                    future.cancel(true)
                    finished.set(true)
                }
            } catch (e: Exception) {
                failOnce(e)
            }
        }

    fun release() {
        runCatching { engine?.close() }
        engine = null
    }
}

private fun mapInferenceError(e: Throwable): Exception {
    val msg = e.message.orEmpty()
    return if (
        msg.contains("OUT_OF_RANGE", ignoreCase = true) ||
        msg.contains("Graph has errors", ignoreCase = true) ||
        msg.contains("predict async", ignoreCase = true)
    ) {
        IllegalStateException(
            "The scan was too large for this on-device model's context window. " +
                "Try SmolLM-135M, ask a shorter question, or scan again with fewer nearby devices visible.",
            e,
        )
    } else if (e is Exception) {
        e
    } else {
        Exception(e)
    }
}

private fun resolveModelFile(ctx: Context, preset: LiteRtModelPreset, hint: ModelDiskLocation?): File {
    if (hint != null) {
        val h = File(hint.absolutePath)
        if (h.exists() && h.isFile && h.length() > 512L) return h
    }

    val default = ModelStorage.presetFile(ctx, preset)
    if (!default.exists() || default.length() <= 512L) {
        throw IllegalStateException(
            """
            LiteRT model (.task) not found.

            Expected: ${default.absolutePath}

            Use Ask → Download preset, or import a .task file via Pick model.
            Reference: ${preset.canonicalDownloadUrl}
            """.trimIndent(),
        )
    }
    return default
}
