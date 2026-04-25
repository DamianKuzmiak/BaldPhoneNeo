# Keep original names (no a.b.c classes)
-dontobfuscate

# Keep actual filenames and line numbers in stack traces
-keepattributes SourceFile,LineNumberTable

# Remove verbose and debug logs
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
