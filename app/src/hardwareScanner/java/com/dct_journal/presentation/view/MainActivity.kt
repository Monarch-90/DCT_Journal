package com.dct_journal.presentation.view

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    private val tag = "MainActivityHardware"

    private var lastScanTime: Long = 0
    private val scanDelay = 1500L

    private var wmsLaunchedThisSession: Boolean = false
    private var navigationToDeregJob: Job? = null

    private var deregistrationResultLauncher: ActivityResultLauncher<Intent>? = null

    // Для аппаратного сканера (Zebra DataWedge & Point Mobile)
    private var scanReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(tag, "onCreate: Начало")
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(tag, "onCreate: View инициализирован")

        // Инициализация ActivityResultLauncher (как в камерной версии)
        deregistrationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(tag, "Результат от DeregistrationActivity: ${result.resultCode}")
            mainViewModel.returnedFromDeregistrationOrAppResume(
                sharedPreferencesManager.isDeregistrationModeActive()
            )
        }

        initializeScanReceiver() // Инициализация экземпляра BroadcastReceiver
        observeViewModel()       // Подписка на ViewModel
        Log.i(tag, "onCreate: Завершение")
    }

    private fun initializeScanReceiver() {
        if (scanReceiver == null) {
            scanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    var scannedData: String? = null
                    var source = "Unknown"

                    if (action == Constants.DATAWEDGE_SCAN_ACTION) { // Для Zebra DataWedge
                        scannedData = intent.getStringExtra(Constants.DATAWEDGE_DATA_STRING_KEY)
                        source = "Zebra"
                    } else if (action == Constants.POINT_MOBILE_SCAN_ACTION) { // Для Point Mobile
                        source = "Point Mobile"
                        val byteArray = intent.getByteArrayExtra(Constants.POINT_MOBILE_DATA_KEY) // Используем новый/правильный ключ
                        if (byteArray != null) {
                            try {
                                scannedData = String(byteArray, Charsets.UTF_8).trim() // Конвертируем byte[] в String
                            } catch (e: Exception) {
                                Log.e(tag, "Error converting Point Mobile byte array to string", e)
                                scannedData = null
                            }
                        }
                    }

                    if (action == Constants.DATAWEDGE_SCAN_ACTION || action == Constants.POINT_MOBILE_SCAN_ACTION) {
                        Log.i(tag, "Scan received from $source. Action: $action. Raw data (if byte[]): ${if(intent?.getByteArrayExtra(Constants.POINT_MOBILE_DATA_KEY) != null && source == "Point Mobile") intent.getByteArrayExtra(Constants.POINT_MOBILE_DATA_KEY).contentToString() else "N/A"}")

                        val currentTime = System.currentTimeMillis()
                        if (mainViewModel.uiState.value.isLoading || wmsLaunchedThisSession || currentTime - lastScanTime < scanDelay) {
                            Log.d(tag, "Scan received but ignored (isLoading=${mainViewModel.uiState.value.isLoading}, wmsLaunched=$wmsLaunchedThisSession, timeGuard=${currentTime - lastScanTime < scanDelay}). Source: $source")
                            return
                        }
                        lastScanTime = currentTime

                        if (!scannedData.isNullOrEmpty()) {
                            Log.i(tag, "ШК получен и обработан ($source): '$scannedData'") // Изменено сообщение
                            if (scannedData == Constants.SECRET_BARCODE) {
                                Log.i(tag, "Обнаружен секретный ШК ($source: ${Constants.SECRET_BARCODE}). Переход на RegistrationActivity.")
                                val registrationIntent = Intent(this@MainActivity, RegistrationActivity::class.java)
                                startActivity(registrationIntent)
                                return
                            }
                            val currentDeviceId = getAndroidId()
                            if (currentDeviceId.isNotBlank() && currentDeviceId != "Не удалось получить ID" && !currentDeviceId.startsWith("Ошибка")) {
                                mainViewModel.authenticate(currentDeviceId, scannedData)
                            } else {
                                val errorMsg = "Критическая ошибка: Не удалось получить ID устройства для аутентификации!"
                                binding.tvScanResult.text = errorMsg
                                Log.e(tag, "$errorMsg Получено: '$currentDeviceId'")
                            }
                        } else {
                            Log.w(tag, "Получен пустой или null ШК от $source (после обработки). Action: $action")
                        }
                    }
                }
            }
            Log.d(tag, "Scan BroadcastReceiver (multi-device with byte[] handling) initialized.")
        }
    }


    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Не удалось получить ID"
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

                        // Условие для установки SP должно быть как в камерной версии (только по androidIdForDereg)
                        if (androidIdForDereg != null) {
                            sharedPreferencesManager.setDeregistrationState(
                                true, userLoginDisp, androidIdForDereg, orderNumDisp
                            )
                            Log.d(tag, "SP для дерегистрации установлены: User='$userLoginDisp', Device(AndroidID)='$androidIdForDereg', Order(DevIdentifier)='$orderNumDisp'")

                            appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                            Log.d(tag, "WMS запущен (с последующей дерегистрацией).")
                            mainViewModel.wmsHasBeenLaunched()

                            navigationToDeregJob?.cancel()
                            navigationToDeregJob = lifecycleScope.launch {
                                delay(Constants.DEREGISTRATION_ACTIVITY_LAUNCH_DELAY)
                                if (sharedPreferencesManager.isDeregistrationModeActive() && wmsLaunchedThisSession) {
                                    Log.i(tag, "Время вышло, переход на DeregistrationActivity.")
                                    goToDeregistrationScreen(userLoginDisp, androidIdForDereg, orderNumDisp)
                                    mainViewModel.deregistrationNavigationInitiated()
                                } else {
                                    Log.w(tag, "Отмена перехода на DeregistrationActivity: SP не активен или WMS сессия была сброшена.")
                                }
                            }
                        } else {
                            Log.e(tag, "Ошибка: androidIdForDereg is null при triggerWmsLaunchAndDeregistration. UserLogin: $userLoginDisp")
                            binding.tvScanResult.text = "Ошибка подготовки к дерегистрации!"
                            wmsLaunchedThisSession = false
                        }
                    } else if (state.triggerWmsLaunchOnly && !wmsLaunchedThisSession) {
                        Log.i(tag, "Состояние: Только запуск WMS...")
                        wmsLaunchedThisSession = true
                        appLauncherViewModel.launchApp(Constants.WMS_APP_PACKAGE_NAME)
                        Log.d(tag, "WMS запущен (только WMS).")
                        mainViewModel.wmsHasBeenLaunched()
                    }
                }
            }
        }
    }

    private fun goToDeregistrationScreen(
        userLoginForDisplay: String?, deviceAndroidId: String?, orderNumberForDereg: String?,
    ) {
        val intent = Intent(this, DeregistrationActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_LOGIN_DISPLAY, userLoginForDisplay)
            putExtra(Constants.EXTRA_ANDROID_ID, deviceAndroidId)
            putExtra(Constants.EXTRA_ORDER_NUMBER_DISPLAY, orderNumberForDereg)
        }
        deregistrationResultLauncher?.launch(intent) // Используем lateinit поле
    }

    private fun registerScanReceiver() {
        if (scanReceiver == null) {
            initializeScanReceiver()
            if (scanReceiver == null) {
                binding.tvScanResult.text = "Критическая ошибка: Сканер не инициализирован!"
                Log.e(tag, "registerScanReceiver: Повторная инициализация не удалась.")
                return
            }
        }
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter()
                filter.addAction(Constants.DATAWEDGE_SCAN_ACTION)      // Для Zebra
                filter.addAction(Constants.POINT_MOBILE_SCAN_ACTION) // Для Point Mobile

                ContextCompat.registerReceiver(
                    this, scanReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
                Log.i(tag, "Scan BroadcastReceiver registered for Zebra & Point Mobile actions.")
            } catch (e: Exception) {
                Log.e(tag, "Error registering Scan BR: ${e.message}", e)
                binding.tvScanResult.text = "Ошибка регистрации сканера!"
            }
        } else {
            Log.d(tag, "registerScanReceiver: Receiver уже зарегистрирован.")
        }
    }

    private fun unregisterScanReceiver() {
        if (scanReceiver != null && isReceiverRegistered) {
            try {
                unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
                Log.i(tag, "Scan BroadcastReceiver unregistered.")
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering scanReceiver: ${e.message}")
            }
        } else {
            Log.d(tag, "unregisterScanReceiver: Receiver не был зарегистрирован или null.")
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
            Log.w(tag, "onResume: SP DeregMode АКТИВЕН! Принудительный переход на DeregistrationActivity. User: $userLoginDisp, Device(AndroidID): $andrId, Order(DevIdentifier): $orderNum")

            if (andrId != null) {
                goToDeregistrationScreen(userLoginDisp, andrId, orderNum)
                finish()
                return // Прерываем выполнение onResume
            } else {
                Log.e(tag, "onResume: SP DeregMode активен, но deviceId (Android ID) в SP null! Аварийная очистка SP.")
                sharedPreferencesManager.clearDeregistrationState()
                // onResume ПРОДОЛЖИТ выполнение ниже (как в камерной версии)
            }
        }

        // Этот код выполнится, если SP НЕ активен (isDeregModeInSP == false ИЛИ был только что аварийно очищен при andrId == null)
        if (wmsLaunchedThisSession) {
            Log.d(tag, "onResume: WMS был запущен в этой сессии. Сброс состояния.")
            mainViewModel.returnedFromDeregistrationOrAppResume(sharedPreferencesManager.isDeregistrationModeActive())
        }
        wmsLaunchedThisSession = false
        Log.d(tag, "onResume: wmsLaunchedThisSession установлен в false.")

        registerScanReceiver()
        Log.d(tag, "onResume: Аппаратный сканер активирован (ScanReceiver зарегистрирован).")
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "onPause")
        navigationToDeregJob?.cancel()
        unregisterScanReceiver()
        Log.d(tag, "onPause: Аппаратный сканер деактивирован (ScanReceiver дерегистрирован).")
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        navigationToDeregJob?.cancel()
        Log.i(tag, "onDestroy")
    }
}