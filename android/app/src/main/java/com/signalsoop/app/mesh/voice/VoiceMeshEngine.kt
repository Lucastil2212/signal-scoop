package com.signalsoop.app.mesh.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.signalsoop.app.mesh.MeshWire
import com.signalsoop.app.mesh.crypto.MeshBytes
import com.signalsoop.app.mesh.transport.RadioMeshHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * LoRa-style local voice: small PCM frames over the radio mesh (Wi-Fi TCP).
 */
class VoiceMeshEngine(
    private val hub: RadioMeshHub,
    private val sessionId: String,
    private val scope: CoroutineScope,
) {
    private val seq = AtomicInteger(0)
    private var recordJob: Job? = null
    private var track: AudioTrack? = null

    fun startTransmit() {
        if (recordJob?.isActive == true) return
        val minBuf =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        recordJob =
            scope.launch(Dispatchers.IO) {
                val buf = ByteArray(FRAME_BYTES)
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        val frame =
                            MeshWire.voiceEnvelope(
                                seq.getAndIncrement(),
                                sessionId,
                                MeshBytes.b64Encode(buf.copyOf(read)),
                            )
                        hub.broadcast(frame.toByteArray())
                    }
                }
                recorder.stop()
                recorder.release()
            }
    }

    fun stopTransmit() {
        recordJob?.cancel()
        recordJob = null
    }

    fun playInbound(pcmB64: String) {
        val pcm = MeshBytes.b64Decode(pcmB64)
        if (track == null) {
            val minBuf =
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            track?.play()
        }
        track?.write(pcm, 0, pcm.size)
    }

    fun release() {
        stopTransmit()
        track?.stop()
        track?.release()
        track = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 40
        val FRAME_BYTES: Int = SAMPLE_RATE * 2 * FRAME_MS / 1000
    }
}
