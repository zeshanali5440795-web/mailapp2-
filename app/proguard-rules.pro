# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep app classes
-keep class com.ali.mailapp.** { *; }
