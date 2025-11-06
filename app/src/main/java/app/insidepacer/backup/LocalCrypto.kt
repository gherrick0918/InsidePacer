package app.insidepacer.backup

import android.content.Context
import androidx.security.crypto.MasterKey
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalCrypto(private val keyProvider: KeyProvider) {
    interface KeyProvider {
        fun getOrCreateKey(): SecretKey
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        try {
            val key = keyProvider.getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            return ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + ciphertext.size)
                .putInt(iv.size)
                .put(iv)
                .put(ciphertext)
                .array()
        } catch (ex: GeneralSecurityException) {
            throw SecurityException("Unable to encrypt backup payload", ex)
        }
    }

    fun decrypt(input: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(input)
            if (buffer.remaining() < Int.SIZE_BYTES) {
                throw SecurityException("Encrypted payload is truncated")
            }
            val ivLength = buffer.int
            if (ivLength <= 0 || ivLength > 32 || buffer.remaining() < ivLength) {
                throw SecurityException("Invalid IV length")
            }
            val iv = ByteArray(ivLength)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val key = keyProvider.getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (ex: GeneralSecurityException) {
            throw SecurityException("Unable to decrypt backup payload", ex)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128

        fun create(context: Context): LocalCrypto {
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val provider = object : KeyProvider {
                override fun getOrCreateKey(): SecretKey {
                    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                    val entry = keyStore.getEntry(masterKey.keyAlias, null) as? KeyStore.SecretKeyEntry
                        ?: throw IllegalStateException("MasterKey entry missing")
                    return entry.secretKey
                }
            }
            return LocalCrypto(provider)
        }

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
