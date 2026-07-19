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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- DocuScan: keep rules required once minify + shrinkResources are on ---

# Room uses reflection on DAOs and entities at runtime.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.example.data.database.** { *; }
-keep class com.example.data.model.** { *; }

# OpenCV native loader + JNI classes must not be stripped/renamed.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Compose / ViewModel retained-state classes (reflection in saveable).
-keep class com.example.ui.viewmodel.** { *; }

# Parcelable / model data passed across navigation / saved state.
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
