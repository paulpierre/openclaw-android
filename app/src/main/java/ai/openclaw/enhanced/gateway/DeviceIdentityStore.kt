// ===========================================================================
// DeviceIdentityStore.kt
// ai/openclaw/enhanced/gateway/DeviceIdentityStore.kt
//
// Generates and persists an Ed25519 keypair for device identity.
// Uses BouncyCastle low-level API directly to avoid Android provider conflicts.
// ===========================================================================

package ai.openclaw.enhanced.gateway

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom

class DeviceIdentityStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "openclaw_device_identity"
        private const val KEY_PRIVATE = "ed25519_private_raw"
        private const val KEY_PUBLIC = "ed25519_public_raw"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val privateKeyParams: Ed25519PrivateKeyParameters
    private val publicKeyParams: Ed25519PublicKeyParameters

    // Raw 32-byte public key bytes
    private val rawPublicKeyBytes: ByteArray

    init {
        val existingPrivate = prefs.getString(KEY_PRIVATE, null)
        val existingPublic = prefs.getString(KEY_PUBLIC, null)

        if (existingPrivate != null && existingPublic != null) {
            // Restore persisted keypair from raw 32-byte keys
            val privBytes = Base64.decode(existingPrivate, Base64.NO_WRAP)
            val pubBytes = Base64.decode(existingPublic, Base64.NO_WRAP)
            privateKeyParams = Ed25519PrivateKeyParameters(privBytes, 0)
            publicKeyParams = Ed25519PublicKeyParameters(pubBytes, 0)
            rawPublicKeyBytes = pubBytes
            Timber.i("Loaded existing Ed25519 device identity")
        } else {
            // Generate fresh Ed25519 keypair using BouncyCastle low-level API
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPair = generator.generateKeyPair()
            privateKeyParams = keyPair.private as Ed25519PrivateKeyParameters
            publicKeyParams = keyPair.public as Ed25519PublicKeyParameters
            rawPublicKeyBytes = publicKeyParams.encoded

            // Persist raw 32-byte keys
            prefs.edit()
                .putString(KEY_PRIVATE, Base64.encodeToString(privateKeyParams.encoded, Base64.NO_WRAP))
                .putString(KEY_PUBLIC, Base64.encodeToString(rawPublicKeyBytes, Base64.NO_WRAP))
                .apply()

            Timber.i("Generated new Ed25519 device identity")
        }
    }

    /** SHA-256 hex digest of the raw 32-byte Ed25519 public key */
    fun getDeviceId(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawPublicKeyBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Raw 32-byte Ed25519 public key, base64url-encoded (no padding) */
    fun getPublicKeyBase64Url(): String {
        return Base64.encodeToString(
            rawPublicKeyBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /** Sign a UTF-8 payload string, return base64url signature (no padding) */
    fun sign(payload: String): String {
        val signer = Ed25519Signer()
        signer.init(true, privateKeyParams)
        val msgBytes = payload.toByteArray(Charsets.UTF_8)
        signer.update(msgBytes, 0, msgBytes.size)
        val signatureBytes = signer.generateSignature()
        return Base64.encodeToString(
            signatureBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
