package com.dct_journal.presentation.view_model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dct_journal.domain.model.AuthResult
import com.dct_journal.domain.usecase.AuthenticateUserUseCase
import com.dct_journal.presentation.model.MainScreenState
import com.dct_journal.util.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MainViewModel(
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val sharedPreferencesManager: SharedPreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()


    private val tag = "MainViewModel"

    fun authenticate(currentDeviceAndroidId: String, rawScannedUserBarcode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                displayMessage = "Проверка...",
                triggerWmsLaunchAndDeregistration = false, // Сброс триггеров
                triggerWmsLaunchOnly = false
            )
            Log.d(
                tag,
                "Аутентификация: DeviceID='$currentDeviceAndroidId', RawBarcode='$rawScannedUserBarcode'"
            )

            authenticateUserUseCase(
                currentDeviceAndroidId,
                rawScannedUserBarcode
            ).collect { result ->
                Log.d(tag, "Результат от UseCase: $result")

                var finalDisplayMsg = "Неизвестный результат"
                var triggerDeregFlow = false
                var triggerWmsOnlyFlow = false
                var userLoginD: String? = null
                var deviceIdD: String? = null // Это будет Android ID
                var orderNumberD: String? = null // Это будет Device Identifier (порядковый номер)
                var rawBarcodeD: String? = null

                when (result) {
                    is AuthResult.AuthenticationSuccess -> {
                        finalDisplayMsg = "${result.userLogin} зарегистрирован"
                        triggerDeregFlow = true
                        userLoginD = result.userLogin
                        deviceIdD = result.currentAndroidId // Android ID
                        orderNumberD = result.deviceIdentifier // Порядковый номер ТСД
                        rawBarcodeD = result.rawScannedUserBarcode
                        Log.i(
                            tag,
                            "Успех (ТСД зарегистрирован): '$finalDisplayMsg'. Готовимся к WMS и дерегистрации."
                        )
                    }

                    is AuthResult.AuthenticationSuccessDeviceUnregistered -> {
                        finalDisplayMsg = "${result.userLogin} зарегистрирован"
                        triggerWmsOnlyFlow = true
                        userLoginD = result.userLogin
                        deviceIdD = result.currentDeviceAndroidId // Android ID
                        orderNumberD = null // Нет порядкового номера, ТСД не зарегистрирован
                        rawBarcodeD = result.rawScannedUserBarcode
                        Log.i(
                            tag,
                            "Успех (ТСД НЕ зарегистрирован): '$finalDisplayMsg'. Только запуск WMS."
                        )
                    }

                    is AuthResult.UserLockedElsewhere -> {
                        finalDisplayMsg = result.message
                        // остальные поля остаются false/null
                    }

                    is AuthResult.DeviceOccupied -> {
                        finalDisplayMsg = result.message
                    }

                    is AuthResult.DeviceNotRegisteredInSystem -> {
                        finalDisplayMsg = result.message
                    }

                    is AuthResult.InvalidPrefix -> {
                        finalDisplayMsg = result.message
                    }

                    is AuthResult.GenericError -> {
                        finalDisplayMsg = result.message
                    }
                }

                _uiState.value = MainScreenState(
                    displayMessage = finalDisplayMsg,
                    isLoading = false,
                    triggerWmsLaunchAndDeregistration = triggerDeregFlow,
                    triggerWmsLaunchOnly = triggerWmsOnlyFlow,
                    userLoginForDeregDisplay = userLoginD,
                    orderNumberForDeregDisplay = orderNumberD,
                    androidIdForDereg = deviceIdD, // Переименовано для ясности (раньше deviceIdForDereg)
                    rawBarcodeForDereg = rawBarcodeD
                )
            }
        }
    }

    fun wmsHasBeenLaunched() {
        if (_uiState.value.triggerWmsLaunchAndDeregistration) {
            // Если был триггер на дерегистрацию, MainActivity сама решит по нему переходить.
            // ViewModel не сбрасывает этот триггер здесь.
            Log.d(tag, "WMS запущен, ожидается переход на дерегистрацию (триггер в state активен).")
        } else if (_uiState.value.triggerWmsLaunchOnly) {
            // Если был триггер только на WMS, сбрасываем его.
            _uiState.value = _uiState.value.copy(triggerWmsLaunchOnly = false)
            Log.d(tag, "WMS запущен (только WMS), триггер сброшен.")
        }
    }

    fun deregistrationNavigationInitiated() {
        _uiState.value = _uiState.value.copy(
            triggerWmsLaunchAndDeregistration = false,
            userLoginForDeregDisplay = null,
            orderNumberForDeregDisplay = null,
            androidIdForDereg = null,
            rawBarcodeForDereg = null
        )
        Log.d(tag, "Триггер и данные для дерегистрации сброшены в ViewModel.")
    }

    fun returnedFromDeregistrationOrAppResume(isDeregModeStillActiveInSP: Boolean) {
        val newDisplayMessage: String

        if (!isDeregModeStillActiveInSP) {
            newDisplayMessage = "Для регистрации,\nотcканируйте свой бейдж"
            Log.d(
                tag,
                "returnedFromDeregistrationOrAppResume: SP не активен. Установлено 'Ожидание сканирования...'."
            )
        } else {
            val userLogin = sharedPreferencesManager.getUserBarcodeToDeregister()
            val deviceId = sharedPreferencesManager.getAndroidIdForDereg()
            val orderNum = sharedPreferencesManager.getOrderNumberForDereg()

            val idToShow = orderNum ?: deviceId
            val loginToShow = if (!userLogin.isNullOrEmpty()) "'$userLogin' " else ""

            newDisplayMessage = if (deviceId != null) {
                "Ожидание дерегистрации ТСД ${idToShow ?: ""} для $loginToShow"
            } else {
                Log.e(
                    tag,
                    "returnedFromDeregistrationOrAppResume: SP активен, но deviceId == null! Аварийная очистка SP."
                )
                sharedPreferencesManager.clearDeregistrationState()
                "Ошибка состояния дерегистрации. SP очищен."
            }
            Log.w(
                tag,
                "returnedFromDeregistrationOrAppResume: SP активен. Сообщение: '$newDisplayMessage'"
            )
        }

        _uiState.value = _uiState.value.copy(
            displayMessage = newDisplayMessage,
            isLoading = false,
            triggerWmsLaunchAndDeregistration = false,
            triggerWmsLaunchOnly = false,
            userLoginForDeregDisplay = null,
            orderNumberForDeregDisplay = null,
            androidIdForDereg = null,
            rawBarcodeForDereg = null
        )
    }
}