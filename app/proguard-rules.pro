# =============================================================================
# ProGuard / R8 rules — tuned for MAXIMUM PERFORMANCE
# =============================================================================
# R8 full mode is enabled in gradle.properties. The rules below tell R8 what
# it MUST keep (otherwise the app crashes at runtime) and let it strip /
# inline / obfuscate everything else for the smallest, fastest bytecode.

# -----------------------------------------------------------------------------
# Aggressive optimisation passes — repeat optimise pass until nothing more
# can be removed. Slightly longer build, much faster startup & inference loop.
# -----------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''

# Strip noisy logging in release builds — turns Logger.d / Log.d into no-ops
# at link time. Removes the string concatenation cost too.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class de.cyclenerd.android.llm.server.utils.Logger {
    public *** d(...);
}

# -----------------------------------------------------------------------------
# Keep LiteRT native bridge — JNI methods MUST keep their original names
# otherwise libtflite_jni.so won't find them.
# -----------------------------------------------------------------------------
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep Ktor server classes — the CIO engine uses reflection for some plugins.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Kotlin coroutines internal entry points.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.** { *; }

# Keep kotlinx.serialization — we hit the @Serializable companions reflectively
# during JSON encode/decode.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Our @Serializable data classes
-keep,includedescriptorclasses class de.cyclenerd.android.llm.server.**$$serializer { *; }
-keepclassmembers class de.cyclenerd.android.llm.server.** {
    *** Companion;
}
-keepclasseswithmembers class de.cyclenerd.android.llm.server.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose runtime — but allow R8 to strip dead composables.
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# SLF4J (used by Ktor for logging).
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Parcelable @Parcelize generated classes
-keep class * implements android.os.Parcelable { *; }
