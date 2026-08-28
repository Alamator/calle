# Debug installs are unobfuscated. Keep MapLibre JNI names if minify is ever enabled.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
