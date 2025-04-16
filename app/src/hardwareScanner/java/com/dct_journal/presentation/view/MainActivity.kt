package com.dct_journal.presentation.view

import android.Manifest
import android.content.BroadcastReceiver
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
import com.dct_journal.databinding.ActivityMainBinding
import com.dct_journal.presentation.view_model.AppLauncherViewModel
import com.dct_journal.presentation.view_model.MainViewModel
import com.google.zxing.ResultPoint
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

    // Приемник для Broadcast Intent от DataWedge
    private var scanReceiver: BroadcastReceiver? = null

    // Константы для DataWedge
    companion object {
        // Действие Intent, указанное в настройках DataWedge вашего MC330M
        const val DATAWEDGE_SCAN_ACTION = "com.dxexaple.ACTION"
        // Стандартный ключ для получения данных сканирования из Intent Extras
        const val DATAWEDGE_DATA_STRING_KEY = "com.symbol.datawedge.data_string"
        // (Опционально) Ключ для получения типа считанного штрихкода
        const val DATAWEDGE_LABEL_TYPE_KEY = "com.symbol.datawedge.label_type"

        // Задержка перед запуском ВМС приложения (2 секунды)
        const val WMS_APP_LAUNCH_DELAY = 2000L
        // !!! ВАЖНО: Замените "com.your.wms.package.name" на реальный пакет вашего ВМС приложения !!!
        const val WMS_APP_PACKAGE_NAME = "com.your.wms.package.name" // ЗАМЕНИТЬ!!!

    }

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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        initializeCsanreceiver() // инициализируем BroadcastReceiver

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.authResult.collectLatest { response ->
                    response?.let {
                        Log.d("Decryption", "Дешифрованное сообщение: ${it.message}")
                        binding.tvScanResult.text = it.message
                    }
                }
            }
        }

        // Запуск ВМС
//        binding.acBtnTestAppStart.setOnClickListener {
//            appLauncherViewModel.launchApp("org.telegram.messenger")
//        }
    }

    private fun setupBarcodeScanner() {
        val barcodeScanner: DecoratedBarcodeView = binding.scanBarcode

        barcodeScanner.resume()

        barcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {

                result?.let {
                    val scannerBarcode = it.text
                    val androidID = getAndroidID()
                    Log.d("BarcodeScanner", "Распознан штрихкод: $scannerBarcode")
                    if (androidID != "unknown") {
                        Log.d(
                            "BarcodeScanner",
                            "Отправка данных с Android Id: $androidID и штрихкодом: $scannerBarcode"
                        )
                        mainViewModel.authenticate(androidID, scannerBarcode)
                    } else {
                        Log.e("BarcodeScanner", "Android ID не удалось получить")
                        binding.tvScanResult.text = "Cann't take AndroidId"
                    }
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
                // Это можно использовать для отображения точек сканирования на экране, если нужно
            }
        })
    }

    private fun getAndroidID(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}