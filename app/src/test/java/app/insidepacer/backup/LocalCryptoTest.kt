package app.insidepacer.backup

import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class LocalCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val crypto = LocalCrypto(object : LocalCrypto.KeyProvider {
        override fun getOrCreateKey() = key
    })

    @Test
    fun encryptDecryptRoundTrip() {
        val payload = "inside pacer backup".encodeToByteArray()
        val encrypted = crypto.encrypt(payload)
        val decrypted = crypto.decrypt(encrypted)
        assertContentEquals(payload, decrypted)
    }

    @Test
    fun decryptFailsWhenTampered() {
        val payload = "secure".encodeToByteArray()
        val encrypted = crypto.encrypt(payload)
        encrypted[encrypted.lastIndex] = encrypted.last().inc()
        assertFailsWith<SecurityException> {
            crypto.decrypt(encrypted)
        }
    }
}
