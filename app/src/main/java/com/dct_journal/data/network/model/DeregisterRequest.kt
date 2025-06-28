package com.dct_journal.data.network.model

data class DeregisterRequest(
    val androidId: String,
    val barcode: String, // Зашифрованный ШК пользователя или админа
    val iv: String,
)