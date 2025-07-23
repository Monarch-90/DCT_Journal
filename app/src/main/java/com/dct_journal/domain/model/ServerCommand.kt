package com.dct_journal.domain.model

/**
 * Запечатанный класс, представляющий все возможные команды,
 * которые сервер может отправить на ТСД.
 */
sealed class ServerCommand {
    // Команда для немедленной отправки своего статуса на сервер
    data object GetStatus : ServerCommand()

    // Команда для начала процесса обновления приложения
    data class UpdateApp(val apkUrl: String) : ServerCommand()

    // Представляет любую неизвестную или неподдерживаемую команду
    data object Unknown : ServerCommand()
}