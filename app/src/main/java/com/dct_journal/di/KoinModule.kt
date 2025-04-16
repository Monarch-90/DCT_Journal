package com.dct_journal.di

import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.getUnsafeOkHttpClient
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.data.repository.AuthRepositoryImpl
import com.dct_journal.domain.usecase.AuthenticateUserUseCase
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import com.dct_journal.util.AESEncryptionUtil
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    // Представляем утилиту шифрования AES
    single { AESEncryptionUtil() }

    // OkHttp клиент (бех него Retrofit не работает)
    single {
        OkHttpClient.Builder()
            .build()
    }

    // Retrofit (надстройка над OkHttp) — для реализации API-клиента и отправки данных на сервер (для тестирования - IP моего ноутбука)
    single {
        Retrofit.Builder()
//            .baseUrl("https://server.url/") // production url
            .baseUrl("https://10.42.0.1:5000/") // local IP
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ApiService для взаимодействия с сервером
    single<ApiService> { get<Retrofit>().create(ApiService::class.java) }

    // Репозиторий авторизации пользователя
    single<AuthRepository> { AuthRepositoryImpl(get()) }

    // Use Case для авторизации пользователя
    single { AuthenticateUserUseCase(get(), get()) }

    // ViewModel
    viewModel { MainViewModel(get()) }
    viewModel { AppLauncherViewModel(get()) } // Для запуска ВМС
}