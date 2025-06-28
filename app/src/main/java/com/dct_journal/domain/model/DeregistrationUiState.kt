package com.dct_journal.domain.model

data class DeregistrationUiState(
    val infoMessage: String = "Загрузка информации...", // Сообщение для tvDeregistrationInfo
    val isLoading: Boolean = false,
    val statusMessage: String? = null, // Для ошибок или сообщений о статусе
    val isDeregistrationSuccessful: Boolean = false // Флаг успешной дерегистрации для навигации
)