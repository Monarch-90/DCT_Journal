package com.dct_journal.data.network

import com.dct_journal.Constants
import com.dct_journal.data.network.model.AuthRequest
import com.dct_journal.data.network.model.AuthResponse
import com.dct_journal.data.network.model.RegisterRequest
import com.dct_journal.data.network.model.RegisterResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    // Метод для аутентификации данных
    @POST(Constants.API_SERVER_ENDPOINT_SCAN)
    suspend fun authenticateUser(@Body request: AuthRequest): AuthResponse

    // Метод для регистрации устройства (добавления устройства в БД)
    @POST(Constants.API_SERVER_ENDPOINT_ADD_DEVICE)
    suspend fun registerDevice(@Body request: RegisterRequest): RegisterResponse
}