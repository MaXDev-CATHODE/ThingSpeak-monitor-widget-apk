# ── ProGuard / R8 Rules ──────────────────────────────────────────
# R8 rules for release build.
# Debug build ignores this file (minifyEnabled = false by default).

# Keep Retrofit interface classes (reflection-based)
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase

# Glance
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# WorkManager entry points
-keep class com.thingspeak.monitor.core.worker.** { *; }
-keep class com.thingspeak.monitor.feature.widget.WidgetRefreshWorker { *; }

# Keep models for Retrofit/Serialization
-keep class com.thingspeak.monitor.feature.channel.data.dto.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
