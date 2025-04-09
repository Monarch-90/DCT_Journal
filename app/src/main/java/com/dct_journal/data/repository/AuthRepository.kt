package com.dct_journal.data.repository

import com.dct_journal.data.network.model.AuthResponse

interface AuthRepository {
    suspend fun authenticateUser(
        androidId: String,
        barcode: String,
        iv: String,
    ): AuthResponse
}