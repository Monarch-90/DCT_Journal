package com.dct_journal.presentation.view_model

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel

class AppLauncherViewModel(private val application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()

    fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Приложение не найдено", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка запуска приложения", Toast.LENGTH_LONG).show()
        }
    }
}