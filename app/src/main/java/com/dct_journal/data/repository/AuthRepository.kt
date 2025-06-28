package com.dct_journal.data.repository

import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.network.model.DeregisterRequest
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse

interface AuthRepository {
    suspend fun authenticateUser(request: AuthRequest): AuthResponse

    suspend fun registerDevice(request: RegisterRequest): RegisterResponse

    suspend fun deregisterDevice(request: DeregisterRequest): AuthResponse
}