# Signal Scoop release hardening

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Strip verbose logging in release (ScanPolicy: no scan payloads in logcat)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Room (scan history — on-device only)
-keep class com.signalsoop.app.history.db.** { *; }
-keep class com.signalsoop.app.evr.** { *; }

# 3D graph WebView bridge
-keepclassmembers class com.signalsoop.app.ui.KnowledgeGraph3DView$* {
    @android.webkit.JavascriptInterface <methods>;
}

# On-device MediaPipe LiteRT (from cil-graph android client)
-keep class com.signalsoop.app.llm.** { *; }
-dontwarn com.google.mediapipe.**
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
