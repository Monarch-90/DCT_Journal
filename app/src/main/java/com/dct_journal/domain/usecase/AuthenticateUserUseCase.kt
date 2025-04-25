package com.dct_journal.domain.usecase

import android.util.Base64
import android.util.Log
import com.dct_journal.Constants
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


                // 6. Анализ статуса и формирование сообщения для UI (С ИЗМЕНЕНИЯМИ)
                val status = decryptedData.get("status")?.asString ?: Constants.ERROR
                // --- ИЗВЛЕКАЕМ ВРЕМЯ СЕРВЕРА (оно есть при OK и DEVICE_NOT_FOUND) ---
                // Используем безопасное получение и даем заглушку "время?", если его нет
                val serverTimestamp = decryptedData.get("serverTimestamp")?.asString ?: "время?"
                // ------------------------------------------------------------------

                when (status) {
                    Constants.OK -> {
                        val userLogin = decryptedData.get("userLogin")?.asString ?: "???"
                        val deviceIdentifier =
                            decryptedData.get("deviceIdentifier")?.asString ?: "???"
                        displayMessage =
                            "Исполнитель: $userLogin\n\n" +
                                    "Вход с терминала: $deviceIdentifier\n\n" + // Номер устройства
                                    "Время операции: $serverTimestamp" // Добавляем время с сервера
                        finalSuccess = true // Успех
                        Log.i(
                            tag,
                            "Статус ОК: Пользователь '$userLogin', Устройство '$deviceIdentifier', Время '$serverTimestamp'. Full success."
                        )
                    }

                    Constants.DEVICE_NOT_FOUND -> {
                        val userLogin = decryptedData.get("userLogin")?.asString ?: "???"
                        val deviceIdentifier = decryptedData.get("deviceIdentifier")?.asString
                            ?: "???" // Сам Android ID
                        displayMessage =
                            "Исполнитель: $userLogin\n\n" +
                                    "Вход с терминала - ID: $deviceIdentifier\n" +
                                    "(Добавьте устройство в базу данных)\n\n" + // Указываем что не найден
                                    "Время операции: $serverTimestamp" // Добавляем время с сервера
                        finalSuccess = false // Неуспех для WMS
                        Log.w(
                            tag,
                            "Статус DEVICE_NOT_FOUND: Пользователь '$userLogin', Устройство '$deviceIdentifier', Время '$serverTimestamp'."
                        )
                    }

                    // Constants.USER_NOT_FOUND - убран с сервера
                    Constants.USER_INVALID_PREFIX -> {
                        displayMessage =
                            decryptedData.get("message")?.asString ?: "Ошибка формата ШК"
                        // Можно добавить время и сюда, если сервер будет его присылать для этого статуса
                        // val ts = decryptedData.get("serverTimestamp")?.asString
                        // if (ts != null) displayMessage += "\n\n$ts"
                        finalSuccess = false
                        Log.w(tag, "Статус $status: $displayMessage")
                    }

                    else -> { // Constants.ERROR или неизвестный статус
                        displayMessage =
                            decryptedData.get("message")?.asString ?: "Неизвестная ошибка сервера"
                        // Можно добавить время и сюда, если сервер будет его присылать для этого статуса
                        // val ts = decryptedData.get("serverTimestamp")?.asString
                        // if (ts != null) displayMessage += "\n\n$ts"
                        finalSuccess = false
                        Log.e(tag, "Неизвестный или ошибочный статус '$status': $displayMessage")
                    }
                }

                // 7. Эмитим результат в ViewModel (структура AuthResponse)
                Log.d(tag, "Эмитим результат: Success=$finalSuccess, Message='$displayMessage'")
                emit(
                    AuthResponse(
                        success = finalSuccess, // Этот флаг для ViewModel (запускать WMS или нет)
                        message = displayMessage, // Готовая строка для UI
                        iv = responseFromServer.iv // IV нужен, т.к. модель та же
                    )
                )
            } catch (e: Exception) {
                // Ловим общие ошибки (сеть, шифрование на клиенте и т.д)
                Log.e(tag, "Критическая ошибка в UseCase: ${e.message}", e)
                displayMessage = "Критическая ошибка: ${e.localizedMessage}"
                emit(AuthResponse(success = false, message = displayMessage, iv = ""))
            }
        } // конец flow

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