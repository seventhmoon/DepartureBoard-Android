# ProGuard / R8 rules for EkiBoard Wear OS (:wear)

# Suppress benign Guava / ListenableFuture warnings on Android
-dontwarn com.google.common.util.concurrent.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
