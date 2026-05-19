package com.signalsoop.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val downloadClient =
    OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

/** HTTPS-only checkpoint download (user-initiated; scan data never sent). */
suspend fun downloadLiteRtCheckpoint(
    url: String,
    destination: File,
    bearerToken: String?,
    onChunk: suspend (receivedBytes: Long) -> Unit = {},
): Long =
    withContext(Dispatchers.IO) {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Refusing non-HTTPS model download."
        }
        destination.parentFile?.mkdirs()
        destination.createNewFile()
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .apply {
                    if (!bearerToken.isNullOrBlank()) {
                        header("Authorization", "Bearer ${bearerToken.trim()}")
                    }
                }
                .get()
                .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download failed (${response.code} ${response.message})")
            }
            val body = response.body ?: error("Empty HTTP body downloading model.")
            var received = 0L
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buf = ByteArray(64 shl 10)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        onChunk(received)
                    }
                    output.flush()
                }
            }
            if (destination.length() < 8192L) {
                destination.delete()
                error("Downloaded file too small — check URL or Hugging Face token.")
            }
            received
        }
    }
