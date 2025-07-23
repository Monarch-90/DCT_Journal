package com.dct_journal.presentation.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.Constants
import com.dct_journal.databinding.ActivityDeregistrationBinding
import com.dct_journal.presentation.view_model.DeregistrationViewModel
import com.dct_journal.util.SharedPreferencesManager
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DeregistrationActivity : AppCompatActivity() {

    private var _binding: ActivityDeregistrationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeregistrationViewModel by viewModel()
    private val sharedPreferencesManager: SharedPreferencesManager by inject()

    private val tag = "DeregActivityCamera"

    private var androidIdForDereg: String? = null
    private var userLoginForDisplay: String? = null
    private var orderNumberForDisplay: String? = null

    private var barcodeView: DecoratedBarcodeView? = null
    private var lastScanTime: Long = 0
    private val scanDelay = 1500L

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(tag, "Camera permission GRANTED for DeregistrationActivity.")
                initializeAndResumeScanner()
            } else {
                Log.w(tag, "Camera permission DENIED for DeregistrationActivity.")
                binding.tvDeregStatusMessage.text =
                    "Ошибка: Необходимо разрешение на камеру для дерегистрации!"
                binding.tvDeregistrationInfo.text =
                    "Дайте разрешение на камеру и перезапустите попытку."
                binding.progressBarDereg.visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(tag, "onCreate: САМОЕ НАЧАЛО, ДО super.onCreate()")
        super.onCreate(savedInstanceState)
        Log.e(tag, "onCreate: super.onCreate() ВЫЗВАН")

        _binding = ActivityDeregistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(tag, "onCreate")

        barcodeView = binding.barcodeScannerDereg // Предполагаем, что ID в XML такой

        userLoginForDisplay = intent.getStringExtra(Constants.EXTRA_USER_LOGIN_DISPLAY)
            ?: sharedPreferencesManager.getUserBarcodeToDeregister()
        androidIdForDereg = intent.getStringExtra(Constants.EXTRA_ANDROID_ID)
            ?: sharedPreferencesManager.getAndroidIdForDereg()
        orderNumberForDisplay = intent.getStringExtra(Constants.EXTRA_ORDER_NUMBER_DISPLAY)
            ?: sharedPreferencesManager.getOrderNumberForDereg()

        Log.d(tag, "Данные для дерегистрации: UserLogin '$userLoginForDisplay', AndroidID '$androidIdForDereg', OrderNo '$orderNumberForDisplay' (из Intent или SP)")

        if (androidIdForDereg == null) {
            Log.e(tag, "Критическая ошибка: androidIdForDereg is null. Завершение Activity.")
            Toast.makeText(
                this,
                "Ошибка: ID ТСД не определен для дерегистрации.",
                Toast.LENGTH_LONG
            ).show()
            sharedPreferencesManager.clearDeregistrationState()
            finish()
            return
        }

        if (!sharedPreferencesManager.isDeregistrationModeActive()) {
            Log.w(tag, "onCreate: Режим дерегистрации в SP НЕ активен, хотя Activity запущена. Немедленное закрытие.")
            finish()
            return
        }

        val displayIdentifier = orderNumberForDisplay ?: androidIdForDereg

        viewModel.loadInitialInfo(androidIdForDereg, userLoginForDisplay, displayIdentifier)

        setupScannerWithPermissionCheck()
        observeViewModel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@DeregistrationActivity,
                    "Для выхода отсканируйте ШК или ШК администратора",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        Log.i(tag, "onCreate завершен")
    }

    private fun setupScannerWithPermissionCheck() {
        if (barcodeView == null) {
            Log.e(tag, "setupScannerWithPermissionCheck: barcodeView is NULL! Не могу продолжить.")
            binding.tvDeregStatusMessage.text = "Ошибка компонента сканера!"
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(tag, "Разрешение на камеру уже есть. Инициализация сканера для дерегистрации.")
            initializeAndResumeScanner()
        } else {
            Log.d(tag, "Разрешения на камеру нет. Запрос разрешения для дерегистрации.")
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeAndResumeScanner() {
        barcodeView?.let { bv ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(
                    tag,
                    "initializeAndResumeScanner: Нет разрешения на камеру, сканер не запускаем."
                )
                return
            }
            bv.setStatusText("Отсканируйте ваш ШК или ШК администратора")
            bv.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    val currentTime = System.currentTimeMillis()
                    if (viewModel.uiState.value.isLoading || currentTime - lastScanTime < scanDelay) {
                        return
                    }
                    lastScanTime = currentTime
                    result?.text?.let { scannedText ->
                        if (scannedText.isNotBlank()) {
                            Log.i(tag, "Отсканирован ШК для дерегистрации: $scannedText")

                            if (scannedText == Constants.SECRET_BARCODE) {
                                Log.i(tag, "Обнаружен секретный ШК (${Constants.SECRET_BARCODE}). Переход на RegistrationActivity.")
                                val intent = Intent(this@DeregistrationActivity, RegistrationActivity::class.java)
                                startActivity(intent)
                                return // Важно: выходим из колбэка, чтобы не пытаться аутентифицировать секретный ШК
                            }

                            binding.tvDeregStatusMessage.text = ""
                            viewModel.attemptDeregistration(scannedText)
                        }
                    }
                }

                override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
            })
            bv.resume() // Возобновляем здесь после настройки
            Log.d(tag, "Scanner initialized and resumed.")
        } ?: Log.e(tag, "initializeAndResumeScanner: barcodeView is NULL!")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(tag, "Dereg UI State: $state")
                    binding.tvDeregistrationInfo.text = state.infoMessage
                    binding.progressBarDereg.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    state.statusMessage?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        Log.d(tag, "Status message: $it")
                        viewModel.statusMessageShown()
                    }

                    if (state.isDeregistrationSuccessful) {
                        Log.i(
                            tag,
                            "Дерегистрация успешна. Очистка SP, запуск MainActivity и завершение текущей Activity."
                        )
                        Toast.makeText(
                            this@DeregistrationActivity,
                            "Дерегистрация успешна!",
                            Toast.LENGTH_SHORT
                        ).show()

                        val mainActivityIntent =
                            Intent(this@DeregistrationActivity, MainActivity::class.java).apply {
                                flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        startActivity(mainActivityIntent)

                        // Завершаем текущую DeregistrationActivity
                        finish()

                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
        // Сканер возобновляется в initializeAndResumeScanner или после получения разрешения
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            barcodeView?.resume()
        }
        // Проверка, нужно ли оставаться на этом экране
        if (!sharedPreferencesManager.isDeregistrationModeActive() && !viewModel.uiState.value.isDeregistrationSuccessful) {
            // Если SP был очищен извне, а мы еще не успешно дерегистрировались, то закрываемся.
            if (androidIdForDereg != null) { // Проверяем, что мы были инициализированы
                Log.w(
                    tag,
                    "onResume: Режим дерегистрации более не активен в SP, но Activity еще существует. Закрытие."
                )
                setResult(RESULT_CANCELED) // Указываем, что дерегистрации не было
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
        Log.d(tag, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "onDestroy")
        _binding = null
    }
}