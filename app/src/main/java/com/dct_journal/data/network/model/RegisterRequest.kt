package com.dct_journal.data.network.model

// Запрос на регистрацию
data class RegisterRequest(
    val androidId: String,
    val orderNumber: String, // Отправляем как строку, т.к. EditText вернет строку
)