# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools.

# Keep Room entities
-keep class com.fillercoach.data.** { *; }

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
