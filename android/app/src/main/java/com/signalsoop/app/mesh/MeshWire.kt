package com.signalsoop.app.mesh

import org.json.JSONObject

/** Wire envelopes compatible with evrus-v0 `{ kind: "mail", item }` plus mesh voice frames. */
object MeshWire {
    const val KIND_MAIL = "mail"
    const val KIND_PREKEYS = "prekeys"
    const val KIND_VOICE = "voice"

    fun mailEnvelope(item: JSONObject): String =
        JSONObject().put("kind", KIND_MAIL).put("item", item).toString()

    fun prekeysEnvelope(bundle: JSONObject): String =
        JSONObject().put("kind", KIND_PREKEYS).put("bundle", bundle).toString()

    fun voiceEnvelope(seq: Int, sessionId: String, pcmB64: String): String =
        JSONObject()
            .put("kind", KIND_VOICE)
            .put("seq", seq)
            .put("sessionId", sessionId)
            .put("pcm", pcmB64)
            .toString()

    fun parse(bytes: ByteArray): JSONObject? = runCatching { JSONObject(String(bytes)) }.getOrNull()

    fun chatItem(
        sessionId: String,
        from: String,
        to: String,
        enc: MeshEncryptedFields,
    ): JSONObject =
        JSONObject()
            .put("type", "chat")
            .put("sessionId", sessionId)
            .put("from", from)
            .put("to", to)
            .put("ts", System.currentTimeMillis())
            .put("counter", enc.counter)
            .put("ciphertext", enc.ciphertextB64)
            .put("iv", enc.ivB64)
            .put("tag", enc.tagB64)
            .put("ad", JSONObject(enc.adJson))

    fun sessionInitItem(
        sessionId: String,
        from: String,
        to: String,
        ephPubB64: String,
        oneTimePubB64: String?,
    ): JSONObject =
        JSONObject()
            .put("type", "session_init")
            .put("sessionId", sessionId)
            .put("from", from)
            .put("to", to)
            .put("ephPub", ephPubB64)
            .put("oneTimePub", oneTimePubB64 ?: JSONObject.NULL)
            .put("ts", System.currentTimeMillis())

    data class MeshEncryptedFields(
        val counter: Int,
        val ciphertextB64: String,
        val ivB64: String,
        val tagB64: String,
        val adJson: String,
    )
}
