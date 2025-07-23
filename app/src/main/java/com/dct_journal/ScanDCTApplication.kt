package com.dct_journal

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.dct_journal.di.appModule
import com.dct_journal.presentation.service.DeviceManagementService
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ScanDCTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.e("MyAppBoot", "ScanDCTApplication onCreate: СТАРТ") // Используем Log.e для заметности

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Log.d("MyAppBoot", "ScanDCTApplication: setDefaultNightMode вызван")

        try {
            startKoin {
                androidContext(this@ScanDCTApplication)
                modules(appModule)
            }
            Log.d("MyAppBoot", "ScanDCTApplication: Koin успешно запущен")
        } catch (e: Exception) {
            Log.e("MyAppBoot", "ScanDCTApplication: ОШИБКА при запуске Koin!", e)
            throw e // Перебрасываем ошибку, чтобы увидеть крэш, если он здесь
        }

        // НОВЫЙ БЛОК: Запускаем наш фоновый сервис
        startDeviceManagementService()

        Log.e("MyAppBoot", "ScanDCTApplication onCreate: КОНЕЦ")
    }

    private fun startDeviceManagementService() {
        val serviceIntent = Intent(this, DeviceManagementService::class.java)
        // Для Android 8 и выше сервис нужно запускать через startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
            Log.i("MyAppBoot", "Запуск DeviceManagementService через startForegroundService()")
        } else {
            startService(serviceIntent)
            Log.i("MyAppBoot", "Запуск DeviceManagementService через startService()")
        }
    }
}