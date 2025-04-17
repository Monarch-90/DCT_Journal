package com.dct_journal.presentation.view_model

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel

class AppLauncherViewModel(private val application: Application) : AndroidViewModel(application) {

    private val tag = "AppLauncherViewModel"

    fun launchApp(packageName: String) {

        val packageManager = application.packageManager

        try {
            Log.d(tag, "Попытка получить Intent для запуска пакета: $packageName")

            val intent = packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                Log.i(tag, "Приложение найдено. Запускаем Activity для пакета: $packageName")

                application.startActivity(intent)
            } else {
                Log.w(
                    tag,
                    "Не удалось найти Intent для запуска пакета: $packageName. Приложение не установлено?"
                )

                Toast.makeText(
                    application,
                    "Приложение '$packageName' не найдено",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "ошибка при попытке запуска приложения '$packageName'", e)

            Toast.makeText(
                application,
                "Ошибка запуска приложения '$packageName'",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}