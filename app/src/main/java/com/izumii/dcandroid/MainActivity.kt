package com.izumii.dcandroid

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.izumii.dcandroid.memory.MemoryOptimizer
import com.izumii.dcandroid.service.ConnectionKeeperService
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: FrameLayout
    private var webView: KeepAliveWebView? = null
    private var bridge: WebAppInterface? = null
    private var backPressedTime: Long = 0

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var recentCrashCount = 0
    private var lastCrashTime = 0L
    private var isAppInForeground = true
    private var latestUnreadCount = 0

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    companion object {
        const val DISCORD_APP_URL = "https://discord.com/app"
        // Desktop User-Agent prevents Discord from redirecting to mobile play store splash
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        const val DISCORD_INJECTION_SCRIPT = """
            (function() {
                // 0. Mute console chatter to stop V8 string allocations and garbage collection cycles
                try {
                    console.log = console.debug = console.info = console.warn = function() {};
                } catch(e) {}

                // 1. Web Audio Context keep-alive hook
                if (!window._dcAudioCtxHooked) {
                    window._dcAudioCtxHooked = true;
                    const orig = window.AudioContext || window.webkitAudioContext;
                    if (orig) {
                        window.AudioContext = function() {
                            const ctx = new orig();
                            window._dcAudioCtx = ctx;
                            return ctx;
                        };
                        window.AudioContext.prototype = orig.prototype;
                    }
                }

                // 2. Unconditionally overwrite HTML5 Notification polyfill
                window.Notification = function(title, options) {
                    this.title = title || 'Discord';
                    this.body = (options && options.body) || '';
                    this.onclick = null;
                    this.onclose = null;
                    if (window.AndroidBridge && typeof window.AndroidBridge.postNotification === 'function') {
                        window.AndroidBridge.postNotification(this.title, this.body);
                    }
                };
                window.Notification.prototype.close = function() {
                    if (typeof this.onclose === 'function') {
                        try { this.onclose(); } catch(e) {}
                    }
                };
                window.Notification.prototype.addEventListener = function(type, listener) {
                    if (type === 'click') this.onclick = listener;
                    if (type === 'close') this.onclose = listener;
                };
                window.Notification.prototype.removeEventListener = function(type, listener) {
                    if (type === 'click' && this.onclick === listener) this.onclick = null;
                    if (type === 'close' && this.onclose === listener) this.onclose = null;
                };
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function() { return Promise.resolve('granted'); };

                // 3. Clipboard bridge for "Copy Message / Copy Link"
                if (!navigator.clipboard) {
                    navigator.clipboard = {};
                }
                navigator.clipboard.writeText = function(text) {
                    if (window.AndroidBridge && typeof window.AndroidBridge.copyText === 'function') {
                        window.AndroidBridge.copyText(text || '');
                    }
                    return Promise.resolve();
                };

                // 4. Spoof document.hidden & visibilityState, override hasFocus for background notifications
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch(e) {}
                document.hasFocus = function() { return !window._dcIsBackground; };

                // 5. Unlock pinch-to-zoom on mobile viewport
                const vp = document.querySelector('meta[name="viewport"]');
                if (vp) {
                    vp.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
                }

                // 6. Suppress promo notices, GPU-heavy backdrop blur, and animated profile decorations
                if (!document.getElementById('dc-perf-style')) {
                    const s = document.createElement('style');
                    s.id = 'dc-perf-style';
                    s.innerHTML = `
                        [class*="notice"], [class*="downloadNotice"], [class*="mobileAppPromo"] { display: none !important; }
                        * { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; }
                        [class*="avatarDecoration"], [class*="profileEffects"] { display: none !important; }
                    `;
                    document.head.appendChild(s);
                }

                // 7. Continuous Anti-AFK activity pulse (prevents auto-disconnect from voice & streams)
                if (!window._dcAfkInterval) {
                    window._dcAfkInterval = setInterval(function() {
                        try {
                            const evt = new MouseEvent('mousemove', { bubbles: true, cancelable: true, clientX: 10, clientY: 10 });
                            window.dispatchEvent(evt);
                            document.dispatchEvent(evt);
                        } catch(e) {}
                    }, 45000);
                }

                // 8. Disable microphone hardware capture (forces pure watch/listen mode, stops VoIP Call mode)
                try {
                    if (navigator.mediaDevices) {
                        if (navigator.mediaDevices.getUserMedia) {
                            navigator.mediaDevices.getUserMedia = function(c) {
                                if (c && c.audio) {
                                    return Promise.reject(new DOMException("Microphone disabled", "NotAllowedError"));
                                }
                                return Promise.reject(new DOMException("Device not supported", "NotFoundError"));
                            };
                        }
                        if (navigator.mediaDevices.enumerateDevices) {
                            const origEnumerate = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
                            navigator.mediaDevices.enumerateDevices = function() {
                                return origEnumerate().then(function(devices) {
                                    return devices.filter(function(d) { return d.kind !== 'audioinput'; });
                                });
                            };
                        }
                    }
                    navigator.getUserMedia = undefined;
                    navigator.webkitGetUserMedia = undefined;
                } catch(e) {}
            })();
        """
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)

        // Request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setupImmersiveFullscreen()
        setupDoubleTapBackToExit()
        setupWebView()

        // Keep audio system strictly in normal media mode, never in-call VoIP mode
        (getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.mode =
            android.media.AudioManager.MODE_NORMAL

        // Keep screen awake while watching streams or in voice channels
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView?.loadUrl(DISCORD_APP_URL)
    }

    private fun setupImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupDoubleTapBackToExit() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                    return
                }

                val wv = webView
                if (wv != null && wv.canGoBack()) {
                    val currentUrl = wv.url ?: ""
                    // Only use web history back if outside Discord main app
                    if (!currentUrl.contains("discord.com/channels") && !currentUrl.contains("discord.com/app")) {
                        wv.goBack()
                        return
                    }
                }

                val now = System.currentTimeMillis()
                if (now - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = now
                    Toast.makeText(this@MainActivity, R.string.exit_prompt, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun hideCustomView() {
        val cv = customView ?: return
        rootContainer.removeView(cv)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView?.visibility = View.VISIBLE
        setupImmersiveFullscreen()
    }

    private fun openExternalChooser(url: String): Boolean {
        return try {
            val intent = if (url.startsWith("intent://", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, getString(R.string.open_with)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val v = KeepAliveWebView(this).also {
            rootContainer.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        webView = v

        val jsBridge = WebAppInterface(context = this)
        bridge = jsBridge
        v.addJavascriptInterface(jsBridge, "AndroidBridge")

        v.setBackgroundColor(Color.parseColor("#1E1F22"))
        v.isFocusable = true
        v.isFocusableInTouchMode = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(v, true)

        val settings = v.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = true
        settings.safeBrowsingEnabled = false
        settings.setGeolocationEnabled(false)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.offscreenPreRaster = false

        // Essential: Spoof desktop user agent so Discord loads the web app interface
        settings.userAgentString = DESKTOP_USER_AGENT

        // Execute script as early as possible on document start
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                v,
                DISCORD_INJECTION_SCRIPT,
                setOf("https://discord.com", "https://*.discord.com")
            )
        }

        v.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            val guessedName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            if (url.startsWith("data:")) {
                jsBridge.saveBase64File(url, guessedName, mimetype)
            } else if (url.startsWith("blob:")) {
                val js = """
                    (function() {
                        fetch('$url')
                            .then(function(r) { return r.blob(); })
                            .then(function(b) {
                                const reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.AndroidBridge && typeof window.AndroidBridge.saveBase64File === 'function') {
                                        window.AndroidBridge.saveBase64File(reader.result, '$guessedName', b.type || '$mimetype');
                                    }
                                };
                                reader.readAsDataURL(b);
                            }).catch(function() {});
                    })();
                """.trimIndent()
                v.evaluateJavascript(js, null)
            } else {
                jsBridge.downloadHttpFile(url, guessedName, mimetype)
            }
        }

        v.webChromeClient = object : WebChromeClient() {
            // Deny device hardware capture requests (microphone/camera) to prevent VoIP mode
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.deny()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val count = title?.let {
                    val match = Regex("""^\((\d+)\)""").find(it)
                    match?.groupValues?.get(1)?.toIntOrNull()
                } ?: 0
                latestUnreadCount = count
                if (!isAppInForeground) {
                    ConnectionKeeperService.updateBadgeCount(this@MainActivity, count)
                }
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = try {
                    fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                } catch (e: Exception) {
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                }

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    hideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                webView?.visibility = View.GONE
                rootContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                setupImmersiveFullscreen()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                request ?: return false
                if (!request.isForMainFrame) {
                    return false
                }

                val url = request.url.toString()
                if (url.startsWith("blob:") || url.startsWith("data:") || url.startsWith("about:") || url.startsWith("javascript:")) {
                    return false
                }

                val host = request.url.host ?: ""

                // Allow internal Discord domains to stay inside the WebView
                if (host.endsWith("discord.com") || host.endsWith("discordapp.com") || host.endsWith("discord.gg") || host.endsWith("discordapp.net")) {
                    return false
                }

                // External links (e.g. YouTube, links sent in chats) open in external browser
                openExternalChooser(url)
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.contains("/api/v9/science") ||
                    url.contains("/api/v9/metrics") ||
                    url.contains("sentry.io") ||
                    url.contains("tracking.discord.gg")
                ) {
                    return android.webkit.WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Execute script on page finished as well
                view?.evaluateJavascript(DISCORD_INJECTION_SCRIPT, null)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val now = System.currentTimeMillis()
                if (now - lastCrashTime < 30_000) {
                    recentCrashCount++
                } else {
                    recentCrashCount = 1
                }
                lastCrashTime = now

                Toast.makeText(this@MainActivity, R.string.reloading_session, Toast.LENGTH_SHORT).show()

                MemoryOptimizer.destroyWebView(webView)
                webView = null

                val shouldClearCache = recentCrashCount > 3
                if (shouldClearCache) {
                    Toast.makeText(
                        this@MainActivity,
                        "Excessive crashes detected. Resetting cache...",
                        Toast.LENGTH_LONG
                    ).show()
                    recentCrashCount = 0
                }

                setupWebView()
                if (shouldClearCache) {
                    try {
                        webView?.clearCache(true)
                    } catch (ignored: Exception) {}
                }
                webView?.loadUrl(DISCORD_APP_URL)
                return true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        webView?.keepAliveInBackground = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersiveFullscreen()
        MemoryOptimizer.onResume(webView)
        ConnectionKeeperService.onPulseListener = null
        ConnectionKeeperService.stop(this)
        ConnectionKeeperService.updateBadgeCount(this, 0)
        (getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.mode =
            android.media.AudioManager.MODE_NORMAL

        // Resume AudioContext if suspended
        webView?.evaluateJavascript(
            "try { if (window._dcAudioCtx && window._dcAudioCtx.state === 'suspended') window._dcAudioCtx.resume(); } catch(e){}",
            null
        )
    }

    override fun onPause() {
        isAppInForeground = false
        webView?.keepAliveInBackground = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
        (getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.mode =
            android.media.AudioManager.MODE_NORMAL
        try {
            CookieManager.getInstance().flush()
        } catch (ignored: Exception) {}
        MemoryOptimizer.onPause(webView)
        // Keep WebSocket Gateway, AudioContext, and Anti-AFK activity primed during background
        ConnectionKeeperService.onPulseListener = {
            webView?.post {
                webView?.evaluateJavascript(
                    "try { if (window._dcAudioCtx && window._dcAudioCtx.state === 'suspended') window._dcAudioCtx.resume(); window.dispatchEvent(new Event('mousemove')); } catch(e){}",
                    null
                )
            }
        }
        ConnectionKeeperService.start(this)
        ConnectionKeeperService.updateBadgeCount(this, latestUnreadCount)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryOptimizer.onTrimMemory(webView, level)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveFullscreen()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        setupImmersiveFullscreen()
    }

    override fun onDestroy() {
        hideCustomView()
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        ConnectionKeeperService.onPulseListener = null
        ConnectionKeeperService.stop(this)
        MemoryOptimizer.destroyWebView(webView)
        webView = null
        bridge = null
        super.onDestroy()
    }
}
