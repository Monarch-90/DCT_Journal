package com.dct_journal.domain.usecase

import android.util.Log
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse
import com.dct_journal.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RegisterDeviceUseCase(
    private val repository: AuthRepository,
) {
    private val tag = "RegisterDeviceUseCase"

    operator fun invoke(androidId: String, orderNumber: String): Flow<RegisterResponse> = flow {
        if (orderNumber.isBlank() || orderNumber.toIntOrNull() == null) {

            Log.w(tag, "Попытка регистрации с неверным номером: '$orderNumber'")

            emit(RegisterResponse(false, "Порядковый номер должен быть числом"))
            return@flow
        }

        val request = RegisterRequest(androidId = androidId, orderNumber = orderNumber)

        Log.d(tag, "Выполнение на регистрацию: $request")
        // Вызываем метод репозитория и эмитим результат

        try {
            emit(repository.registerDevice(request))
        } catch (e: Exception) {
            Log.e(tag, "Исключение при вызове репозитория: ${e.message}", e)

            emit(RegisterResponse(false, "Исключение: ${e.message}"))
        }
    }
}