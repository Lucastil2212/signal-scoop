package com.signalsoop.app.mesh.crypto

import android.util.Base64
import java.security.MessageDigest

internal object MeshBytes {
    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun b64Encode(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    fun b64Decode(b64: String): ByteArray = Base64.decode(b64, Base64.NO_WRAP)

    fun pad32(key: ByteArray): ByteArray =
        if (key.size == 32) key else sha256(key)
}
