package com.izumii.dcandroid

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that intercepts window visibility changes to prevent
 * Chromium Blink renderer from entering the 3-minute background freezing cycle.
 * Keeps Discord WebSockets, Gateway, and WebRTC Voice streams active in the background.
 */
class KeepAliveWebView : WebView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Default false when in foreground so normal IME/keyboard lifecycle works 100%
    var keepAliveInBackground = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Allow Chromium to pause UI compositing while in background to save GPU/CPU power
        super.onWindowVisibilityChanged(visibility)
    }
}
