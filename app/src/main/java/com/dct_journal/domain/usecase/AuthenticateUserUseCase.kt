package com.dct_journal.domain.usecase

import android.util.Base64
import android.util.Log
import com.dct_journal.Constants
import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.ServerResponsePayload
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.domain.model.AuthResult
import com.dct_journal.util.AESEncryptionUtil
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthenticateUserUseCase(
    private val repository: AuthRepository,
    private val encryptionUtil: AESEncryptionUtil,
) {
    private val tag = "AuthenticateUseCase"

    private fun extractLoginFromBarcode(rawBarcode: String): String {
        Constants.PREFIX_USER_BARCODE.forEach { prefix ->
            if (rawBarcode.startsWith(
                    prefix,
                    ignoreCase = true
                )
            ) { // Добавим ignoreCase для надежности
                return rawBarcode.substring(prefix.length)
            }
        }
        Log.w(
            tag,
            "Ни один из известных префиксов (${Constants.PREFIX_USER_BARCODE}) не найден в ШК: '$rawBarcode'. Возвращаем как есть."
        )
        return rawBarcode // Если ни один префикс не подошел
    }

    operator fun invoke(currentAndroidId: String, rawUserBarcode: String): Flow<AuthResult> = flow {
        try {
            val encryptedBarcode = encryptionUtil.encrypt(rawUserBarcode)
            val iv = encryptionUtil.getIv()
            Log.d(
                tag,
                "Подготовка /scan. AndroidID: $currentAndroidId, EncBarcode: $encryptedBarcode, IV: $iv"
            )

            val authRequest =
                AuthRequest(androidId = currentAndroidId, barcode = encryptedBarcode, iv = iv)
            val responseFromServer =
                repository.authenticateUser(authRequest) // Передаем AuthRequest

            Log.d(
                tag,
                "Ответ от /scan: TopSuccess=${responseFromServer.success}, EncMsg=${responseFromServer.message}, IV=${responseFromServer.iv}"
            )

            if (responseFromServer.message.isEmpty() || responseFromServer.iv.isEmpty()) {
                Log.e(tag, "Сервер (/scan) вернул пустое сообщение или IV.")
                emit(AuthResult.GenericError("Ошибка ответа сервера (пусто)", null))
                return@flow
            }

            val decryptedJsonPayload: String? = try {
                if (isBase64(responseFromServer.message)) {
                    encryptionUtil.decrypt(responseFromServer.message, responseFromServer.iv)
                } else {
                    Log.e(tag, "Сообщение от /scan не Base64: ${responseFromServer.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка дешифровки ответа /scan: ${e.message}", e)
                null
            }

            if (decryptedJsonPayload == null) {
                emit(AuthResult.GenericError("Ошибка обработки ответа /scan", null))
                return@flow
            }
            Log.d(tag, "Дешифрованный payload от /scan: $decryptedJsonPayload")

            val payload: ServerResponsePayload? = try {
                Gson().fromJson(decryptedJsonPayload, ServerResponsePayload::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(tag, "Ошибка парсинга JSON из /scan: '$decryptedJsonPayload'", e)
                null
            }

            if (payload?.status == null) {
                emit(AuthResult.GenericError("Ошибка формата данных от /scan", null))
                return@flow
            }

            // Используем верхнеуровневый responseFromServer.success для определения, можно ли запускать WMS
            val wmsLaunchAllowed = responseFromServer.success

            val extractedLogin = extractLoginFromBarcode(rawUserBarcode)
            val userLogin = payload.userLogin ?: extractedLogin

            val deviceIdentifierFromServer = payload.deviceIdentifier ?: currentAndroidId
            val timestamp = payload.serverTimestamp ?: "Нет времени"
            val message = payload.message ?: "Нет сообщения от сервера"

            Log.i(
                tag,
                "Parsed payload: Status='${payload.status}', WMSLaunch=${wmsLaunchAllowed}, User='${userLogin}', DeviceID='${deviceIdentifierFromServer}', Time='${timestamp}'"
            )

            when (payload.status) {
                Constants.SCAN_STATUS_OK -> {
                    if (wmsLaunchAllowed) {
                        // Сравниваем deviceIdentifier из ответа сервера с currentAndroidId ТСД
                        // Если они НЕ равны, значит сервер прислал OrderNumber (ТСД зарегистрирован)
                        if (deviceIdentifierFromServer != currentAndroidId) {
                            Log.d(
                                tag,
                                "Статус 'ok', deviceIdentifier ($deviceIdentifierFromServer) != currentAndroidId ($currentAndroidId). ТСД зарегистрирован."
                            )
                            emit(
                                AuthResult.AuthenticationSuccess(
                                    userLogin,
                                    deviceIdentifierFromServer,
                                    timestamp,
                                    rawUserBarcode,
                                    currentAndroidId
                                )
                            )
                        } else {
                            // Если они РАВНЫ, значит сервер прислал AndroidID (ТСД НЕ зарегистрирован)
                            Log.d(
                                tag,
                                "Статус 'ok', deviceIdentifier ($deviceIdentifierFromServer) == currentAndroidId ($currentAndroidId). ТСД НЕ зарегистрирован."
                            )
                            emit(
                                AuthResult.AuthenticationSuccessDeviceUnregistered(
                                    userLogin,
                                    currentAndroidId,
                                    timestamp,
                                    rawUserBarcode,
                                    currentAndroidId
                                )
                            )
                        }
                    } else {
                        // Этот случай маловероятен, если сервер всегда ставит success=true при status="ok"
                        Log.w(tag, "Статус 'ok', но WMSLaunchAllowed=false. Противоречие.")
                        emit(
                            AuthResult.GenericError(
                                "Противоречие в ответе сервера (ok, но WMS запрещен)",
                                timestamp
                            )
                        )
                    }
                }

                Constants.SCAN_STATUS_USER_LOCKED_ELSEWHERE -> emit(
                    AuthResult.UserLockedElsewhere(
                        message,
                        userLogin,
                        timestamp
                    )
                )

                Constants.SCAN_STATUS_DEVICE_OCCUPIED -> emit(
                    AuthResult.DeviceOccupied(
                        message,
                        userLogin,
                        timestamp
                    )
                )

                Constants.SCAN_STATUS_USER_INVALID_PREFIX -> emit(
                    AuthResult.InvalidPrefix(
                        message,
                        userLogin,
                        timestamp
                    )
                )

                else -> {
                    Log.w(
                        tag,
                        "Неизвестный или необработанный статус от сервера: '${payload.status}'. Сообщение: '$message'"
                    )
                    emit(AuthResult.GenericError(message, timestamp))
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Критическая ошибка в AuthenticateUserUseCase: ${e.message}", e)
            emit(AuthResult.GenericError("Критическая ошибка: ${e.localizedMessage}", null))
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