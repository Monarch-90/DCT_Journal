package com.dct_journal.domain.usecase

import android.util.Base64
import android.util.Log
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.util.AESEncryptionUtil
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthenticateUserUseCase(
    private val repository: AuthRepository,
    private val encryptionUtil: AESEncryptionUtil,
) {

    // Константы для статусов ответа сервера (должны совпадать с сервером)
    private object ServersStatus {
        const val OK = "ok"
        const val DEVICE_NOT_FOUND = "device_not_found"
        const val USER_NOT_FOUND = "user_not_found"
        const val USER_INVALID_PREFIX = "user_invalid_prefix"
        const val ERROR = "error" // Общая ошибка
    }

    // Тег для логов
    private val tag = "AuthenticateUseCase"

    operator fun invoke(
        androidId: String,
        barcode: String,
    ): Flow<AuthResponse> = // Возвращаем Flow с моделью AuthResponse
        flow {
            var finalSuccess = false // Будет true только если статус от сервера "ok"
            var displayMessage = "Неизвестная ошибка" // Сообщение для UI по умолчанию

            try {
                // 1. Шифруем данные перед отправкой
                val encryptedBarcode = encryptionUtil.encrypt(barcode)
                val iv = encryptionUtil.getIv() // IV генерируется при шифровании

                Log.d(
                    tag,
                    "Подготовка запроса. AndroidId: $androidId, EncryptedBarcode: $encryptedBarcode, IV: $iv"
                )

                // 2. Запрос к репозиторию (который вызовет API)
                val responseFromServer =
                    repository.authenticateUser(androidId, encryptedBarcode, iv)

                Log.d(
                    tag,
                    "Ответ от репозитория получен. Success=${responseFromServer.success}, EncryptedMessage=${responseFromServer.message}, IV=${responseFromServer.iv}"
                )

                // 3. Проверка ответа и IV на пустоту
                if (responseFromServer.message.isNullOrEmpty() || responseFromServer.iv.isNullOrEmpty()) {
                    Log.d(tag, "Сервер вернул пустое сообщение или IV.")
                    displayMessage = "Ошибка ответа сервера (пусто)"
                    // success остаётся false, emit результат
                    emit(
                        AuthResponse(
                            success = finalSuccess,
                            message = displayMessage,
                            iv = responseFromServer.iv
                        )
                    )
                    return@flow // Прерываем flow
                }


                // 4. Дешифровка поля 'message', которое содержит зашифрованный JSON
                val decryptedJsonPayload: String? = try {
                    if (isBase64(responseFromServer.message)) {
                        encryptionUtil.decrypt(responseFromServer.message, responseFromServer.iv)
                    } else {
                        Log.d(
                            tag,
                            "Сообщение от сервера не является валидным Base64: ${responseFromServer.message}"
                        )
                        null // Ошибка формата
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Ошибка дешифровки ответе сервера: ${e.message}", e)
                    null // Ошибка дешифровки
                }

                // Если дешифровка не удалась
                if (decryptedJsonPayload == null) {
                    displayMessage = "Ошибка обработки ответа"
                    emit(
                        AuthResponse(
                            success = finalSuccess,
                            message = displayMessage,
                            iv = responseFromServer.iv
                        )
                    )
                    return@flow
                }

                Log.d(tag, "Дешифрованный JSON payload: $decryptedJsonPayload")

                // 5. Парсинг дешифрованного JSON
                val decryptedData: JsonObject? = try {
                    Gson().fromJson(decryptedJsonPayload, JsonObject::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(tag, "Ошибка парсинга JSON из ответа: '$decryptedJsonPayload'", e)
                    null
                }

                if (decryptedData == null) {
                    displayMessage = "Ошибка формата данных из сервера"
                    emit(
                        AuthResponse(
                            success = finalSuccess,
                            message = displayMessage,
                            iv = responseFromServer.iv
                        )
                    )
                    return@flow
                }


                // 6. Анализ статуса и формирование сообщения для UI
                val status = decryptedData.get("status")?.asString ?: ServersStatus.ERROR

                when (status) {
                    ServersStatus.OK -> {
                        val userLogin = decryptedData.get("userLogin")?.asString ?: "???"
                        val deviceId = decryptedData.get("deviceId")?.asString ?: "???"
                        displayMessage =
                            "Исполнитель - $userLogin \nВход с терминала - №$deviceId" // Формат: "ЛОГИН / НОМЕР"
                        finalSuccess = true // Единственный успешный случайный
                        Log.i(
                            tag,
                            "Статус ОК: Пользователь '$userLogin', Устройство '$deviceId'. Full success."
                        )
                    }

                    ServersStatus.DEVICE_NOT_FOUND -> {
                        val userLogin = decryptedData.get("userLogin")?.asString ?: "???"
                        val deviceId = decryptedData.get("deviceId")?.asString ?: "???"
                        displayMessage =
                            "Исполнитель - $userLogin \nВход с терминала - №$deviceId" // Формат: "ЛОГИН / AndroidId"
                        finalSuccess = false // Неуспех, т.к. устройство не найдено
                        Log.w(
                            tag,
                            "Статус DEVICE_NOT_FOUND: Пользователь '$userLogin', Устройство '$deviceId'."
                        )
                    }

                    ServersStatus.USER_NOT_FOUND, ServersStatus.USER_INVALID_PREFIX -> {
                        // Берём сообщение об ошибке из JSON
                        displayMessage =
                            decryptedData.get("message")?.asString ?: "Ошибка пользователя"
                        finalSuccess = false // Неуспех
                        Log.w(tag, "Статус $status: $displayMessage")
                    }

                    else -> { // ServiceStatus.ERROR или неизвестный статус
                        displayMessage =
                            decryptedData.get("message")?.asString ?: "Неизвестная ошибка сервера"
                        finalSuccess = false // Неуспех
                        Log.e(tag, "Неизвестный или ошибочный статус '$status': $displayMessage")
                    }
                }

                // 7. Эмитим результат в ViewModel
                // finalSuccess определяет успех всей операции
                // displayMessage - строка для отображения в UI
                Log.d(tag, "Эмитим результат: Success=$finalSuccess, Message='$displayMessage'")
                emit(
                    AuthResponse(
                        success = finalSuccess,
                        message = displayMessage,
                        iv = responseFromServer.iv
                    )
                )
            } catch (e: Exception) {
                // Ловим общие ошибки (сеть, шифрование на клиенте и т.д)
                Log.e(tag, "Критическая ошибка в UseCase: ${e.message}", e)
                displayMessage =
                    "Критическая ошибка: ${e.localizedMessage}" // Показать сообщение об ошибке
                emit(
                    AuthResponse(
                        success = false,
                        message = displayMessage,
                        iv = ""
                    )
                ) // IV неизвестен или неважен
            }
        }

    // Утилита для проверки Base64
    private fun isBase64(input: String?): Boolean {
        if (input.isNullOrEmpty()) {
            return false
        }
        return try {
            Base64.decode(input, Base64.NO_WRAP) // NO_WRAP более строгий
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}