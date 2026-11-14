-keep class org.webrtc.** { *; }
-keep class com.xtvlite.tv.decoder.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
