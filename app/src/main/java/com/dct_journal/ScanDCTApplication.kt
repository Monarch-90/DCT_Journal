package com.dct_journal

import android.app.Application
import com.dct_journal.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ScanDCTApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ScanDCTApplication)
            modules(appModule)
        }
    }
}