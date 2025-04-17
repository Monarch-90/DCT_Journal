package com.dct_journal.presentation.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.Constants
import com.dct_journal.databinding.ActivityMainBinding
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by viewModel()
    private val appLauncherViewModel: AppLauncherViewModel by viewModel()

    // Приемник для Broadcast Intent от DataWedge
    private var scanReceiver: BroadcastReceiver? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                binding.tvScanResult.text = "AndroidId problem: Permission Denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверка разрешений
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
//        }

        initializeScanReceiver() // инициализируем BroadcastReceiver

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.authResult.collectLatest { response ->
                    response?.let { authResponse ->
                        Log.d(
                            "AuthResult",
                            "Получен ответ: Success=${authResponse.success}, Message='${authResponse.message}'"
                        )
                        binding.tvScanResult.text = authResponse.message

                        if (authResponse.success) {
                            Log.i(
                                "AppLaunch",
                                "Успешная аутентификация. Запуск ВМС (${Constants.WMS_APP_PACKAGE_NAME}) через ${Constants.WMS_APP_LAUNCH_DELAY} мс."
                            )

                            lifecycleScope.launch {
                                delay(Constants.WMS_APP_LAUNCH_DELAY)
                                Log.d(
                                    "AppLaunch",
                                    "Попытка запуска приложения: $Constants.WMS_APP_PACKAGE_NAME"
                                )

                                appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                            }
                        } else {
                            Log.d(
                                "AppLaunch",
                                "Аутентификация не успешна (success = false). ВМС не запускается"
                            )
                        }
                    }
                }
            }
        }
    }

    // Инициализация BroadcastReceiver для приема данных сканирования
    private fun initializeScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == Constants.DATAWEDGE_SCAN_ACTION) {
                    val scannedData = intent.getStringExtra(Constants.DATAWEDGE_DATA_STRING_KEY)

                    if (!scannedData.isNullOrEmpty()) {
                        Log.d("DataWedgeScan", "получен ШК: $scannedData")
                        processScanData(scannedData)
                    } else {
                        Log.w("DataWedgeScan", "Получен пустой или null ШК от DataWedge")
                    }
                }
            }
        }
        Log.d("MainActivityLifecycle", "ScanReceiver инициализирован")
    }

    // Обработка данных сканирования
    private fun processScanData(scannedBarcode: String) {
        val androidId = getAndroidId()

        Log.d("ProcessScan", "Обработка ШК: $scannedBarcode")

        if (androidId != "unknown") {
            Log.d(
                "ProcessScan",
                "Отправка данных с Android Id: $androidId и ШК: $scannedBarcode"
            )
            mainViewModel.authenticate(androidId, scannedBarcode)
        } else {
            Log.e("ProcessScan", "Android ID не удалось получить. Невозможно аутентифицировать")
            binding.tvScanResult.text = "Ошибка: Не удалось получить ID устройства"
        }
    }

    // Регистрация BroadcastReceiver при возобновлении Activity
    override fun onResume() {
        super.onResume()

        if (scanReceiver != null) {
            val filter = IntentFilter(Constants.DATAWEDGE_SCAN_ACTION)

            ContextCompat.registerReceiver(
                this, // Контекст Activity
                scanReceiver, // Наш BroadcastReceiver
                filter, // Фильтр интентов
                ContextCompat.RECEIVER_EXPORTED // Флаг для приема внешних broadcast-ов
            )
            Log.d("MainActivityLifecycle", "ScanReceiver зарегистрирован с помощью ContextCompat")
        } else {
            Log.e(
                "MainActivityLifecycle",
                "Ошибка: Попытка зарегистрировать неинициализированный scanReceiver"
            )
        }

    }

    // Отмена регистрации BroadcastReceiver при приостановке Activity
    override fun onPause() {
        super.onPause()

        if (scanReceiver != null) {
            try {
                unregisterReceiver(scanReceiver)
                Log.d("MainActivityLifecycle", "ScanReceiver отменён")
            } catch (e: IllegalArgumentException) {
                // Это нормально, если ресивер по какой-то причине не был зарегистрирован (например, ошибка в onResume)
                Log.w(
                    "MainActivityLifecycle",
                    "Попытка отменить регистрацию незарегистрированного ScanReceiver: ${e.message}"
                )
            }
        }
    }

    // Метод получения Android ID
    private fun getAndroidId(): String {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Добавим лог для отладки получения ID
        Log.d("AndroidID", "Полученный Settings.Secure.ANDROID_ID: $id")
        return if (id.isNullOrBlank()) {
            Log.w("AndroidId", "Settings.Secure.ANDROID_ID пуст или null, возвращаем 'unknown'")
            "unknown"
        } else {
            id
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}