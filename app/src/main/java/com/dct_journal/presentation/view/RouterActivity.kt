package com.dct_journal.presentation.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dct_journal.Constants
import com.dct_journal.util.SharedPreferencesManager
import org.koin.android.ext.android.inject

class RouterActivity : AppCompatActivity() {

    private val tag = "RouterActivity"

    private val sharedPreferencesManager: SharedPreferencesManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: RouterActivity started")

        // Проверяем, активен ли режим дерегистрации
        if (sharedPreferencesManager.isDeregistrationModeActive()) {
            val userLoginForDisplaySP = sharedPreferencesManager.getUserBarcodeToDeregister()
            val androidIdForDeregSP = sharedPreferencesManager.getAndroidIdForDereg()
            val orderNumberForDeregSP = sharedPreferencesManager.getOrderNumberForDereg()

            if (androidIdForDeregSP != null) {
                Log.i(tag, "Режим дерегистрации активен. Переход на DeregistrationActivity.")
                val intent = Intent(this, DeregistrationActivity::class.java).apply {
                    putExtra(Constants.EXTRA_USER_LOGIN_DISPLAY, userLoginForDisplaySP)
                    putExtra(Constants.EXTRA_ANDROID_ID, androidIdForDeregSP)
                    putExtra(Constants.EXTRA_ORDER_NUMBER_DISPLAY, orderNumberForDeregSP)
                }
                startActivity(intent)
            } else {
                Log.w(tag, "Режим дерегистрации активен в SP, но androidIdForDeregSP is null. Очистка SP и переход на MainActivity.")
                sharedPreferencesManager.clearDeregistrationState()
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            Log.i(tag, "Режим дерегистрации НЕ активен. Переход на MainActivity.")
            startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
        Log.d(tag, "RouterActivity finished")
    }
}