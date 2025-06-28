package com.dct_journal.domain.model

sealed class AuthResult {
    // Успешный вход, ТСД зарегистрирован, WMS можно запускать
    data class AuthenticationSuccess(
        val userLogin: String,
        val deviceIdentifier: String, // Порядковый номер ТСД
        val serverTimestamp: String,
        val rawScannedUserBarcode: String, // Исходный ШК для передачи на экран дерегистрации
        val currentAndroidId: String // Android ID текущего ТСД для передачи
    ) : AuthResult()

    // Успешный вход, ТСД НЕ зарегистрирован, WMS можно запускать
    data class AuthenticationSuccessDeviceUnregistered(
        val userLogin: String,
        val deviceIdentifier: String,
        val serverTimestamp: String,
        val rawScannedUserBarcode: String,
        val currentDeviceAndroidId: String
    ) : AuthResult()

    // Ошибки, при которых WMS НЕ запускается
    data class UserLockedElsewhere(val message: String, val userLogin: String, val serverTimestamp: String) : AuthResult()
    data class DeviceOccupied(val message: String, val userLogin: String, val serverTimestamp: String) : AuthResult()
    data class DeviceNotRegisteredInSystem(val message: String, val userLogin: String, val serverTimestamp: String): AuthResult() // Если сервер решит, что WMS нельзя запускать
    data class InvalidPrefix(val message: String, val userLogin: String, val serverTimestamp: String) : AuthResult()
    data class GenericError(val message: String, val serverTimestamp: String?) : AuthResult() // Общие ошибки
}