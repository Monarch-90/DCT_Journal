package com.dct_journal.data.repository

import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse

interface AuthRepository {
    suspend fun authenticateUser(
        androidId: String,
        barcode: String,
        iv: String,
    ): AuthResponse

    suspend fun registerDevice(request: RegisterRequest): RegisterResponse
}