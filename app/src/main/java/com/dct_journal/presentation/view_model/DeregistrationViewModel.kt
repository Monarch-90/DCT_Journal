package com.dct_journal.presentation.view_model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dct_journal.domain.model.DeregistrationUiState
import com.dct_journal.domain.usecase.DeregisterUseCase
import com.dct_journal.domain.model.DeregistrationResult
import com.dct_journal.util.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeregistrationViewModel(
    private val deregisterUseCase: DeregisterUseCase,
    private val sharedPreferencesManager: SharedPreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeregistrationUiState())
    val uiState: StateFlow<DeregistrationUiState> = _uiState.asStateFlow()

    private val tag = "DeregVM"

    private var androidIdToDeregister: String? = null

    fun loadInitialInfo(
        androidId: String?,
        userLoginForDisplay: String?,
        displayableDeviceIdentifier: String?,
    ) {
        androidIdToDeregister = androidId

        if (androidId == null) { // userLoginForDisplay может быть неизвестен, если это админская дерегистрация
            _uiState.value = DeregistrationUiState(
                infoMessage = "Ошибка: ID ТСД не определен.",
                statusMessage = "Перезапустите процесс входа."
            )
            Log.e(tag, "AndroidID не передан для дерегистрации.")
            return
        }

        val displayLogin =
            if (!userLoginForDisplay.isNullOrEmpty()) "$userLoginForDisplay " else ""

        val idToShow = displayableDeviceIdentifier ?: androidId

        _uiState.value = DeregistrationUiState(
            infoMessage = "Зарегистрирован: ${displayLogin}\nТСД: $idToShow"
        )
    }

    fun attemptDeregistration(scannedBarcode: String) {
        val currentAndroidId = androidIdToDeregister
        if (currentAndroidId == null) {
            Log.e(tag, "Попытка дерегистрации без androidId.")
            _uiState.value =
                _uiState.value.copy(statusMessage = "Внутренняя ошибка: ID устройства не найден.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            statusMessage = null,
            isDeregistrationSuccessful = false
        )
        Log.d(
            tag,
            "Попытка дерегистрации ТСД: $currentAndroidId, сканированный ШК: $scannedBarcode"
        )

        viewModelScope.launch {
            deregisterUseCase(currentAndroidId, scannedBarcode).collect { result ->
                when (result) {
                    is DeregistrationResult.Success -> {
                        Log.i(tag, "Дерегистрация успешна: ${result.message}")
                        // Очищаем состояние из SharedPreferences
                        sharedPreferencesManager.clearDeregistrationState()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = result.message,
                            isDeregistrationSuccessful = true // Устанавливаем флаг для Activity
                        )
                    }

                    is DeregistrationResult.Failure -> {
                        Log.w(tag, "Ошибка дерегистрации: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = result.message,
                            isDeregistrationSuccessful = false
                        )
                    }
                }
            }
        }
    }

    // Для сброса сообщения об ошибке после его показа
    fun statusMessageShown() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}