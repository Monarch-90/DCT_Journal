package com.dct_journal.data.repository

import com.dct_journal.data.network.model.StatusUpdateRequest

// Интерфейс для репозитория, отвечающего за управление устройством
interface DeviceRepository {

    /**
     * Отправляет на сервер информацию о текущем состоянии устройства.
     * @return - true в случае успеха, false в случае ошибки сети или сервера.
     */
    suspend fun sendStatus(request: StatusUpdateRequest): Boolean
}