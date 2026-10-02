package com.novacamera.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Private Vault: AES256-GCM encrypted file storage backed by Android Keystore
 * (MasterKey) + BiometricPrompt gate in UI. Files live in app-private storage
 * (excluded from cloud backup) so only an unlocked vault can read them.
 */
@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    fun vaultDir(): File = File(context.filesDir, "vault").apply { mkdirs() }
    fun vaultFile(name: String): File = File(vaultDir(), name)

    suspend fun importEncrypted(source: File, name: String): File = withContext(Dispatchers.IO) {
        val dest = vaultFile(name)
        val enc = EncryptedFile.Builder(
            context, dest, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        source.inputStream().use { ins ->
            enc.openFileOutput().use { out -> ins.copyTo(out) }
        }
        // Best-effort secure delete of the plain source.
        runCatching { source.writeBytes(ByteArray(source.length().toInt())); source.delete() }
        dest
    }

    suspend fun openDecrypted(name: String): File = withContext(Dispatchers.IO) {
        val src = vaultFile(name)
        val tmp = File.createTempFile("vault_view", null, context.cacheDir)
        val enc = EncryptedFile.Builder(
            context, src, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        enc.openFileInput().use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
        tmp
    }

    fun list(): List<File> = vaultDir().listFiles()?.toList() ?: emptyList()
    fun delete(name: String): Boolean = vaultFile(name).delete()
}
