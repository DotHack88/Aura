# ============================================================
#  Aura — ProGuard / R8 Rules
# ============================================================

# ── Generali ────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable   # mantieni stacktrace leggibili

# ── Kotlin ──────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Jetpack Compose ─────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Firebase ────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Firestore — mantieni i data class per serializzazione
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keep class * {
    @com.google.firebase.firestore.DocumentId <fields>;
}

# ── Retrofit + OkHttp ───────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ── Gson ────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Room ────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }

# ── Coil ────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── NewPipeExtractor ────────────────────────────────────────
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.mozilla.**
-dontwarn com.grack.**

# ── ExoPlayer / Media3 ──────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Google Cast SDK ─────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.**

# ── MediaRouter ─────────────────────────────────────────────
-keep class androidx.mediarouter.** { *; }
-dontwarn androidx.mediarouter.**

# ── Palette API ─────────────────────────────────────────────
-keep class androidx.palette.** { *; }

# ── App: data class / model ─────────────────────────────────
# Mantieni tutti i data class del pacchetto app (usati da Room/Firestore/Gson)
-keep class com.muse.app.data.** { *; }
-keep class com.muse.app.model.** { *; }
-keep class com.muse.app.domain.** { *; }

# ── Coroutines ──────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Protobuf (escluso via configurations.all) ───────────────
-dontwarn com.google.protobuf.**
