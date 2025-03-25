package com.dct_journal.domain.usecase

import android.util.Base64
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.util.AESEncryptionUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthenticateUserUseCase(
    private val repository: AuthRepository,
    private val encryptionUtil: AESEncryptionUtil,
) {

    operator fun invoke(androidId: String, barcode: String, userName: String): Flow<AuthResponse> =
        flow {
            // Шифруем данные перед отправкой
            val encryptedBarcode = encryptionUtil.encrypt(barcode)
            val encryptedUserName = encryptionUtil.encrypt(userName)

            // Получения ответа из репозитория
            val response =
                repository.authenticateUser(androidId, encryptedBarcode, encryptedUserName)

            // Проверяем данные на Base64 и выполняем дешифровку
            val decryptedMessage = if (isBase64(response.message)) {
                encryptionUtil.decrypt(response.message!!)
            } else {
                response.message ?: "Сообщение отсутствует"
            }
            val decryptUserName = if (isBase64(response.userName)) {
                encryptionUtil.decrypt(response.userName!!)
            } else {
                response.userName ?: "Имя пользователя отсутствует"
            }

            emit(
                AuthResponse(
                    success = response.success,
                    message = decryptedMessage,
                    userName = decryptUserName
                )
            )
        }

    private fun isBase64(input: String?): Boolean {
        if (input.isNullOrEmpty()) {
            return false
        }

        return try {
            Base64.decode(input, Base64.DEFAULT)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}