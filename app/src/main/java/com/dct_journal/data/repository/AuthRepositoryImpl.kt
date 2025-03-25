package com.dct_journal.data.repository

import android.util.Log
import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.util.AESEncryptionUtil

class AuthRepositoryImpl(
    private val apiService: ApiService,
) : AuthRepository {

    override suspend fun authenticateUser(
        androidId: String,
        barcode: String,
        userName: String,
    ): AuthResponse {

        return try {
            // Создание запроса
            val request = AuthRequest(
                androidId = androidId, barcode = barcode, userName = userName
            )

            Log.d("AuthRepository", "Отправляю запрос на сервер с данными: $request")

            // Отправка запроса к серверу
            val response = apiService.authenticateUser(request)
            Log.d("AuthRepository", "Получен ответ от сервера: $response")
            response
        } catch (e: Exception) {
            Log.e("AuthRepository", "Ошибка в запросе: ${e.message}", e)
            AuthResponse(false, "Ошибка сервера: ${e.message}", "UserName: $userName")
        }
    }
}