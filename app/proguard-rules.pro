# WebView + Compose defaults; nothing to keep beyond what R8 already keeps.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
