package com.dct_journal.data.repository

import android.util.Log
import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.model.StatusUpdateRequest

class DeviceRepositoryImpl(
    private val apiService: ApiService
) : DeviceRepository {

    private val tag = "DeviceRepository"

    override suspend fun sendStatus(request: StatusUpdateRequest): Boolean {
        return try {
            Log.d(tag, "Отправляю запрос на /api/v1/terminal/status: $request")
            // Мы просто вызываем метод. Если он не выбросит исключение (т.е. сервер вернет 2xx),
            // значит все прошло успешно.
            apiService.sendStatus(request)
            Log.i(tag, "Статус успешно отправлен.")
            true
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при отправке статуса: ${e.message}", e)
            false
        }
    }
}