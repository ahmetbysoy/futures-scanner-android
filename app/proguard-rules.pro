# Futures Scanner ProGuard rules
-keepclassmembers class com.predator.futures.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

-dontobfuscate
-keep class com.predator.futures.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# WebView
-keep class android.webkit.** { *; }
