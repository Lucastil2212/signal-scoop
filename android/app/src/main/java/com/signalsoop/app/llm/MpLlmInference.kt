package com.signalsoop.app.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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

    fun isLoaded(): Boolean = engine != null

    fun load(context: Context, preset: LiteRtModelPreset, hintedPath: ModelDiskLocation?) {
        release()
        val file = resolveModelFile(context, preset, hintedPath)
        val inferenceOptions =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(1024)
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

    suspend fun generate(prompt: String): String =
        withContext(Dispatchers.IO) {
            val llm = engine ?: error("Load a .task model before asking questions.")

            val sessionOpts =
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.35f)
                    .setTopK(48)
                    .setTopP(0.9f)
                    .build()

            val session = LlmInferenceSession.createFromOptions(llm, sessionOpts)
            try {
                session.addQueryChunk(prompt)
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val accumulator = StringBuilder()

                    fun finishOnce() {
                        if (finished.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(accumulator.toString())
                        }
                    }

                    val future =
                        session.generateResponseAsync { partialText, done ->
                            if (!partialText.isNullOrEmpty()) accumulator.append(partialText)
                            if (done) finishOnce()
                        }

                    cont.invokeOnCancellation {
                        future.cancel(true)
                        finished.set(true)
                    }
                }
            } finally {
                runCatching { session.close() }
            }
        }

    fun release() {
        runCatching { engine?.close() }
        engine = null
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
