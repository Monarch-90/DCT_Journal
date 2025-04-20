package com.dct_journal.data.repository

import android.util.Log
import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse

class AuthRepositoryImpl(
    private val apiService: ApiService,
) : AuthRepository {

    private val tag = "AuthRepository"

    override suspend fun authenticateUser(
        androidId: String,
        barcode: String,
        iv: String,
    ): AuthResponse {

        return try {
            // Создание запроса
            val request = AuthRequest(
                androidId = androidId, barcode = barcode, iv = iv
            )

            Log.d(tag, "Отправляю запрос на сервер с данными: $request")

            // Отправка запроса к серверу
            val response = apiService.authenticateUser(request)
            Log.d(tag, "Получен ответ от сервера: $response")
            response
        } catch (e: Exception) {
            Log.e(tag, "Ошибка в запросе: ${e.message}", e)
            // Возвращаем ошибку в стандартной структуре AuthResponse
            AuthResponse(false, "Ошибка сервера: ${e.message}", "IV: $iv")
        }
    }

    override suspend fun registerDevice(
        request: RegisterRequest,
    ): RegisterResponse {

        return try {
            Log.d(tag, "Отправляю запрос registerDevice: $request")
            val response = apiService.registerDevice(request)

            Log.d(tag, "Получен ответ registerDevice: $response")
            response
        } catch (e: Exception) {
            Log.e(tag, "Ошибка в запросе registerDevice: ${e.message}", e)
            // Возвращаем свою структуру ошибки, соответствующую RegisterResponse
            RegisterResponse(false, "Ошибка сети: ${e.message}")
        }
    }
}