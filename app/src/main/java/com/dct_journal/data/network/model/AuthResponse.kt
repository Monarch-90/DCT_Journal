package com.dct_journal.data.network.model

data class AuthResponse (
    val success: Boolean,
    val message: String,
    val userName: String
)