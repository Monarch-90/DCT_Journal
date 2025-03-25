package com.dct_journal.data.network.model

data class AuthRequest(
    val androidId: String,
    val barcode: String,
    val userName: String,
)