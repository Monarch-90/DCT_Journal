package com.dct_journal.data.network

import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("scan")
    suspend fun authenticateUser(@Body request: AuthRequest): AuthResponse
}