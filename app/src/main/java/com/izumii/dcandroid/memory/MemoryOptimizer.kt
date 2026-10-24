package com.izumii.dcandroid.memory

import android.content.ComponentCallbacks2
import android.view.ViewGroup
import android.webkit.WebView

object MemoryOptimizer {

    // Throttles heavy CSS animations when backgrounded to save CPU and battery
    private const val THROTTLE_ANIMATIONS_SCRIPT = """
        (function() {
            if (!document.getElementById('dc-bg-style')) {
                const style = document.createElement('style');
                style.id = 'dc-bg-style';
                style.innerHTML = '* { animation-duration: 0.001ms !important; transition-duration: 0.001ms !important; }';
                document.head.appendChild(style);
            }
        })();
    """

    private const val RESTORE_ANIMATIONS_SCRIPT = """
        (function() {
            const style = document.getElementById('dc-bg-style');
            if (style) style.remove();
        })();
    """

    fun onPause(webView: WebView?) {
        webView ?: return
        webView.evaluateJavascript(THROTTLE_ANIMATIONS_SCRIPT, null)
    }

    fun onResume(webView: WebView?) {
        webView ?: return
        webView.evaluateJavascript(RESTORE_ANIMATIONS_SCRIPT, null)
    }

    fun onTrimMemory(webView: WebView?, level: Int) {
        webView ?: return
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                try {
                    webView.clearCache(false)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun destroyWebView(webView: WebView?) {
        webView ?: return
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (ignored: Exception) {}
    }
}
