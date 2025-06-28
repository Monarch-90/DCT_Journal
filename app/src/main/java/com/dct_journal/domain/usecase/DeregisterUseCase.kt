package com.dct_journal.domain.usecase

import android.util.Log
import com.dct_journal.data.network.model.DeregisterRequest
import com.dct_journal.data.network.model.ServerResponsePayload
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.domain.model.DeregistrationResult
import com.dct_journal.util.AESEncryptionUtil
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Base64

class DeregisterUseCase(
    private val repository: AuthRepository,
    private val encryptionUtil: AESEncryptionUtil,
) {
    private val tag = "DeregisterUseCase"

    operator fun invoke(
        deviceAndroidId: String,
        barcodeToDeregister: String,
    ): Flow<DeregistrationResult> = flow {
        try {
            Log.d(
                tag,
                "Начало дерегистрации. DeviceID: $deviceAndroidId, Barcode: $barcodeToDeregister"
            )
            val encryptedBarcode = encryptionUtil.encrypt(barcodeToDeregister)
            val iv = encryptionUtil.getIv()

            val request = DeregisterRequest(
                androidId = deviceAndroidId,
                barcode = encryptedBarcode,
                iv = iv
            )

            val responseFromServer = repository.deregisterDevice(request)
            Log.d(
                tag,
                "Ответ от /deregister: TopSuccess=${responseFromServer.success}, EncMsg=${responseFromServer.message}, IV=${responseFromServer.iv}"
            )

            if (responseFromServer.message.isEmpty() || responseFromServer.iv.isEmpty()) {
                Log.e(tag, "Сервер (/deregister) вернул пустое сообщение или IV.")
                emit(
                    DeregistrationResult.Failure(
                        "Ошибка ответа сервера (пусто)",
                        null,
                        DeregistrationResult.FailureType.INVALID_FORMAT
                    )
                )
                return@flow
            }

            val decryptedJsonPayload: String? = try {
                if (isBase64(responseFromServer.message)) {
                    encryptionUtil.decrypt(responseFromServer.message, responseFromServer.iv)
                } else {
                    Log.e(tag, "Сообщение от /deregister не Base64: ${responseFromServer.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка дешифровки ответа /deregister: ${e.message}", e)
                null
            }

            if (decryptedJsonPayload == null) {
                emit(
                    DeregistrationResult.Failure(
                        "Ошибка обработки ответа /deregister",
                        null,
                        DeregistrationResult.FailureType.INVALID_FORMAT
                    )
                )
                return@flow
            }
            Log.d(tag, "Дешифрованный payload от /deregister: $decryptedJsonPayload")

            val payload: ServerResponsePayload? = try {
                Gson().fromJson(decryptedJsonPayload, ServerResponsePayload::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(tag, "Ошибка парсинга JSON из /deregister: '$decryptedJsonPayload'", e)
                null
            }

            if (payload?.status == null || payload.message == null || payload.serverTimestamp == null) {
                Log.e(
                    tag,
                    "Ошибка формата данных от /deregister: payload, status, message или timestamp is null. Payload: $payload"
                )
                emit(
                    DeregistrationResult.Failure(
                        "Ошибка формата данных от /deregister",
                        payload?.serverTimestamp,
                        DeregistrationResult.FailureType.INVALID_FORMAT
                    )
                )
                return@flow
            }

            if (responseFromServer.success) { // Используем верхнеуровневый success
                Log.i(tag, "Дерегистрация успешна на сервере. Сообщение: ${payload.message}")
                emit(DeregistrationResult.Success(payload.message, payload.serverTimestamp))
            } else {
                Log.w(tag, "Дерегистрация не удалась на сервере. Сообщение: ${payload.message}")
                emit(
                    DeregistrationResult.Failure(
                        payload.message,
                        payload.serverTimestamp,
                        DeregistrationResult.FailureType.INVALID_FORMAT
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(tag, "Критическая ошибка в DeregisterUseCase: ${e.message}", e)
            emit(
                DeregistrationResult.Failure(
                    "Критическая ошибка: ${e.localizedMessage}",
                    null,
                    DeregistrationResult.FailureType.INVALID_FORMAT
                )
            )
        }
    }

    private fun isBase64(input: String?): Boolean {
        if (input.isNullOrEmpty()) {
            return false
        }
        return try {
            Base64.decode(input, Base64.NO_WRAP); true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
