package com.dct_journal.data.repository

import android.util.Log
import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.network.model.DeregisterRequest
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse

class AuthRepositoryImpl(
    private val apiService: ApiService,
) : AuthRepository {

    private val tag = "AuthRepository"

    override suspend fun authenticateUser(request: AuthRequest): AuthResponse {
        return try {
            Log.d(tag, "Отправляю запрос /scan: $request")
            val response = apiService.authenticateUser(request)
            Log.d(tag, "Получен ответ от /scan: $response")
            response
        } catch (e: Exception) {
            Log.e(tag, "Ошибка в запросе /scan: ${e.message}", e)
            AuthResponse(false, "Ошибка сети: ${e.message}", "")
        }
    }

    override suspend fun registerDevice(request: RegisterRequest): RegisterResponse {
        return try {
            Log.d(tag, "Отправляю запрос /add_device: $request")
            val response = apiService.registerDevice(request)
            Log.d(tag, "Получен ответ от /add_device: $response")
            response
        } catch (e: Exception) {
            Log.e(tag, "Ошибка в запросе /add_device: ${e.message}", e)
            RegisterResponse(false, "Ошибка сети: ${e.message}")
        }
    }

    override suspend fun deregisterDevice(request: DeregisterRequest): AuthResponse {
        return try {
            Log.d(tag, "Отправляю запрос /deregister: $request")
            val response = apiService.deregisterDevice(request)
            Log.d(tag, "Получен ответ от /deregister: $response")
            response
        } catch (e: Exception) {
            Log.e(tag, "Ошибка в запросе /deregister: ${e.message}", e)
            // Возвращаем структуру AuthResponse, т.к. сервер ее отдает
            AuthResponse(false, "Ошибка сети (дерегистрация): ${e.message}", "")
        }
    }
}