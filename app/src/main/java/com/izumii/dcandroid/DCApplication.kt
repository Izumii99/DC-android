package com.izumii.dcandroid

import android.app.Application
import android.os.Build
import android.webkit.WebView

class DCApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable remote DevTools inspection in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
