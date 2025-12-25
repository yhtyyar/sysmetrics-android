# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep SysMetrics public API
-keep class com.sysmetrics.SysMetrics { *; }
-keep class com.sysmetrics.domain.model.** { *; }
-keep class com.sysmetrics.domain.repository.IMetricsRepository { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.sysmetrics.**$$serializer { *; }
-keepclassmembers class com.sysmetrics.** {
    *** Companion;
}
-keepclasseswithmembers class com.sysmetrics.** {
    kotlinx.serialization.KSerializer serializer(...);
}
