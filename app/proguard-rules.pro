# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/fung/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep Moshi JSON models and annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep class com.androidfung.departureboard.data.model.** { *; }

# Keep the DataStore station DTOs. These are round-tripped through Moshi codegen
# adapters (referenced directly, so normally shrink-safe), but keeping them verbatim
# is cheap insurance against R8 renaming if serialization ever falls back to reflection.
-keep class com.androidfung.departureboard.data.datastore.** { *; }

# Keep Glance AppWidget and receiver components
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Suppress warnings for OkHttp internals
-dontwarn okhttp3.internal.Util

