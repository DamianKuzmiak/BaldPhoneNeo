# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line contactUri information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line contactUri information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Joda-Time
-keep class org.joda.time.** { *; }
-keep interface org.joda.time.** { *;}

# Rendescript
-keepclasseswithmembernames class * {
   native <methods>;
}
-keep class android.support.v8.renderscript.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Uncomment for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule

# Crashlytics
-keep class com.crashlytics.** { *; }
-keepattributes SourceFile,LineNumberTable,*Annotation*
-keep class com.crashlytics.android.**
