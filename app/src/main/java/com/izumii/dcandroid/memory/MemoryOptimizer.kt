package com.izumii.dcandroid.memory

import android.content.ComponentCallbacks2
import android.view.ViewGroup
import android.webkit.WebView

object MemoryOptimizer {

    // Throttles animations, requestAnimationFrame to 1 FPS, and heavy media when backgrounded
    private const val THROTTLE_ANIMATIONS_SCRIPT = """
        (function() {
            window._dcIsBackground = true;
            if (!document.getElementById('dc-bg-style')) {
                const style = document.createElement('style');
                style.id = 'dc-bg-style';
                style.innerHTML = '* { animation: none !important; transition: none !important; } canvas, img[src*=".gif"], [class*="animator"], [class*="mediaAttachments"], [class*="gif"], [class*="avatarDecoration"], [class*="profileEffects"], [class*="typing"] { display: none !important; } video { opacity: 0.01 !important; width: 1px !important; height: 1px !important; }';
                document.head.appendChild(style);
            }
            try {
                const videos = document.querySelectorAll('video');
                for (let i = 0; i < videos.length; i++) {
                    if (videos[i].srcObject) continue;
                    if (!videos[i].paused) {
                        videos[i]._dcWasPlaying = true;
                        videos[i].pause();
                    }
                }
            } catch(e) {}
            if (!window._dcOrigRAF) {
                window._dcOrigRAF = window.requestAnimationFrame;
                window.requestAnimationFrame = function(cb) {
                    if (window._dcIsBackground) {
                        return setTimeout(function() { cb(performance.now()); }, 1000);
                    }
                    return window._dcOrigRAF(cb);
                };
            }
            if (typeof window.gc === 'function') {
                try { window.gc(); } catch(e) {}
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
            try {
                const videos = document.querySelectorAll('video');
                for (let i = 0; i < videos.length; i++) {
                    if (videos[i].srcObject) continue;
                    if (videos[i]._dcWasPlaying) {
                        videos[i]._dcWasPlaying = false;
                        videos[i].play().catch(function() {});
                    }
                }
            } catch(e) {}
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
