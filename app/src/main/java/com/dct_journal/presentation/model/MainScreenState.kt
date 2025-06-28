package com.dct_journal.presentation.model

import android.content.Context
import com.dct_journal.R

// Состояние UI для главного экрана
data class MainScreenState(
    val displayMessage: String = "Для регистрации,\nотcканируйте свой бейдж",
    val isLoading: Boolean = false,
    val triggerWmsLaunchAndDeregistration: Boolean = false,
    val triggerWmsLaunchOnly: Boolean = false, // Флаг для однократного запуска WMS и перехода
    val userLoginForDeregDisplay: String? = null, // Логин пользователя для передачи на экран дерегистрации
    val orderNumberForDeregDisplay: String? =null, // Порядковый номер ТСД для передачи на экран дерегистрации
    val androidIdForDereg: String? = null, // ID ТСД для передачи на экран дерегистрации
    val rawBarcodeForDereg: String? = null, // Исходный ШК для передачи
)