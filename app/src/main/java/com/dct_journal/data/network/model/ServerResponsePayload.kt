package com.dct_journal.data.network.model

data class ServerResponsePayload(
    val status: String?, // "ok", "device_not_found", "user_invalid_prefix", "user_locked_elsewhere", "device_occupied", "device_not_registered", "ok_deregistered", "already_free_dereg", "permission_denied_dereg" и т.д.
    val userLogin: String?,
    val deviceIdentifier: String?, // Может быть orderNumber или Android ID
    val serverTimestamp: String?,
    val message: String?, // Основное сообщение для пользователя от сервера
    // Дополнительные поля, которые могут приходить от сервера в специфичных случаях
    val occupyingUser: String? = null,
    val lockedTsdOrder: String? = null,
)