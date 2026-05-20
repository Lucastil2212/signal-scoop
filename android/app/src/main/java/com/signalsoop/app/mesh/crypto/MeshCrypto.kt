package com.signalsoop.app.mesh.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EVRUS-compatible X3DH + simplified Double Ratchet (evrus-v0 packages/messaging).
 */
object MeshCrypto {
    private val random = SecureRandom()

    data class PrekeyBundle(
        val principal: String,
        val identityPubB64: String,
        val signedPrekeyPubB64: String,
        val signedPrekeySigB64: String,
        val oneTimePubB64: String?,
    )

    data class LocalPrekeys(
        val bundle: PrekeyBundle,
        val identityPrivate: ByteArray,
        val signedPrekeyPrivate: ByteArray,
        val oneTimePrivate: ByteArray?,
    )

    data class EncryptedPayload(
        val ciphertextB64: String,
        val ivB64: String,
        val tagB64: String,
        val counter: Int,
        val adJson: String,
    )

    fun generateLocalPrekeys(principal: String): LocalPrekeys {
        val identityPrivate = ByteArray(32).also { random.nextBytes(it) }
        val identityPublic = ed25519PublicFromPrivate(identityPrivate)
        val spkPrivate = ByteArray(32).also { random.nextBytes(it) }
        val spkPublic = x25519Public(spkPrivate)
        val sig = ed25519Sign(spkPublic, identityPrivate)
        val otpPrivate = ByteArray(32).also { random.nextBytes(it) }
        val otpPublic = x25519Public(otpPrivate)
        val bundle =
            PrekeyBundle(
                principal = principal,
                identityPubB64 = MeshBytes.b64Encode(identityPublic),
                signedPrekeyPubB64 = MeshBytes.b64Encode(spkPublic),
                signedPrekeySigB64 = MeshBytes.b64Encode(sig),
                oneTimePubB64 = MeshBytes.b64Encode(otpPublic),
            )
        return LocalPrekeys(bundle, identityPrivate, spkPrivate, otpPrivate)
    }

    fun verifySignedPrekey(bundle: PrekeyBundle): Boolean =
        runCatching {
            ed25519Verify(
                MeshBytes.b64Decode(bundle.signedPrekeySigB64),
                MeshBytes.b64Decode(bundle.signedPrekeyPubB64),
                MeshBytes.b64Decode(bundle.identityPubB64),
            )
        }.getOrDefault(false)

    fun deriveResponderRootKey(
        signedPrekeyPrivate: ByteArray,
        ephPubB64: String,
        oneTimePrivate: ByteArray?,
        oneTimePubB64: String?,
    ): String {
        val dh1 = x25519Shared(signedPrekeyPrivate, MeshBytes.b64Decode(ephPubB64))
        val material =
            if (oneTimePrivate != null && oneTimePubB64 != null) {
                dh1 + x25519Shared(oneTimePrivate, MeshBytes.b64Decode(ephPubB64))
            } else {
                dh1
            }
        return MeshBytes.sha256Hex(material)
    }

    fun deriveRootKey(ephPrivate: ByteArray, signedPrekeyPubB64: String, oneTimePubB64: String?): String {
        val dh1 = x25519Shared(ephPrivate, MeshBytes.b64Decode(signedPrekeyPubB64))
        val material =
            if (oneTimePubB64 != null) {
                dh1 + x25519Shared(ephPrivate, MeshBytes.b64Decode(oneTimePubB64))
            } else {
                dh1
            }
        return MeshBytes.sha256Hex(material)
    }

    fun hkdfRoot(a: String, b: String): String = MeshBytes.sha256Hex("$a|$b".toByteArray())

    fun kdfChain(seedHex: String, label: String = ""): String {
        val digest = SHA256Digest()
        val seed = hexToBytes(seedHex)
        digest.update(seed, 0, seed.size)
        val labelBytes = ":$label".toByteArray()
        digest.update(labelBytes, 0, labelBytes.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        return out.joinToString("") { b -> "%02x".format(b) }
    }

    fun chainStep(ckHex: String): Pair<ByteArray, String> {
        val ck = hexToBytes(ckHex)
        val mkDigest = SHA256Digest()
        mkDigest.update(ck, 0, ck.size)
        mkDigest.update(":mk".toByteArray(), 0, 3)
        val mk = ByteArray(32)
        mkDigest.doFinal(mk, 0)

        val nextDigest = SHA256Digest()
        nextDigest.update(ck, 0, ck.size)
        nextDigest.update(":next".toByteArray(), 0, 5)
        val next = ByteArray(32)
        nextDigest.doFinal(next, 0)
        return mk to next.joinToString("") { b -> "%02x".format(b) }
    }

    fun deriveNonceFromMessageKey(mk: ByteArray, counter: Int): ByteArray {
        val digest = SHA256Digest()
        digest.update(mk, 0, mk.size)
        val info = "nonce:$counter".toByteArray()
        digest.update(info, 0, info.size)
        val full = ByteArray(32)
        digest.doFinal(full, 0)
        return full.copyOf(12)
    }

    fun encryptMessage(
        sendChainHex: String,
        counter: Int,
        plaintext: String,
        sessionId: String,
        from: String,
        to: String,
        dhPubB64: String,
    ): Pair<EncryptedPayload, String> {
        val (mk, nextCk) = chainStep(sendChainHex)
        val iv = deriveNonceFromMessageKey(mk, counter)
        val ad =
            JSONObject()
                .put("cty", "evrus/msg")
                .put("dhPub", dhPubB64)
                .put("topic", "evrus.mail.${principalHash(to)}")
                .put("sessionId", sessionId)
        val adBytes = ad.toString().toByteArray()
        val (ct, tag) = aeadEncrypt(mk, iv, plaintext.toByteArray(), adBytes)
        return EncryptedPayload(
            ciphertextB64 = MeshBytes.b64Encode(ct),
            ivB64 = MeshBytes.b64Encode(iv),
            tagB64 = MeshBytes.b64Encode(tag),
            counter = counter,
            adJson = ad.toString(),
        ) to nextCk
    }

    fun decryptMessage(
        recvChainHex: String,
        recvCounter: Int,
        targetCounter: Int,
        ciphertextB64: String,
        ivB64: String,
        tagB64: String,
        adJson: String,
        skipped: MutableMap<Int, ByteArray>,
    ): Pair<String, String> {
        var ck = recvChainHex
        var counter = recvCounter
        var mk: ByteArray? = skipped[targetCounter]
        if (mk == null) {
            while (counter < targetCounter) {
                val (skipMk, nextCk) = chainStep(ck)
                skipped[counter] = skipMk
                ck = nextCk
                counter++
            }
            val (recvMk, nextCk) = chainStep(ck)
            mk = recvMk
            ck = nextCk
        }
        val pt =
            aeadDecrypt(
                mk!!,
                MeshBytes.b64Decode(ivB64),
                MeshBytes.b64Decode(ciphertextB64),
                adJson.toByteArray(),
                MeshBytes.b64Decode(tagB64),
            )
        return String(pt) to ck
    }

    fun principalHash(principal: String): String = MeshBytes.sha256Hex(principal.toByteArray()).take(24)

    fun randomEphKeypair(): Pair<ByteArray, ByteArray> {
        val priv = ByteArray(32).also { random.nextBytes(it) }
        return priv to x25519Public(priv)
    }

    private fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, ad: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(MeshBytes.pad32(key), "ChaCha20"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(ad)
        val combined = cipher.doFinal(plaintext)
        val ct = combined.copyOf(combined.size - 16)
        val tag = combined.copyOfRange(combined.size - 16, combined.size)
        return ct to tag
    }

    private fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        ad: ByteArray,
        tag: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(MeshBytes.pad32(key), "ChaCha20"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(ad)
        return cipher.doFinal(ciphertext + tag)
    }

    private fun x25519Public(private: ByteArray): ByteArray {
        val public = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(private, 0, public, 0)
        return public
    }

    private fun x25519Shared(private: ByteArray, public: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(private, 0))
        val shared = ByteArray(32)
        agreement.calculateAgreement(X25519PublicKeyParameters(public, 0), shared, 0)
        return shared
    }

    private fun ed25519PublicFromPrivate(private: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(private, 0).generatePublicKey().encoded

    private fun ed25519Sign(message: ByteArray, private: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(private, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    private fun ed25519Verify(sig: ByteArray, message: ByteArray, public: ByteArray): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(public, 0))
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(sig)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
