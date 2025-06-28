package com.dct_journal.presentation.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    private val tag = "MainActivityCamera" // Или ваш соответствующий тег

    private var barcodeView: DecoratedBarcodeView? = null
    private var lastScanTime: Long = 0
    private val scanDelay = 1500L // мс

    private var wmsLaunchedThisSession: Boolean = false
    private var navigationToDeregJob: Job? = null // Для управления корутиной навигации

    private var deregistrationResultLauncher: ActivityResultLauncher<Intent>? = null

    // Запрос разрешения на камеру (примерная реализация, адаптируйте под свой код)
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(tag, "Camera permission GRANTED.")
                initializeBarcodeScanner() // Инициализация после получения разрешения
            } else {
                Log.w(tag, "Camera permission DENIED.")
                binding.tvScanResult.text = "Ошибка: Необходимо разрешение на камеру!"
                // Обработайте отказ в разрешении (например, покажите диалог или закройте функционал)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(tag, "onCreate: Начало")
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(tag, "onCreate: View инициализирован")

        barcodeView = binding.scanBarcode // Предполагая, что ID в XML такой

        deregistrationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(tag, "Результат от DeregistrationActivity: ${result.resultCode}")
            // onResume обработает сброс wmsLaunchedThisSession и обновление UI
            // Передаем контекст и актуальное состояние SP во ViewModel
            mainViewModel.returnedFromDeregistrationOrAppResume(
                sharedPreferencesManager.isDeregistrationModeActive()
            )
        }

        setupBarcodeScannerWithPermissionCheck() // Для cameraScanner flavor
        observeViewModel()
        Log.i(tag, "onCreate: Завершение")
    }

    private fun setupBarcodeScannerWithPermissionCheck() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(tag, "Разрешение на камеру уже есть. Инициализация сканера.")
            initializeBarcodeScanner()
        } else {
            Log.d(tag, "Разрешения на камеру нет. Запрос разрешения.")
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeBarcodeScanner() {
        // Ваша логика инициализации сканера
        barcodeView?.setStatusText("Отсканируйте ШК для аутентификации")
        barcodeView?.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val currentTime = System.currentTimeMillis()
                if (mainViewModel.uiState.value.isLoading || wmsLaunchedThisSession || currentTime - lastScanTime < scanDelay) {
                    return // Игнорируем сканы, если идет загрузка, WMS запущен или слишком часто
                }
                lastScanTime = currentTime
                result?.text?.let { scannedText ->
                    if (scannedText.isNotBlank()) {
                        Log.i(tag, "Отсканирован ШК: $scannedText")

                        if (scannedText == Constants.SECRET_BARCODE) {
                            Log.i(
                                tag,
                                "Обнаружен секретный ШК (${Constants.SECRET_BARCODE}). Переход на RegistrationActivity."
                            )
                            val intent = Intent(this@MainActivity, RegistrationActivity::class.java)
                            startActivity(intent)
                            return // Важно: выходим из колбэка, чтобы не пытаться аутентифицировать секретный ШК
                        }

                        val currentDeviceId = getAndroidId() // Ваш метод получения Android ID
                        if (currentDeviceId.isNotBlank() && currentDeviceId != "Не удалось получить ID" && !currentDeviceId.startsWith(
                                "Ошибка"
                            )
                        ) {
                            mainViewModel.authenticate(currentDeviceId, scannedText)
                        } else {
                            val errorMsg =
                                "Критическая ошибка: Не удалось получить ID устройства для аутентификации!"
                            binding.tvScanResult.text = errorMsg
                            Log.e(tag, "$errorMsg Получено: '$currentDeviceId'")
                        }
                    }
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    // Ваш метод получения Android ID
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
                    binding.tvScanResult.text =
                        state.displayMessage // Обновляем текст всегда из state

                    if (state.triggerWmsLaunchAndDeregistration && !wmsLaunchedThisSession) {
                        Log.i(tag, "Состояние: Запуск WMS и подготовка к дерегистрации...")
                        wmsLaunchedThisSession = true
                        binding.tvScanResult.text = "Запуск WMS..." // Временное сообщение

                        val userLoginDisp = state.userLoginForDeregDisplay
                        val androidIdForDereg = state.androidIdForDereg // Android ID
                        val orderNumDisp = state.orderNumberForDeregDisplay // Device Identifier

                        if (androidIdForDereg != null) {
                            sharedPreferencesManager.setDeregistrationState(
                                true,
                                userLoginDisp, androidIdForDereg, orderNumDisp
                            )
                            Log.d(
                                tag,
                                "SP для дерегистрации установлены: User='$userLoginDisp', Device(AndroidID)='$androidIdForDereg', Order(DevIdentifier)='$orderNumDisp'"
                            )

                            appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                            Log.d(tag, "WMS запущен (с последующей дерегистрацией).")
                            mainViewModel.wmsHasBeenLaunched() // Сообщаем ViewModel, что WMS был запущен (но триггер на дерегистрацию он сам не сбрасывает)

                            navigationToDeregJob?.cancel() // Отменяем предыдущую задачу, если есть
                            navigationToDeregJob = lifecycleScope.launch {
                                delay(Constants.DEREGISTRATION_ACTIVITY_LAUNCH_DELAY)

                                // Дополнительная проверка, что мы все еще должны переходить
                                if (sharedPreferencesManager.isDeregistrationModeActive() && wmsLaunchedThisSession) {
                                    Log.i(tag, "Время вышло, переход на DeregistrationActivity.")
                                    goToDeregistrationScreen(
                                        userLoginDisp,
                                        androidIdForDereg,
                                        orderNumDisp
                                    )
                                    // После успешного перехода и возврата, onResume обработает сброс состояний.
                                    // Сообщаем VM, что навигация была инициирована, чтобы он сбросил свои данные для дерег.
                                    mainViewModel.deregistrationNavigationInitiated()
                                } else {
                                    Log.w(
                                        tag,
                                        "Отмена перехода на DeregistrationActivity: SP не активен или WMS сессия была сброшена."
                                    )
                                }
                            }
                        } else {
                            Log.e(
                                tag,
                                "Ошибка: androidIdForDereg is null при triggerWmsLaunchAndDeregistration."
                            )
                            binding.tvScanResult.text = "Ошибка подготовки к дерегистрации!" // Обновляем сообщение об ошибке
                            wmsLaunchedThisSession = false // Сбой, разрешаем новый скан
                        }

                    } else if (state.triggerWmsLaunchOnly && !wmsLaunchedThisSession) {
                        Log.i(tag, "Состояние: Только запуск WMS...")
                        wmsLaunchedThisSession = true
                        // binding.tvScanResult.text = state.displayMessage // Уже установлено в начале collectLatest

                        appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                        Log.d(tag, "WMS запущен (только WMS).")
                        mainViewModel.wmsHasBeenLaunched() // Сообщаем ViewModel (он сбросит триггер triggerWmsLaunchOnly)
                        // wmsLaunchedThisSession сбросится в onResume, когда вернемся из WMS
                    }
                }
            }
        }
    }

    private fun goToDeregistrationScreen(
        userLoginForDisplay: String?,
        deviceAndroidId: String?,
        orderNumberForDereg: String?,
    ) {
        val intent = Intent(this, DeregistrationActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_LOGIN_DISPLAY, userLoginForDisplay)
            putExtra(Constants.EXTRA_ANDROID_ID, deviceAndroidId) // Это Android ID
            putExtra(
                Constants.EXTRA_ORDER_NUMBER_DISPLAY,
                orderNumberForDereg
            ) // Это Device Identifier
        }
        deregistrationResultLauncher?.launch(intent)
        // finish() здесь не нужен, он вызывается в onCreate/onResume перед этим методом, если переход безусловный
    }

    override fun onResume() {
        super.onResume()
        val isDeregModeInSP = sharedPreferencesManager.isDeregistrationModeActive()
        Log.i(
            tag,
            "onResume. SP DeregMode Active: $isDeregModeInSP, wmsLaunchedThisSession до обработки: $wmsLaunchedThisSession"
        )

        navigationToDeregJob?.cancel() // Отменяем любую запланированную навигацию, т.к. мы уже в onResume

        if (isDeregModeInSP) {
            // Если SP активен, принудительно переходим и завершаем MainActivity
            val userLoginDisp = sharedPreferencesManager.getUserBarcodeToDeregister()
            val andrId = sharedPreferencesManager.getAndroidIdForDereg() // Android ID
            val orderNum =
                sharedPreferencesManager.getOrderNumberForDereg() // Device Identifier
            Log.w(
                tag,
                "onResume: SP DeregMode АКТИВЕН! Принудительный переход и ЗАВЕРШЕНИЕ MainActivity. User: $userLoginDisp, Device(AndroidID): $andrId, Order(DevIdentifier): $orderNum"
            )
            if (andrId != null) {
                goToDeregistrationScreen(userLoginDisp, andrId, orderNum)
                finish()
                return // Прерываем выполнение onResume
            } else {
                Log.e(
                    tag,
                    "onResume: SP DeregMode активен, но deviceId (Android ID) в SP null! Аварийная очистка SP."
                )
                sharedPreferencesManager.clearDeregistrationState()
                // isDeregModeInSP станет false для следующей части логики
            }
        }

        // Этот код выполнится, если SP НЕ активен (isDeregModeInSP == false ИЛИ был только что аварийно очищен)
        if (wmsLaunchedThisSession) {
            Log.d(tag, "onResume: WMS был запущен в этой сессии, SP теперь чист. Сброс состояния.")
            // Сообщаем ViewModel, что мы вернулись и SP чист
            mainViewModel.returnedFromDeregistrationOrAppResume(sharedPreferencesManager.isDeregistrationModeActive())
        }
        wmsLaunchedThisSession = false // Готовимся к новому циклу сканирования/запуска WMS
        Log.d(tag, "onResume: wmsLaunchedThisSession установлен в false.")

        // Активация сканера (для cameraScanner flavor)
        if (tag.contains("Camera")) { // Предполагаем, что это ваш способ определения flavor
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                barcodeView?.resume()
                Log.d(tag, "onResume: Camera resumed.")
            } else {
                Log.w(
                    tag,
                    "onResume: Camera permission not granted. Scanner может не возобновиться немедленно."
                )
                // Можно снова запросить разрешение, если это уместно, или показать сообщение
                // requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "onPause")
        navigationToDeregJob?.cancel() // Отменяем навигацию, если Activity уходит в фон

        if (tag.contains("Camera")) { // Для cameraScanner flavor
            barcodeView?.pause()
        } else { // Для hardwareScanner flavor
            // unregisterScanReceiver() // Ваша логика для hardware-сканера
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        navigationToDeregJob?.cancel() // На всякий случай
        Log.i(tag, "onDestroy")
    }
}