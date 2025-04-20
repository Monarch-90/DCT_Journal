package com.dct_journal.presentation.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.Constants
import com.dct_journal.databinding.ActivityMainBinding
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by viewModel()
    private val appLauncherViewModel: AppLauncherViewModel by viewModel()
    private var lastScanTime: Long = 0
    private val scanDelay = 1000L
    private val tag = "MainActivityCamera"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                binding.tvScanResult.text = "Permission problem"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRequiredPermissions()
        setupBarcodeScanner()
        observeViewModel()
    }

    private fun requestRequiredPermissions() {
        // Request permission for CAMERA
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupBarcodeScanner() {
        val barcodeScanner: DecoratedBarcodeView = binding.scanBarcode

        barcodeScanner.resume()

        barcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastScanTime < scanDelay) return
                lastScanTime = currentTime

                result?.text?.let { scannedText ->
                    Log.d(tag, "Распознан штрихкод: $scannedText")

                    if (scannedText == Constants.SECRET_BARCODE) {
                        Log.i(tag, "Обнаружен секретный код! Запуск RegistrationActivity")

                        val intent = Intent(this@MainActivity, RegistrationActivity::class.java)
                        startActivity(intent)
                        return
                    } else {
                        val androidId = getAndroidId()

                        if (androidId != "Не удалось получить ID" && !androidId.startsWith("Ошибка")) {
                            Log.d(
                                tag,
                                "Отправка данных аутентификации: $androidId и штрихкодом: $scannedText"
                            )
                            mainViewModel.authenticate(androidId, scannedText)
                        } else {
                            Log.e(tag, "Не удалось получить Android ID")
                            binding.tvScanResult.text = "Ошибка: Не удалось получить ID устройства"
                        }
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.authResult.collectLatest { response ->
                    response?.let {
                        Log.d(tag, "Получен результат аутентификации для UI: ${it.message}")
                        binding.tvScanResult.text = it.message
                    }
                }
            }
        }
    }

    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "Не удалось получить ID"
        } catch (e: Exception) {
            Log.e(tag, "Исключение при получении Android ID: ${e.message}", e)
            "Ошибка получения ID"
        }
    }

    // --- Управление жизненным циклом сканера (важно для камеры) ---
    override fun onResume() {
        super.onResume()
        // binding не может быть null здесь, если используется правильный lifecycle
        _binding?.scanBarcode?.resume() // Возобновляем сканер при возвращении на экран
        Log.d(tag, "onResume: сканер возобновлен")
    }

    override fun onPause() {
        super.onPause()
        _binding?.scanBarcode?.pause() // Ставим сканер на паузу при уходе с экрана
        Log.d(tag, "onPause: сканер поставлен на паузу")
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}