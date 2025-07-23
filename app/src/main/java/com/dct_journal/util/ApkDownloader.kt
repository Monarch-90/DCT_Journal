package com.dct_journal.util

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import java.io.File

class ApkDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val tag = "ApkDownloader"

    /**
     * Начинает загрузку APK-файла.
     * @param url URL для скачивания APK.
     * @return ID загрузки в системном DownloadManager.
     */
    fun downloadApk(url: String): Long? {
        return try {
            val fileName = url.substring(url.lastIndexOf('/') + 1)

            // --- НАЧАЛО ИЗМЕНЕНИЙ ---
            // Используем стандартную публичную папку "Загрузки"
            val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destination.exists()) {
                Log.w(tag, "Старый файл обновления '${fileName}' найден и будет удален.")
                destination.delete()
            }

            Log.d(tag, "Подготовка запроса на загрузку. URL: $url")

            val request = DownloadManager.Request(url.toUri())
                .setTitle("Обновление приложения ($fileName)")
                .setDescription("Загрузка новой версии...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Указываем публичную папку для сохранения
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
                // Добавляем флаги, разрешающие скачивание по любой сети
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            // --- КОНЕЦ ИЗМЕНЕНИЙ ---

            val downloadId = downloadManager.enqueue(request)
            Log.i(tag, "Загрузка APK успешно поставлена в очередь. Download ID: $downloadId")
            downloadId
        } catch (e: Exception) {
            Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА при постановке APK в очередь на загрузку", e)
            null
        }
    }
}
