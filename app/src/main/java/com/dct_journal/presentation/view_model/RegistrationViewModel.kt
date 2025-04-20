package com.dct_journal.presentation.view_model

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dct_journal.data.network.model.RegisterResponse
import com.dct_journal.data.network.model.RegistrationUiState
import com.dct_journal.domain.usecase.RegisterDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val registerDeviceUseCase: RegisterDeviceUseCase,
    private val contentResolver: ContentResolver, // Инжектим ContentResolver для ID
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val tag = "RegistrationViewModel"

    init {
        loadAndroidId()
    }

    private fun loadAndroidId() {
        try {
            val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "Не удалось получить ID"
            _uiState.value = _uiState.value.copy(androidId = id)
            Log.d(tag, "Android ID загружен: $id")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка получения Android ID: ${e.message}", e)
            _uiState.value = _uiState.value.copy(androidId = "Ошибка получения ID")
        }
    }

    fun registerDevice(orderNumber: String) {
        val currentAndroidId = _uiState.value.androidId
        // Проверяем, что ID был успешно загружен
        if (currentAndroidId.startsWith("Ошибка") || currentAndroidId == "Загрузка...") {
            Log.e(tag, "Попытка регистрации без валидного Android ID")
            _uiState.value = _uiState.value.copy(
                registrationResult = RegisterResponse(
                    false,
                    "Не удалось получить Android ID устройства"
                ),
                errorEvent = "Не удалось получить Android ID устройства" // Показываем ошибку
            )
            return
        }

        viewModelScope.launch {
            // Устанавливаем состояние загрузки
            _uiState.value =
                _uiState.value.copy(isLoading = true, registrationResult = null, errorEvent = null)
            Log.d(tag, "Начинаем регистрацию: ID=$currentAndroidId, Number=$orderNumber")

            registerDeviceUseCase(currentAndroidId, orderNumber).collect { result ->
                Log.d(tag, "Результат регистрации получен: $result")
                // Обновляем UI State с результатом и выключаем загрузку
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    registrationResult = result
                )
                // Если была ошибка, также можем использовать errorEvent для Snackbar/Toast
                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        errorEvent = result.message ?: "Неизвестная ошибка регистрации"
                    )
                }
            }
        }
    }

    // Функция для сброса события ошибки после его обработки в UI
    fun errorEventHandled() {
        _uiState.value = _uiState.value.copy(errorEvent = null)
    }
}