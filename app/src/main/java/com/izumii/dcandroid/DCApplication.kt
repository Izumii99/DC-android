package com.izumii.dcandroid

import android.app.Application
import android.content.ComponentCallbacks2
import android.webkit.WebView

class DCApplication : Application(), ComponentCallbacks2 {

    override fun onCreate() {
        super.onCreate()

        // Enable remote DevTools inspection in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }
}
