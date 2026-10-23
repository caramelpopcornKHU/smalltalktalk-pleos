package com.smalltoktok.pleos.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 로그인 토큰을 Android Keystore 키(AES-GCM)로 암호화해 SharedPreferences에 둔다.
 * Pleos 보안 가이드: 저장 위치와 관계없이 민감정보는 암호화, 키는 Keystore로 보호, 코드에 키를 두지 않는다.
 * 키는 기기 밖으로 나가지 않고, 앱을 지우면 같이 사라진다.
 */
class SecureStore(private val prefs: SharedPreferences) {

    fun getString(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        if (!stored.startsWith(PREFIX)) {
            // 암호화 전에 저장된 값(2026-10-23 이전 설치)은 읽은 뒤 암호화해서 다시 쓴다
            putString(name, stored)
            return stored
        }
        return runCatching { decrypt(stored.removePrefix(PREFIX)) }.getOrNull()
    }

    fun putString(name: String, value: String) {
        prefs.edit().putString(name, PREFIX + encrypt(value)).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    /** IV(12바이트, 매번 새로) + 암호문을 Base64로 */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "smalltoktok_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val PREFIX = "v1:"
    }
}
