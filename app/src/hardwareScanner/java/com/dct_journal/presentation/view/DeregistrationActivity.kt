package com.dct_journal.presentation.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.Constants
import com.dct_journal.databinding.ActivityDeregistrationBinding
// import com.dct_journal.domain.model.DeregistrationUiState // Если вы используете этот тип в collect
import com.dct_journal.presentation.view_model.DeregistrationViewModel
import com.dct_journal.util.SharedPreferencesManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DeregistrationActivity : AppCompatActivity() {

    private var _binding: ActivityDeregistrationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeregistrationViewModel by viewModel()
    private val sharedPreferencesManager: SharedPreferencesManager by inject()

    private val tag = "DeregActivityHardware"

    private var androidIdForDereg: String? = null
    private var userLoginForDisplay: String? = null
    private var orderNumberForDisplay: String? = null

    private var lastScanTime: Long = 0
    private val scanDelay = 1500L

    private var scanReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.i(tag, "onCreate: Начало")
            super.onCreate(savedInstanceState) // Потенциальная точка сбоя №1 (тема, ресурсы)
            Log.i(tag, "onCreate: super.onCreate() вызван")

            // Инициализация биндинга
            try {
                _binding = ActivityDeregistrationBinding.inflate(layoutInflater) // Потенциальная точка сбоя №2 (layout inflation)
                setContentView(binding.root)
                Log.i(tag, "onCreate: View инициализирован")
            } catch (e: Exception) {
                Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: Не удалось инициализировать ViewBinding: ${e.message}", e)
                Toast.makeText(this, "Критическая ошибка UI: ${e.message}", Toast.LENGTH_LONG).show()
                finishAffinity() // Закрываем все приложение, так как UI не работает
                return
            }

            // Загрузка параметров с защитой от ошибок SharedPreferences
            try {
                userLoginForDisplay = intent.getStringExtra(Constants.EXTRA_USER_LOGIN_DISPLAY)
                    ?: sharedPreferencesManager.getUserBarcodeToDeregister()
                androidIdForDereg = intent.getStringExtra(Constants.EXTRA_ANDROID_ID)
                    ?: sharedPreferencesManager.getAndroidIdForDereg()
                orderNumberForDisplay = intent.getStringExtra(Constants.EXTRA_ORDER_NUMBER_DISPLAY)
                    ?: sharedPreferencesManager.getOrderNumberForDereg()
                Log.d(tag, "Загружены параметры: User='$userLoginForDisplay', AndroidID='$androidIdForDereg', OrderNo='$orderNumberForDisplay'")
            } catch (e: Exception) {
                Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: Исключение при доступе к SharedPreferences в onCreate: ${e.message}", e)
                Toast.makeText(this, "Ошибка данных сессии (SP)", Toast.LENGTH_LONG).show()
                try { sharedPreferencesManager.clearDeregistrationState() } catch (_: Exception) { /* Игнорируем ошибку очистки */ }
                finish()
                return
            }

            // Предусловие №1: androidIdForDereg должен быть не null
            if (androidIdForDereg == null) {
                Log.e(tag, "Критическая ошибка: androidIdForDereg is null. Завершение Activity.")
                Toast.makeText(this, "Ошибка: ID ТСД не определен.", Toast.LENGTH_LONG).show()
                try { sharedPreferencesManager.clearDeregistrationState() } catch (_: Exception) { /* Игнорируем ошибку очистки */ }
                finish()
                return
            }

            // Предусловие №2: Режим дерегистрации должен быть активен в SP (с защитой)
            var isDeregModeCurrentlyActive = false
            try {
                isDeregModeCurrentlyActive = sharedPreferencesManager.isDeregistrationModeActive()
            } catch (e: Exception) {
                Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: Исключение при чтении isDeregistrationModeActive: ${e.message}", e)
                Toast.makeText(this, "Ошибка проверки режима (SP)", Toast.LENGTH_LONG).show()
                try { sharedPreferencesManager.clearDeregistrationState() } catch (_: Exception) { /* Игнорируем ошибку очистки */ }
                finish()
                return
            }

            if (!isDeregModeCurrentlyActive) {
                Log.w(tag, "onCreate: Режим дерегистрации в SP НЕ активен, хотя Activity запущена. Немедленное закрытие.")
                finish()
                return
            }

            // Если предусловия пройденy:
            val displayIdentifier = orderNumberForDisplay ?: androidIdForDereg

            try {
                viewModel.loadInitialInfo(androidIdForDereg, userLoginForDisplay, displayIdentifier)
            } catch (e: Exception) {
                Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: Исключение при вызове viewModel.loadInitialInfo: ${e.message}", e)
                Toast.makeText(this, "Ошибка инициализации данных окна", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            try {
                binding.tvDeregStatusMessage.text = "Для дерегистрации,\nотсканируйте свой бейдж,\nили ШК Администратора"
            } catch (e: Exception) {
                Log.e(tag, "ОШИБКА: Исключение при установке начального текста: ${e.message}", e)
                // Можно продолжить, если это не критично, или завершить Activity
            }

            // Эти методы также могут выбросить исключения, если есть проблемы с DI или другими компонентами
            try {
                initializeHardwareScanner() // Ваш метод для инициализации BroadcastReceiver
                observeViewModelState()     // Ваш метод для подписки на ViewModel (или observeViewModel, как он у вас называется)

                // Добавляем обработчик нажатия "назад" прямо здесь (как было в вашем коде)
                onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        Toast.makeText(this@DeregistrationActivity, "Для выхода отсканируйте ШК или ШК администратора", Toast.LENGTH_LONG).show()
                    }
                })

            } catch (e: Exception) {
                Log.e(tag, "КРИТИЧЕСКАЯ ОШИБКА: Исключение при настройке сканера/ViewModel/BackPressed: ${e.message}", e)
                Toast.makeText(this, "Ошибка настройки компонентов окна", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            Log.i(tag, "onCreate: Завершение")

        } catch (e: Exception) {
            // Самый внешний обработчик для любых других необработанных исключений в onCreate
            Log.e(tag, "КРИТИЧЕСКАЯ НЕОБРАБОТАННАЯ ОШИБКА В onCreate: ${e.message}", e)
            Toast.makeText(this, "Критическая ошибка запуска окна дерегистрации.", Toast.LENGTH_LONG).show()
            try {
                // Попытка очистить состояние, чтобы не зациклиться.
                // Проверка isInitialized здесь была удалена как некорректная для by inject()
                sharedPreferencesManager.clearDeregistrationState()
            } catch (clearException: Exception) {
                // Логируем ошибку очистки, но продолжаем, так как мы уже в критическом состоянии
                Log.e(tag, "Ошибка при попытке аварийной очистки SP: ${clearException.message}", clearException)
            }

            if (!isFinishing) {
                // Пытаемся закрыть всё приложение, чтобы избежать плохого состояния или бесконечных перезапусков
                finishAffinity()
            }
        }
    }

    private fun initializeHardwareScanner() { // Или initializeScanReceiver
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
                                Log.e(tag, "Error converting Point Mobile byte array to string for dereg", e)
                                scannedData = null
                            }
                        }
                    }

                    if (action == Constants.DATAWEDGE_SCAN_ACTION || action == Constants.POINT_MOBILE_SCAN_ACTION) {
                        Log.i(tag, "Scan received for deregistration from $source. Action: $action. Raw data (if byte[]): ${if(intent?.getByteArrayExtra(Constants.POINT_MOBILE_DATA_KEY) != null && source == "Point Mobile") intent.getByteArrayExtra(Constants.POINT_MOBILE_DATA_KEY).contentToString() else "N/A"}")

                        val currentTime = System.currentTimeMillis()
                        if (viewModel.uiState.value.isLoading || currentTime - lastScanTime < scanDelay) {
                            Log.d(tag, "Скан проигнорирован ($source): isLoading=${viewModel.uiState.value.isLoading}, timeGuard=${currentTime - lastScanTime < scanDelay}")
                            return
                        }
                        lastScanTime = currentTime

                        if (!scannedData.isNullOrEmpty()) {
                            Log.i(tag, "Отсканирован и обработан ШК для дерегистрации ($source): $scannedData") // Изменено сообщение

                            if (scannedData == Constants.SECRET_BARCODE) {
                                Log.i(tag, "Обнаружен секретный ШК ($source: ${Constants.SECRET_BARCODE}). Переход на RegistrationActivity.")
                                // Убедитесь, что у вас есть метод navigateToRegistration() или замените на прямой вызов
                                val registrationIntent = Intent(this@DeregistrationActivity, RegistrationActivity::class.java)
                                startActivity(registrationIntent)
                                return
                            }

                            binding.tvDeregStatusMessage.text = ""
                            viewModel.attemptDeregistration(scannedData)
                        } else {
                            Log.w(tag, "Получен пустой или null ШК от $source для дерегистрации (после обработки). Action: $action")
                        }
                    }
                }
            }
            Log.d(tag, "Scan BroadcastReceiver (multi-device with byte[] handling) initialized for DeregistrationActivity.")
        }
    }


    // Переименовал для консистентности с предыдущими версиями, где была такая функция
    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(tag, "Dereg UI State: $state")
                    binding.tvDeregistrationInfo.text = state.infoMessage
                    binding.progressBarDereg.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    state.statusMessage?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        Log.d(tag, "Status message: $it")
                        viewModel.statusMessageShown()
                    }

                    if (state.isDeregistrationSuccessful) {
                        Log.i(tag, "Дерегистрация успешна. Запуск MainActivity и завершение текущей Activity.")
                        Toast.makeText(this@DeregistrationActivity, "Дерегистрация успешна!", Toast.LENGTH_SHORT).show()

                        val mainActivityIntent = Intent(this@DeregistrationActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            // Флаг EXTRA_JUST_DEREGISTERED здесь не нужен, если MainActivity.onResume()
                            // будет строго следовать логике камерной версии (которая не использует этот флаг).
                        }
                        startActivity(mainActivityIntent)
                        finish()
                    }
                }
            }
        }
    }

    private fun registerScanReceiver() {
        if (scanReceiver == null) {
            initializeHardwareScanner()
            if (scanReceiver == null) {
                binding.tvDeregStatusMessage.text = "Ошибка инициализации сканера!"
                Log.e(tag, "scanReceiver is null after re-init, cannot register.")
                return
            }
        }
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter()
                filter.addAction(Constants.DATAWEDGE_SCAN_ACTION)
                filter.addAction(Constants.POINT_MOBILE_SCAN_ACTION)
                ContextCompat.registerReceiver(this, scanReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
                isReceiverRegistered = true
                Log.i(tag, "Scan BroadcastReceiver registered for Zebra & Point Mobile actions in Deregistration.")
            } catch (e: Exception) {
                Log.e(tag, "Error registering Scan BR: ${e.message}", e)
                binding.tvDeregStatusMessage.text = "Ошибка регистрации сканера!"
            }
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
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
        registerScanReceiver()
        Log.d(tag, "onResume: Аппаратный сканер активирован (ScanReceiver зарегистрирован).")

        // Логика onResume из вашей аппаратной DeregistrationActivity, она соответствует логике камерной
        if (!sharedPreferencesManager.isDeregistrationModeActive() && !viewModel.uiState.value.isDeregistrationSuccessful) {
            if (androidIdForDereg != null) {
                Log.w(tag, "onResume: Режим дерегистрации более не активен в SP, но Activity еще существует. Закрытие.")
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterScanReceiver() // Убедитесь, что это вызывается до super.onPause(), если есть вероятность, что super.onPause() может что-то сломать
        Log.d(tag, "onPause: Аппаратный сканер деактивирован (ScanReceiver дерегистрирован).")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "onDestroy")
        _binding = null
    }
}