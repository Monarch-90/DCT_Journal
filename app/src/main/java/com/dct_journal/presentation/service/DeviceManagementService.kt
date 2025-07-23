package com.dct_journal.presentation.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dct_journal.BuildConfig
import com.dct_journal.Constants
import com.dct_journal.R
import com.dct_journal.data.network.SocketIoManager
import com.dct_journal.data.network.model.StatusUpdateRequest
import com.dct_journal.data.repository.DeviceRepository
import com.dct_journal.domain.model.ServerCommand
import com.dct_journal.util.ApkDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DeviceManagementService : Service() {

    private val tag = "DeviceMgmtService"

    private val socketManager: SocketIoManager by inject()
    private val deviceRepository: DeviceRepository by inject()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var apkDownloader: ApkDownloader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "onCreate: Сервис создается...")

        // Инициализируем загрузчик здесь, когда контекст уже доступен
        apkDownloader = ApkDownloader(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand: Сервис запускается...")
        startForeground(Constants.NOTIFICATION_ID, createNotification())

        val androidId = getAndroidId()
        if (androidId.isNotBlank() && !androidId.startsWith("Ошибка")) {
            socketManager.connect(androidId)
            observeServerCommands()
        } else {
            Log.e(
                tag,
                "Критическая ошибка: не удалось получить Android ID. Сервис не сможет работать корректно."
            )
            stopSelf()
        }
        return START_STICKY
    }

    private fun observeServerCommands() {
        socketManager.observeServerCommands()
            .onEach { command ->
                Log.d(tag, "Получена команда от сервера: $command")
                when (command) {
                    is ServerCommand.GetStatus -> handleGetStatusCommand()
                    is ServerCommand.UpdateApp -> handleUpdateAppCommand(command.apkUrl)
                    is ServerCommand.Unknown -> Log.w(tag, "Получена неизвестная команда.")
                }
            }
            .catch { error ->
                Log.e(tag, "Ошибка в потоке команд сокета", error)
            }
            .launchIn(serviceScope)
    }

    private fun handleGetStatusCommand() {
        serviceScope.launch {
            val statusRequest = StatusUpdateRequest(
                deviceId = getAndroidId(),
                androidVersion = Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME,
                wmsVersion = getWmsAppVersion(),
                batteryLevel = getBatteryLevel()
            )
            deviceRepository.sendStatus(statusRequest)
        }
    }

    private fun handleUpdateAppCommand(apkUrl: String) {
        Log.i(tag, "ТРЕБУЕТСЯ РЕАЛИЗАЦИЯ: Начало обновления приложения по URL: $apkUrl")
        apkDownloader?.downloadApk(apkUrl)
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Управление ТСД",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый сервис для связи с сервером"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Система Учета")
            .setContentText("ТСД подключен к серверу")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(tag, "onDestroy: Сервис останавливается...")
        serviceJob.cancel()
        socketManager.disconnect()
    }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        try {
            return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при получении Android ID", e)
            return "Ошибка получения ID"
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getWmsAppVersion(): String {
        return try {
            val wmsPackageInfo = packageManager.getPackageInfo(Constants.WMS_APP_PACKAGE_NAME, 0)
            // ИСПРАВЛЕНО: Добавляем обработку возможного null с помощью Элвис-оператора
            wmsPackageInfo.versionName ?: "not specified"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(tag, "WMS-приложение (${Constants.WMS_APP_PACKAGE_NAME}) не найдено.")
            "n/a"
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при получении версии WMS", e)
            "error"
        }
    }
}