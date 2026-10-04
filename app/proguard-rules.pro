# Keep CameraX / ML Kit / Hilt
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class dagger.hilt.** { *; }
-keep class com.novacamera.** { *; }
# Tink (security-crypto) references errorprone annotations only at compile time.
-dontwarn com.google.errorprone.**
