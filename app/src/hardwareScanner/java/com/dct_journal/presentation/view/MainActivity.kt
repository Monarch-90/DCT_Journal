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

    private val tag = "MainActivityHardware"

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

        Log.d(tag, "onCreate: Hardware Scanner Flavor")

        // Проверка разрешений
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
//        }

        initializeScanReceiver() // инициализируем BroadcastReceiver
        observeViewModel() // подписка на ViewModel
    }

    // Инициализация BroadcastReceiver для приема данных сканирования
    private fun initializeScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == Constants.DATAWEDGE_SCAN_ACTION) {
                    binding.tvScanResult.text = "Получен Intent: ${intent.action}"

                    val scannedData = intent.getStringExtra(Constants.DATAWEDGE_DATA_STRING_KEY)

                    if (!scannedData.isNullOrEmpty()) {
                        binding.tvScanResult.text = "Скан: $scannedData"

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

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.authResult.collectLatest { response ->
                    response?.let { authResponse ->
                        Log.d(
                            tag,
                            "Получен ответ: Success=${authResponse.success}, Message='${authResponse.message}'"
                        )
                        binding.tvScanResult.text = authResponse.message

                        if (authResponse.success) {
                            Log.i(
                                tag,
                                "Успешная аутентификация. Запуск ВМС (${Constants.WMS_APP_PACKAGE_NAME}) через ${Constants.WMS_APP_LAUNCH_DELAY} мс."
                            )

                            launch {
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

    // Обработка данных сканирования
    private fun processScanData(scannedText: String) {
        Log.d(tag, "Получен результат от аппаратного сканера: $scannedText")

        if (scannedText == Constants.SECRET_BARCODE) {
            Log.i(tag, "Обнаружен секретный код! Запуск RegistrationActivity.")

            val intent = Intent(this@MainActivity, RegistrationActivity::class.java)
            startActivity(intent)
            return
        } else {
            val androidId = getAndroidId()

            if (androidId != "Не удалось получить ID" && !androidId.startsWith("Ошибка")) {
                Log.d(
                    tag,
                    "Отправка данных аутентификации. AndroidId: $androidId, ШК: $scannedText"
                )

                mainViewModel.authenticate(androidId, scannedText)
            } else {
                Log.e(tag, "Не удалось получить Android ID для аутентификации.")

                binding.tvScanResult.text = "Ошибка: Не удалось получить ID устройства"
            }
        }
    }

    // Метод получения Android ID
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "Не удалось получить ID"
        } catch (e: Exception) {
            Log.e(tag, "Исключение при получении Android ID: ${e.message}", e)
            "Ошибка получения ID"
        }
    }

    // Регистрация BroadcastReceiver при возобновлении Activity
    override fun onResume() {
        super.onResume()

        registerScanReceiver() // Регистрируем приемник при активации
        Log.d(tag, "onResume completed.")
    }

    private fun registerScanReceiver() {
        if (scanReceiver != null) {
            Log.e(tag, "Ошибка: scanReceiver is null")

            initializeScanReceiver() // Попытка инициализировать снова

            if (scanReceiver == null) {
                Log.e(tag, "Failed to initialize scanReceiver. Cannot register.")
                binding.tvScanResult.text = "Ошибка: Не удалось инициализировать сканер."
                return // Не можем зарегистрировать

            }
        }

        try {
            val filter = IntentFilter(Constants.DATAWEDGE_SCAN_ACTION)
            Log.d(tag, "Registering receiver for action: ${Constants.DATAWEDGE_SCAN_ACTION}")

            ContextCompat.registerReceiver(
                this,
                scanReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(tag, "Scan BroadcastReceiver registered successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Error registering Scan BroadcastReceiver: ${e.message}", e)

            binding.tvScanResult.text = "Ошибка регистрации сканера"
        }
    }

    // Отмена регистрации BroadcastReceiver при приостановке Activity
    override fun onPause() {
        super.onPause()

        unregisterScanReceiver() // Отменяем регистрацию при уходе с экрана
        Log.d(tag, "onPause completed.")
    }

    /**
     * Отменяет регистрацию scanReceiver.
     */
    private fun unregisterScanReceiver() {
        if (scanReceiver != null) {
            try {
                unregisterReceiver(scanReceiver)
                Log.i(tag, "Scan BroadcastReceiver unregistered successfully.")
            } catch (e: IllegalArgumentException) {
                Log.w(tag, "Attempted to unregister a receiver that was not registered: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering Scan BroadcastReceiver: ${e.message}", e)
            }
        } else {
            Log.w(tag, "scanReceiver was null during unregistration attempt.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}