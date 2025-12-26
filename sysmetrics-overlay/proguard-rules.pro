# Add project specific ProGuard rules here.

# Keep SysMetrics Overlay public API
-keep class com.sysmetrics.overlay.SysMetricsOverlay { *; }
-keep class com.sysmetrics.overlay.OverlayConfig { *; }
-keep class com.sysmetrics.overlay.OverlayHandle { *; }

# Keep FrameRateMonitor
-keep class com.sysmetrics.overlay.fps.FrameRateMonitor { *; }
