package com.dct_journal.util

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESEncryptionUtil {

    private val key = Base64.decode("dGhpc2lzbXlzZWNyZXRrZXlmb3JhcGVz", Base64.DEFAULT)
    private var lastGeneratedIv: ByteArray? = null

    /** Метод шифрования */
    fun encrypt(input: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            lastGeneratedIv = iv
            val ivSpec = IvParameterSpec(iv)
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
            val result = Base64.encodeToString(encrypted, Base64.DEFAULT)

            Log.d("AESUtil", "Шифрование завершено: $result")
            result
        } catch (e: Exception) {
            Log.d("AESUtil", "Ошибка при шифровании: ${e.message}", e)
            throw e
        }
    }

    /** Метод дешифрования */
    fun decrypt(encryptedInput: String, ivBase64: String): String {
        return try {
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val encrypted = Base64.decode(encryptedInput, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encrypted)
            val result = String(decryptedBytes, Charsets.UTF_8)

            Log.d("AESUtil", "Дешифрование завершено: $result")
            result
        } catch (e: Exception) {
            Log.d("AESUtil", "Ошибка при дешифровании: ${e.message}", e)
            throw e
        }
    }

    fun getIv(): String {
        if (lastGeneratedIv == null) {
            throw IllegalStateException("IV ещё не сгенерирован. Сначала вызовите encrypt()!")
        }
        return Base64.encodeToString(lastGeneratedIv, Base64.DEFAULT)
    }
}