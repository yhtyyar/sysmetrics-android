# Consumer ProGuard rules for SysMetrics library
# These rules will be applied to consuming applications

-keep class com.sysmetrics.SysMetrics { *; }
-keep class com.sysmetrics.domain.model.** { *; }
-keep class com.sysmetrics.domain.repository.IMetricsRepository { *; }
