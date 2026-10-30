# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/fung/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep Moshi code-generated adapters and @JsonClass model constructors (for reflective adapter lookup)
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep class * extends com.squareup.moshi.JsonAdapter {
    <init>(...);
}

# Keep Glance AppWidget and receiver components (constructors needed for reflection instantiation)
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    <init>(...);
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    <init>(...);
}
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Suppress warnings for OkHttp internals
-dontwarn okhttp3.internal.Util

# Keep Dynamic Feature Module string title in base table
-keepclassmembers class com.androidfung.departureboard.R$string {
    public static final int title_ai_assistant;
}


