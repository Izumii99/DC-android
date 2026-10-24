package com.izumii.dcandroid.memory

import android.content.ComponentCallbacks2
import android.view.ViewGroup
import android.webkit.WebView

object MemoryOptimizer {

    // Throttles animations, detaches heavy visual DOM trees, halts WebRTC video decoding, and pauses media when backgrounded
    private const val THROTTLE_ANIMATIONS_SCRIPT = """
        (function() {
            window._dcIsBackground = true;
            if (!document.getElementById('dc-bg-style')) {
                const style = document.createElement('style');
                style.id = 'dc-bg-style';
                style.innerHTML = '#app-mount [class*="sidebar_"], #app-mount [class*="guilds_"], #app-mount [class*="chat_"], #app-mount [class*="membersWrap_"], #app-mount [class*="banner_"], #app-mount [class*="channelTextArea_"], [class*="animator"], [class*="animated"], [class*="avatarDecoration"], [class*="profileEffects"], [class*="typing"], img[src*=".gif"], canvas { animation: none !important; display: none !important; }';
                document.head.appendChild(style);
            }
            try {
                const videos = document.querySelectorAll('video');
                for (let i = 0; i < videos.length; i++) {
                    const stream = videos[i].srcObject;
                    if (stream && stream.getVideoTracks) {
                        const vTracks = stream.getVideoTracks();
                        for (let t = 0; t < vTracks.length; t++) {
                            vTracks[t]._dcWasEnabled = vTracks[t].enabled;
                            vTracks[t].enabled = false;
                        }
                    } else if (!videos[i].paused) {
                        videos[i]._dcWasPlaying = true;
                        videos[i].pause();
                    }
                }
            } catch(e) {}
            if (!window._dcOrigRAF) {
                window._dcOrigRAF = window.requestAnimationFrame;
                window.requestAnimationFrame = function(cb) {
                    if (window._dcIsBackground) {
                        return setTimeout(function() { cb(performance.now()); }, 10000);
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
                    const stream = videos[i].srcObject;
                    if (stream && stream.getVideoTracks) {
                        const vTracks = stream.getVideoTracks();
                        for (let t = 0; t < vTracks.length; t++) {
                            if (vTracks[t]._dcWasEnabled !== false) {
                                vTracks[t].enabled = true;
                            }
                        }
                    } else if (videos[i]._dcWasPlaying) {
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
