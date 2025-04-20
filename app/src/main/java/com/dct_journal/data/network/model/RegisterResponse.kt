package com.dct_journal.data.network.model

// Ответ от сервера при регистрации
data class RegisterResponse(
    val success: Boolean,
    val message: String?,
)