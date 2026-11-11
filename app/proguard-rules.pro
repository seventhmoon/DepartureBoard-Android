# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/fung/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep @JsonClass names so Moshi's bundled conditional rules resolve <1>JsonAdapter via Class.forName
-keepnames @com.squareup.moshi.JsonClass class *

# Keep Glance ActionCallback no-arg constructors (instantiated reflectively by ActionCallbackBroadcastReceiver)
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Keep Dynamic Feature Module AI engine instantiated reflectively by DynamicAiAssistantProvider
-keep class com.androidfung.departureboard.features.ai.DynamicLmEngineImpl {
    public <init>(android.content.Context);
}

# Keep Dynamic Feature Module string title in base table
-keepclassmembers class com.androidfung.departureboard.R$string {
    public static final int title_ai_assistant;
}


