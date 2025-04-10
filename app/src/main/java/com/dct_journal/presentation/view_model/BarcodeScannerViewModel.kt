//package com.dct_journal.presentation.view_model
//
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import com.dct_journal.data.repository.BarcodeRepository
//
//class BarcodeScannerViewModel(private val repository: BarcodeRepository) : ViewModel() {
//
//    fun onBarcodeScanned(barcode: String) {
//        if (repository.canProcessBarcode()) {
//            processBarcode(barcode)
//        } else {
//            Log.d("BarcodeScanner", "Сканирование игнорировано из-за задержки")
//        }
//    }
//
//    private fun processBarcode(barcode: String) {
//        Log.d("BarcodeScanner", "Распознан штрихкод: $barcode")
//    }
//}