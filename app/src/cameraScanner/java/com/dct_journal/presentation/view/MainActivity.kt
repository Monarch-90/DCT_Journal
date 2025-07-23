package com.dct_journal.presentation.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.Constants
import com.dct_journal.databinding.ActivityMainBinding
import com.dct_journal.presentation.model.MainScreenState
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import com.dct_journal.util.SharedPreferencesManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by viewModel()
    private val appLauncherViewModel: AppLauncherViewModel by viewModel()
    private val sharedPreferencesManager: SharedPreferencesManager by inject()

    private val tag = "MainActivityCamera"

    private var barcodeView: DecoratedBarcodeView? = null
    private var lastScanTime: Long = 0
    private val scanDelay = 1500L // мс

    private var wmsLaunchedThisSession: Boolean = false
    private var navigationToDeregJob: Job? = null // Для управления корутиной навигации

    private var deregistrationResultLauncher: ActivityResultLauncher<Intent>? = null

    // После получения разрешения на хранилище, проверяем разрешение на установку
    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(tag, "Разрешение на хранилище ПОЛУЧЕНО.")
                // Запускаем проверку следующего разрешения
                checkAndRequestInstallPermission()
            } else {
                Log.w(tag, "Разрешение на хранилище ОТКЛОНЕНО.")
                Toast.makeText(this, "Без разрешения на доступ к хранилищу обновления не будут скачиваться.", Toast.LENGTH_LONG).show()
            }
        }

    // Начинаем с запроса на камеру
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(tag, "Camera permission GRANTED.")
                initializeBarcodeScanner()
                // Шаг 2: После получения разрешения на камеру, запрашиваем разрешение на хранилище
                checkAndRequestStoragePermission()
            } else {
                Log.w(tag, "Camera permission DENIED.")
                binding.tvScanResult.text = "Ошибка: Необходимо разрешение на камеру!"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(tag, "onCreate: Начало")
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(tag, "onCreate: View инициализирован")

        barcodeView = binding.scanBarcode

        deregistrationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(tag, "Результат от DeregistrationActivity: ${result.resultCode}")
            mainViewModel.returnedFromDeregistrationOrAppResume(
                sharedPreferencesManager.isDeregistrationModeActive()
            )
        }

        setupBarcodeScannerWithPermissionCheck()
        observeViewModel()

        Log.i(tag, "onCreate: Завершение")
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(tag, "Разрешение на запись в хранилище уже есть.")
                    // Если разрешение уже есть, сразу переходим к следующей проверке
                    checkAndRequestInstallPermission()
                }
                else -> {
                    Log.d(tag, "Запрос разрешения на запись в хранилище...")
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            // На Android 10+ разрешение на запись не нужно, сразу проверяем установку
            checkAndRequestInstallPermission()
        }
    }

    private fun setupBarcodeScannerWithPermissionCheck() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(tag, "Разрешение на камеру уже есть. Инициализация сканера.")
            initializeBarcodeScanner()
            // Если разрешение на камеру уже есть, все равно запускаем проверку остальных разрешений
            checkAndRequestStoragePermission()
        } else {
            Log.d(tag, "Разрешения на камеру нет. Запрос разрешения.")
            // Запускаем всю цепочку, начиная с запроса на камеру
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeBarcodeScanner() {
        barcodeView?.setStatusText("Отсканируйте ШК для аутентификации")
        barcodeView?.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val currentTime = System.currentTimeMillis()
                if (mainViewModel.uiState.value.isLoading || wmsLaunchedThisSession || currentTime - lastScanTime < scanDelay) {
                    return
                }
                lastScanTime = currentTime
                result?.text?.let { scannedText ->
                    if (scannedText.isNotBlank()) {
                        Log.i(tag, "Отсканирован ШК: $scannedText")

                        if (scannedText == Constants.SECRET_BARCODE) {
                            Log.i(tag, "Обнаружен секретный ШК. Переход на RegistrationActivity.")
                            startActivity(Intent(this@MainActivity, RegistrationActivity::class.java))
                            return
                        }

                        val currentDeviceId = getAndroidId()
                        if (currentDeviceId.isNotBlank() && !currentDeviceId.startsWith("Ошибка")) {
                            mainViewModel.authenticate(currentDeviceId, scannedText)
                        } else {
                            val errorMsg = "Критическая ошибка: Не удалось получить ID устройства!"
                            binding.tvScanResult.text = errorMsg
                            Log.e(tag, "$errorMsg Получено: '$currentDeviceId'")
                        }
                    }
                }
            }
            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "Не удалось получить ID"
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при получении Android ID", e)
            "Ошибка получения ID: ${e.message}"
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collectLatest { state: MainScreenState ->
                    Log.d(tag, "UI State Updated: $state")
                    binding.tvScanResult.text = state.displayMessage

                    if (state.triggerWmsLaunchAndDeregistration && !wmsLaunchedThisSession) {
                        Log.i(tag, "Состояние: Запуск WMS и подготовка к дерегистрации...")
                        wmsLaunchedThisSession = true
                        binding.tvScanResult.text = "Запуск WMS..."

                        val userLoginDisp = state.userLoginForDeregDisplay
                        val androidIdForDereg = state.androidIdForDereg
                        val orderNumDisp = state.orderNumberForDeregDisplay

                        if (androidIdForDereg != null) {
                            sharedPreferencesManager.setDeregistrationState(true, userLoginDisp, androidIdForDereg, orderNumDisp)
                            appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                            mainViewModel.wmsHasBeenLaunched()

                            navigationToDeregJob?.cancel()
                            navigationToDeregJob = lifecycleScope.launch {
                                delay(Constants.DEREGISTRATION_ACTIVITY_LAUNCH_DELAY)
                                if (sharedPreferencesManager.isDeregistrationModeActive() && wmsLaunchedThisSession) {
                                    goToDeregistrationScreen(userLoginDisp, androidIdForDereg, orderNumDisp)
                                    mainViewModel.deregistrationNavigationInitiated()
                                }
                            }
                        } else {
                            Log.e(tag, "Ошибка: androidIdForDereg is null при triggerWmsLaunchAndDeregistration.")
                            binding.tvScanResult.text = "Ошибка подготовки к дерегистрации!"
                            wmsLaunchedThisSession = false
                        }

                    } else if (state.triggerWmsLaunchOnly && !wmsLaunchedThisSession) {
                        Log.i(tag, "Состояние: Только запуск WMS...")
                        wmsLaunchedThisSession = true
                        appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                        mainViewModel.wmsHasBeenLaunched()
                    }
                }
            }
        }
    }

    private fun goToDeregistrationScreen(userLoginForDisplay: String?, deviceAndroidId: String?, orderNumberForDereg: String?) {
        val intent = Intent(this, DeregistrationActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_LOGIN_DISPLAY, userLoginForDisplay)
            putExtra(Constants.EXTRA_ANDROID_ID, deviceAndroidId)
            putExtra(Constants.EXTRA_ORDER_NUMBER_DISPLAY, orderNumberForDereg)
        }
        deregistrationResultLauncher?.launch(intent)
    }

    private fun checkAndRequestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = packageManager.canRequestPackageInstalls()
            if (!hasPermission) {
                Log.w(tag, "Разрешение на установку пакетов отсутствует. Запрашиваем...")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Log.d(tag, "Разрешение на установку пакетов уже есть.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isDeregModeInSP = sharedPreferencesManager.isDeregistrationModeActive()
        Log.i(tag, "onResume. SP DeregMode Active: $isDeregModeInSP, wmsLaunchedThisSession до обработки: $wmsLaunchedThisSession")
        navigationToDeregJob?.cancel()
        if (isDeregModeInSP) {
            val userLoginDisp = sharedPreferencesManager.getUserBarcodeToDeregister()
            val andrId = sharedPreferencesManager.getAndroidIdForDereg()
            val orderNum = sharedPreferencesManager.getOrderNumberForDereg()
            if (andrId != null) {
                goToDeregistrationScreen(userLoginDisp, andrId, orderNum)
                finish()
                return
            } else {
                Log.e(tag, "onResume: SP DeregMode активен, но deviceId в SP null! Аварийная очистка SP.")
                sharedPreferencesManager.clearDeregistrationState()
            }
        }
        if (wmsLaunchedThisSession) {
            mainViewModel.returnedFromDeregistrationOrAppResume(sharedPreferencesManager.isDeregistrationModeActive())
        }
        wmsLaunchedThisSession = false
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView?.resume()
            Log.d(tag, "onResume: Camera resumed.")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "onPause")
        navigationToDeregJob?.cancel()
        barcodeView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        navigationToDeregJob?.cancel()
        Log.i(tag, "onDestroy")
    }
}
