# Default ProGuard rules for Personal Tracker
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.personaltracker.data.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
