package com.twojstar.llmbench.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.twojstar.llmbench.data.model.ApiKeyConfig
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class ApiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ApiKeyConfig = runCatching {
        val iv = preferences.getString(IV_KEY, null) ?: return ApiKeyConfig()
        val ciphertext = preferences.getString(CIPHERTEXT_KEY, null) ?: return ApiKeyConfig()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).decodeToString()
        val json = JSONObject(plaintext)
        ApiKeyConfig(
            geminiKey = json.optString("gemini"),
            openAiKey = json.optString("openai"),
            claudeKey = json.optString("claude"),
            deepseekKey = json.optString("deepseek"),
            kimiKey = json.optString("kimi"),
            openRouterKey = json.optString("openrouter"),
            aiHubMixKey = json.optString("aihubmix")
        )
    }.getOrDefault(ApiKeyConfig())

    fun save(config: ApiKeyConfig): Boolean = runCatching {
        val plaintext = JSONObject()
            .put("gemini", config.geminiKey)
            .put("openai", config.openAiKey)
            .put("claude", config.claudeKey)
            .put("deepseek", config.deepseekKey)
            .put("kimi", config.kimiKey)
            .put("openrouter", config.openRouterKey)
            .put("aihubmix", config.aiHubMixKey)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        preferences.edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        true
    }.getOrDefault(false)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "llmbench-api-keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES_NAME = "encrypted_api_keys"
        const val IV_KEY = "iv"
        const val CIPHERTEXT_KEY = "ciphertext"
    }
}
