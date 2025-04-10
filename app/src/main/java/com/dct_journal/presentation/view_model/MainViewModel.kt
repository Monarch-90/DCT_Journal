package com.dct_journal.presentation.view_model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.domain.usecase.AuthenticateUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val authenticateUserUseCase: AuthenticateUserUseCase) : ViewModel() {

    private val _authResult = MutableStateFlow<AuthResponse?>(null)
    val authResult: StateFlow<AuthResponse?> = _authResult

    fun authenticate(androidId: String, barcode: String) {
        viewModelScope.launch {
            try {
                authenticateUserUseCase(androidId, barcode).collect { response ->
                    Log.d("MainViewModel", "Получен ответ от сервера: ${response.message}")
                    _authResult.value = response
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка при отправке данных: ${e.message}", e)
                _authResult.value =
                    AuthResponse(false, "Ошибка: ${e.message}", "")
            }
        }
    }
}