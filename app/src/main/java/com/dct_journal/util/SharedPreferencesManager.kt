package com.dct_journal.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.dct_journal.Constants

class SharedPreferencesManager(private val context: Context) {

    private fun getPreferences(): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setDeregistrationState(
        isActive: Boolean,
        userBarcode: String?,
        androidId: String?,
        orderNumber: String?,
    ) {
        getPreferences().edit {
            putBoolean(Constants.KEY_DEREG_MODE_ACTIVE, isActive)
            if (isActive && userBarcode != null && androidId != null) {
                putString(Constants.KEY_USER_BARCODE_TO_DEREGISTER, userBarcode)
                putString(Constants.KEY_ANDROID_ID_FOR_DEREG, androidId)
                putString(Constants.KEY_ORDER_NUMBER_FOR_DEREG, orderNumber)
            } else {
                remove(Constants.KEY_USER_BARCODE_TO_DEREGISTER)
                remove(Constants.KEY_ANDROID_ID_FOR_DEREG)
                remove(Constants.KEY_ORDER_NUMBER_FOR_DEREG)
                if (!isActive) { // Если деактивируем, то и сам флаг активности убираем
                    remove(Constants.KEY_DEREG_MODE_ACTIVE)
                }
            }
        }
    }

    fun isDeregistrationModeActive(): Boolean {
        return getPreferences().getBoolean(Constants.KEY_DEREG_MODE_ACTIVE, false)
    }

    fun getUserBarcodeToDeregister(): String? {
        return getPreferences().getString(Constants.KEY_USER_BARCODE_TO_DEREGISTER, null)
    }

    fun getAndroidIdForDereg(): String? {
        return getPreferences().getString(Constants.KEY_ANDROID_ID_FOR_DEREG, null)
    }

    fun getOrderNumberForDereg(): String? {
        return getPreferences().getString(Constants.KEY_ORDER_NUMBER_FOR_DEREG, null)
    }

    fun clearDeregistrationState() {
        getPreferences().edit {
            remove(Constants.KEY_DEREG_MODE_ACTIVE)
            remove(Constants.KEY_USER_BARCODE_TO_DEREGISTER)
            remove(Constants.KEY_ANDROID_ID_FOR_DEREG)
            remove(Constants.KEY_ORDER_NUMBER_FOR_DEREG)
        }
    }
}