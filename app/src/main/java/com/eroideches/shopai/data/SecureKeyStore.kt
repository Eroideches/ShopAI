package com.eroideches.shopai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.keysStore by preferencesDataStore(name = "api_keys")

/**
 * Chiavi API inserite dall'utente. Cifrate con AES-GCM usando una chiave che non esce mai
 * dall'Android Keystore; su disco resta solo il testo cifrato.
 */
class SecureKeyStore(private val context: Context) {
    private val alias = "shopai_api_keys"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String? = runCatching {
        val bytes = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
        String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()

    /** Tutte le chiavi in chiaro (solo in memoria). */
    val flow: Flow<Map<String, String>> = context.keysStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) -> (v as? String)?.let { decrypt(it) }?.let { k.name to it } }.toMap()
    }

    suspend fun all(): Map<String, String> = flow.first()

    suspend fun get(id: String): String? = all()[id]?.takeIf { it.isNotBlank() }

    suspend fun set(id: String, value: String) {
        val v = value.trim()
        context.keysStore.edit { if (v.isEmpty()) it.remove(stringPreferencesKey(id)) else it[stringPreferencesKey(id)] = encrypt(v) }
    }

    suspend fun remove(ids: Collection<String>) = context.keysStore.edit { p -> ids.forEach { p.remove(stringPreferencesKey(it)) } }
}
