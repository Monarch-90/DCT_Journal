package com.dct_journal.data.network.model

data class RegistrationUiState(
    val androidId: String = "Загрузка...",
    val isLoading: Boolean = false,
    val registrationResult: RegisterResponse? = null, // Результат последнего запроса
    val errorEvent: String? = null, // Одноразовое событие ошибки
)