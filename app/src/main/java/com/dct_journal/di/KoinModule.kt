package com.dct_journal.di

import com.dct_journal.Constants
import com.dct_journal.data.network.ApiService
import com.dct_journal.data.network.SocketIoManager
import com.dct_journal.data.network.getUnsafeOkHttpClient
import com.dct_journal.data.repository.AuthRepository
import com.dct_journal.data.repository.AuthRepositoryImpl
import com.dct_journal.data.repository.DeviceRepository
import com.dct_journal.data.repository.DeviceRepositoryImpl
import com.dct_journal.domain.usecase.AuthenticateUserUseCase
import com.dct_journal.domain.usecase.DeregisterUseCase
import com.dct_journal.domain.usecase.RegisterDeviceUseCase
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.DeregistrationViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import com.dct_journal.presentation.view_model.RegistrationViewModel
import com.dct_journal.util.AESEncryptionUtil
import com.dct_journal.util.SharedPreferencesManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    // Представляем утилиту шифрования AES
    single { AESEncryptionUtil() }

    // Менеджер для работы с Socket.IO
    single { SocketIoManager() }

    // OkHttp клиент (бех него Retrofit не работает)
    single {
        OkHttpClient.Builder()
            .build()
    }

    // Retrofit (надстройка над OkHttp) — для реализации API-клиента и отправки данных на сервер (для тестирования - IP ноутбука)
    single {
        Retrofit.Builder()
//            .baseUrl("https://server.url/") // production url
            .baseUrl(Constants.MY_IP_ADDRESS) // local IP
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ApiService для взаимодействия с сервером
    single<ApiService> { get<Retrofit>().create(ApiService::class.java) }

    // Репозиторий авторизации пользователя
    single<AuthRepository> { AuthRepositoryImpl(get()) }

    // Репозиторий для управления устройством
    single<DeviceRepository> { DeviceRepositoryImpl(get()) }

    // Use Cases для авторизации пользователя и добавления ТСД в БД
    single { AuthenticateUserUseCase(get(), get()) }
    single { RegisterDeviceUseCase(get()) }
    single { DeregisterUseCase(get(), get()) } // Второй get() для encryptionUtil

    single { SharedPreferencesManager(androidApplication().applicationContext) }

    // ViewModel
    viewModel { MainViewModel(get(), get()) }
    viewModel { AppLauncherViewModel(get()) } // Для запуска ВМС

    // Передаем ContentResolver из контекста приложения
    viewModel { RegistrationViewModel(get(), androidApplication().contentResolver) }
    viewModel { params -> // Используем params для передачи applicationContext из Activity
        DeregistrationViewModel(
            deregisterUseCase = get(),
            sharedPreferencesManager = get()
        )
    }
}