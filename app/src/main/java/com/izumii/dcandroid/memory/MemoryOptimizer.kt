package com.izumii.dcandroid.memory

import android.content.ComponentCallbacks2
import android.view.ViewGroup
import android.webkit.WebView

object MemoryOptimizer {

    // Suppresses animations and non-essential UI while keeping live streams and audio running smoothly
    private const val THROTTLE_ANIMATIONS_SCRIPT = """
        (function() {
            window._dcIsBackground = true;
            if (!document.getElementById('dc-bg-style')) {
                const style = document.createElement('style');
                style.id = 'dc-bg-style';
                style.innerHTML = '#app-mount [class*="sidebar_"], #app-mount [class*="membersWrap_"], [class*="animator"], [class*="animated"], [class*="avatarDecoration"], [class*="profileEffects"], [class*="typing"], img[src*=".gif"] { animation: none !important; }';
                document.head.appendChild(style);
            }
            if (!window._dcOrigRAF) {
                window._dcOrigRAF = window.requestAnimationFrame;
                window.requestAnimationFrame = function(cb) {
                    if (window._dcIsBackground) {
                        return setTimeout(function() { cb(performance.now()); }, 1000);
                    }
                    return window._dcOrigRAF(cb);
                };
            }
        })();
    """

    private const val RESTORE_ANIMATIONS_SCRIPT = """
        (function() {
            window._dcIsBackground = false;
            if (window._dcOrigRAF) {
                window.requestAnimationFrame = window._dcOrigRAF;
                window._dcOrigRAF = null;
            }
            const style = document.getElementById('dc-bg-style');
            if (style) style.remove();
        })();
    """

    fun onPause(webView: WebView?) {
        webView ?: return
        webView.evaluateJavascript(THROTTLE_ANIMATIONS_SCRIPT, null)
        try {
            webView.clearCache(false)
        } catch (ignored: Exception) {}
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
                    webView.evaluateJavascript("if (typeof window.gc === 'function') { try { window.gc(); } catch(e) {} }", null)
                    System.gc()
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
