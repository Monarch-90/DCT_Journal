package com.dct_journal.presentation.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DownloadCompletedReceiver : BroadcastReceiver() {

    private val tag = "DownloadReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) {
            Log.e(tag, "ID загрузки не был получен в Intent.")
            return
        }

        Log.i(tag, "Получено событие DOWNLOAD_COMPLETE для ID: $downloadId")
        val downloadManager = context.getSystemService(DownloadManager::class.java)

        // Получаем URI скачанного файла
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)

        if (apkUri != null) {
            Log.d(tag, "URI для файла получен: $apkUri. Запускаем установщик...")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Очень важный флаг
            }

            // Проверяем, есть ли в системе приложение, которое может обработать наш Intent (установщик пакетов)
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(installIntent, 0)
            val isIntentSafe = activities.isNotEmpty()

            if (isIntentSafe) {
                try {
                    context.startActivity(installIntent)
                    Log.i(tag, "Установщик APK успешно запущен.")
                } catch (e: Exception) {
                    Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА при запуске установщика", e)
                }
            } else {
                Log.e(tag, "Ошибка: в системе не найдено приложение для установки APK.")
            }

        } else {
            Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: не удалось получить Uri для загруженного файла с ID: $downloadId.")
            // Добавим дополнительную диагностику
            logDownloadStatus(downloadManager, downloadId)
        }
    }

    private fun logDownloadStatus(downloadManager: DownloadManager, downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            try {
                val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val reasonIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)

                val status = cursor.getInt(statusIndex)
                val reason = cursor.getInt(reasonIndex)

                val statusText = when(status) {
                    DownloadManager.STATUS_FAILED -> "FAILED"
                    DownloadManager.STATUS_PAUSED -> "PAUSED"
                    DownloadManager.STATUS_PENDING -> "PENDING"
                    DownloadManager.STATUS_RUNNING -> "RUNNING"
                    DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL"
                    else -> "UNKNOWN"
                }
                Log.e(tag, "Диагностика загрузки: Статус=$statusText, Причина=$reason")
            } catch (e: Exception) {
                Log.e(tag, "Ошибка при чтении курсора DownloadManager", e)
            } finally {
                cursor.close()
            }
        } else {
            Log.e(tag, "Диагностика загрузки: не удалось найти запись для ID $downloadId")
        }
    }
}