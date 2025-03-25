//package com.dct_journal.data.repository
//
//class BarcodeRepository {
//    private var lastScanTime: Long = 0
//    private val scanDelay: Long = 10 * 1000
//
//    fun canProcessBarcode(): Boolean {
//        val currentTime = System.currentTimeMillis()
//        return if (currentTime - lastScanTime >= scanDelay) {
//            lastScanTime = currentTime
//            true
//        } else {
//            false
//        }
//    }
//}