# DeepEyeLLM ProGuard / R8 Rules
# ================================

# --- JNI Native Bridge Functions ---
# Keep all JNI entry points for llama.cpp and LiteRT native engines
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.deepeye.agent.domain.engine.LlamaCppEngine { *; }
-keep class com.deepeye.agent.core.hardware.HardwareBackendSelector { *; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- JNA (UniFFI Bindings) ---
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class com.deepeye.agent.domain.engine.PerformanceStats { *; }

# --- Retrofit / OkHttp ---
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# --- Compose ---
# R8 full mode strips Compose runtime metadata; keep stability annotations
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# --- WorkManager ---
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# --- General ---
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
