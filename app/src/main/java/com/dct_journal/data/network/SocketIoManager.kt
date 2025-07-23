package com.dct_journal.data.network

import android.util.Log
import com.dct_journal.Constants
import com.dct_journal.domain.model.ServerCommand
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

class SocketIoManager {

    private var socket: Socket? = null
    private val tag = "SocketIoManager"

    // URL нашего сервера. ВАЖНО: он должен совпадать с тем, что в Koin.
    private val serverUrl = Constants.MY_IP_ADDRESS

    /**
     * Инициализирует и устанавливает соединение с сервером.
     * Отправляет ID устройства при подключении.
     */
    fun connect(androidId: String) {
        // Если сокет уже существует и подключен, ничего не делаем
        if (socket?.isActive == true) {
            Log.d(tag, "Сокет уже подключен.")
            return
        }

        try {
            // Используем тот же unsafe-клиент, что и для Retrofit, чтобы доверять нашему самоподписанному сертификату
            val okHttpClient = getUnsafeOkHttpClient()
            IO.setDefaultOkHttpCallFactory(okHttpClient)
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient)

            // Настраиваем параметры подключения.
            // В `query` мы передаем ID устройства, чтобы сервер знал, кто к нему подключился.
            val options = IO.Options.builder()
                .setQuery("deviceId=$androidId")
                .build()

            Log.i(tag, "Попытка подключения к $serverUrl с deviceId: $androidId")
            socket = IO.socket(serverUrl, options)
            socket?.connect()

        } catch (e: Exception) {
            Log.e(tag, "Ошибка при создании сокета: ${e.message}", e)
        }
    }

    /**
     * Отключается от сервера и очищает ресурсы.
     */
    fun disconnect() {
        Log.i(tag, "Отключение сокета...")
        socket?.disconnect()
        socket?.off() // Удаляем всех слушателей
        socket = null
    }

    /**
     * Создает Flow, который будет эммитить команды от сервера.
     * На этот Flow подпишется наш будущий сервис.
     */
    fun observeServerCommands(): Flow<ServerCommand> = callbackFlow {
        val socketInstance = socket ?: run {
            Log.e(tag, "Попытка слушать команды, но сокет не инициализирован!")
            close(IllegalStateException("Socket not initialized"))
            return@callbackFlow
        }

        // Слушатель для команды "отправь статус"
        val statusListener = io.socket.emitter.Emitter.Listener {
            Log.i(tag, "Получена команда 'command:get_status'")
            trySend(ServerCommand.GetStatus)
        }

        // Слушатель для команды "обнови приложение"
        val updateListener = io.socket.emitter.Emitter.Listener { args ->
            Log.i(tag, "Получена команда 'command:update_app'")
            val data = args.getOrNull(0) as? JSONObject
            val url = data?.optString("apkUrl", "") ?: ""
            if (url.isNotEmpty()) {
                Log.d(tag, "URL для обновления: $url")
                trySend(ServerCommand.UpdateApp(url))
            } else {
                Log.w(tag, "Команда 'update_app' пришла без URL")
            }
        }

        // Стандартные слушатели для отладки
        socketInstance.on(Socket.EVENT_CONNECT) { Log.i(tag, "EVENT_CONNECT: Успешно подключено!") }
        socketInstance.on(Socket.EVENT_DISCONNECT) { args -> Log.w(tag, "EVENT_DISCONNECT: Отключено. Причина: ${args.getOrNull(0)}") }
        socketInstance.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e(tag, "EVENT_CONNECT_ERROR: Ошибка подключения. ${args.getOrNull(0)}") }

        // Подписываемся на наши кастомные команды
        socketInstance.on("command:get_status", statusListener)
        socketInstance.on("command:update_app", updateListener)

        // Этот блок выполнится, когда Flow будет закрыт (например, при вызове disconnect)
        awaitClose {
            Log.d(tag, "Flow команд закрывается. Отписка от событий сокета.")
            socketInstance.off("command:get_status", statusListener)
            socketInstance.off("command:update_app", updateListener)
            socketInstance.off(Socket.EVENT_CONNECT)
            socketInstance.off(Socket.EVENT_DISCONNECT)
            socketInstance.off(Socket.EVENT_CONNECT_ERROR)
        }
    }
}