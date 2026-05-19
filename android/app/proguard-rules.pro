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
