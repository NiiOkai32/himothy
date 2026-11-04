package com.campusfit.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A random SQLCipher passphrase wrapped by a non-exportable Android Keystore key. */
object DatabaseKey {
    @Synchronized fun load(context: Context): ByteArray {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "campusfit.database"
        val key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val file = java.io.File(context.noBackupFilesDir, "database.key")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (file.exists()) {
            val wrapped = file.readBytes()
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
            return cipher.doFinal(wrapped.copyOfRange(12, wrapped.size))
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val atomic = android.util.AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(cipher.iv + cipher.doFinal(passphrase)); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
        return passphrase
    }
}
