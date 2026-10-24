# Proguard rules for DC-Android
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.izumii.dcandroid.KeepAliveWebView { *; }
