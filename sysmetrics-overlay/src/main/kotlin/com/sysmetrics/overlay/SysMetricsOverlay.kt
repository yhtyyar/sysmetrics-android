package com.sysmetrics.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sysmetrics.SysMetrics
import com.sysmetrics.domain.model.FpsMetrics
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.overlay.fps.FrameRateMonitor
import com.sysmetrics.overlay.view.MetricsOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for the SysMetrics overlay functionality.
 *
 * Provides an in-app overlay (HUD) that displays system metrics
 * without requiring SYSTEM_ALERT_WINDOW permission.
 *
 * ## Usage
 *
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private var overlayHandle: OverlayHandle? = null
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         // Attach overlay (only in debug by default)
 *         if (BuildConfig.DEBUG) {
 *             overlayHandle = SysMetricsOverlay.attach(this)
 *         }
 *     }
 *
 *     override fun onDestroy() {
 *         overlayHandle?.detach()
 *         super.onDestroy()
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```kotlin
 * val config = OverlayConfig(
 *     updateIntervalMs = 500,
 *     showNetworkSpeed = true,
 *     draggable = true,
 *     enableInRelease = false // Safe for production
 * )
 * val handle = SysMetricsOverlay.attach(activity, config)
 * ```
 *
 * ## Release Builds
 *
 * By default, the overlay is disabled in release builds unless
 * `enableInRelease = true` is explicitly set in the config.
 * This prevents accidental exposure in production.
 */
public object SysMetricsOverlay {

    private var isDebugBuild: Boolean? = null

    /**
     * Attaches the metrics overlay to the given activity.
     *
     * The overlay is attached to the activity's DecorView and will
     * automatically handle lifecycle events (pause/resume/destroy).
     *
     * @param activity The activity to attach the overlay to
     * @param config Configuration for the overlay
     * @return [OverlayHandle] for controlling the overlay, or null if disabled
     * @throws IllegalStateException if SysMetrics is not initialized
     */
    public fun attach(
        activity: Activity,
        config: OverlayConfig = OverlayConfig()
    ): OverlayHandle? {
        // Check if overlay should be enabled
        if (!shouldEnableOverlay(activity, config)) {
            return null
        }

        // Ensure SysMetrics is initialized
        if (!SysMetrics.isInitialized()) {
            throw IllegalStateException(
                "SysMetrics must be initialized before attaching overlay. " +
                "Call SysMetrics.initialize(context) first."
            )
        }

        return OverlayHandleImpl(activity, config)
    }

    /**
     * Sets whether the current build is a debug build.
     *
     * Call this if automatic detection doesn't work for your build setup.
     *
     * @param isDebug true if this is a debug build
     */
    public fun setDebugBuild(isDebug: Boolean) {
        isDebugBuild = isDebug
    }

    private fun shouldEnableOverlay(activity: Activity, config: OverlayConfig): Boolean {
        // If explicitly enabled for release, allow
        if (config.enableInRelease) {
            return true
        }

        // Check cached value
        isDebugBuild?.let { return it }

        // Try to detect debug build
        return try {
            val appInfo = activity.applicationInfo
            val isDebug = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            isDebugBuild = isDebug
            isDebug
        } catch (e: Exception) {
            // If detection fails, default to disabled for safety
            false
        }
    }
}

/**
 * Implementation of [OverlayHandle] that manages the overlay lifecycle.
 */
internal class OverlayHandleImpl(
    activity: Activity,
    private var config: OverlayConfig
) : OverlayHandle, LifecycleEventObserver {

    private val activityRef = WeakReference(activity)
    private val attached = AtomicBoolean(true)
    private var overlayView: MetricsOverlayView? = null
    private val frameRateMonitor = FrameRateMonitor()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var metricsJob: Job? = null
    private var fpsJob: Job? = null

    private var lifecycleOwner: LifecycleOwner? = null
    private var activityCallbacks: Application.ActivityLifecycleCallbacks? = null

    override val isAttached: Boolean get() = attached.get()
    override val isExpanded: Boolean get() = overlayView?.getIsExpanded() ?: false

    init {
        attachToActivity(activity)
    }

    private fun attachToActivity(activity: Activity) {
        // Create overlay view
        overlayView = MetricsOverlayView(activity, config).apply {
            onExpandedChanged = { expanded ->
                // Optional callback for expanded state changes
            }
        }

        // Add to DecorView
        val decorView = activity.window.decorView as? ViewGroup ?: return
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        decorView.addView(overlayView, params)

        // Set initial position
        overlayView?.post {
            overlayView?.let { view ->
                val parent = view.parent as? ViewGroup ?: return@post
                view.x = config.initialPositionX * (parent.width - view.width)
                view.y = config.initialPositionY * (parent.height - view.height)
            }
        }

        // Setup lifecycle observation
        setupLifecycleObservation(activity)

        // Start monitoring
        startMonitoring(activity)
    }

    private fun setupLifecycleObservation(activity: Activity) {
        // Try to use LifecycleOwner if available
        if (activity is LifecycleOwner) {
            lifecycleOwner = activity
            activity.lifecycle.addObserver(this)
        } else {
            // Fallback to ActivityLifecycleCallbacks
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {
                    if (a === activityRef.get()) onResume()
                }
                override fun onActivityPaused(a: Activity) {
                    if (a === activityRef.get()) onPause()
                }
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {
                    if (a === activityRef.get()) detach()
                }
            }
            activityCallbacks = callbacks
            activity.application.registerActivityLifecycleCallbacks(callbacks)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_DESTROY -> detach()
            else -> { /* ignore */ }
        }
    }

    private fun startMonitoring(activity: Activity) {
        // Start FPS monitoring
        frameRateMonitor.start(activity)

        // Collect FPS metrics
        fpsJob = scope.launch {
            frameRateMonitor.fpsFlow
                .catch { /* ignore errors */ }
                .collect { fps ->
                    overlayView?.updateFps(fps)
                }
        }

        // Collect system metrics
        metricsJob = scope.launch {
            SysMetrics.observeMetrics(config.updateIntervalMs)
                .flowOn(Dispatchers.IO)
                .catch { /* ignore errors */ }
                .collect { metrics ->
                    overlayView?.updateMetrics(metrics)
                }
        }
    }

    private fun stopMonitoring() {
        metricsJob?.cancel()
        fpsJob?.cancel()
        metricsJob = null
        fpsJob = null
        frameRateMonitor.stop()
    }

    private fun onResume() {
        if (!attached.get()) return
        overlayView?.visibility = android.view.View.VISIBLE
        activityRef.get()?.let { activity ->
            frameRateMonitor.start(activity)
        }
    }

    private fun onPause() {
        overlayView?.visibility = android.view.View.GONE
        frameRateMonitor.stop()
    }

    override fun detach() {
        if (!attached.getAndSet(false)) return

        // Stop monitoring
        stopMonitoring()

        // Remove view
        val activity = activityRef.get()
        overlayView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlayView = null

        // Remove lifecycle observer
        lifecycleOwner?.lifecycle?.removeObserver(this)
        lifecycleOwner = null

        // Remove activity callbacks
        activityCallbacks?.let { callbacks ->
            activity?.application?.unregisterActivityLifecycleCallbacks(callbacks)
        }
        activityCallbacks = null

        // Cancel scope
        scope.cancel()
    }

    override fun toggleExpanded() {
        overlayView?.toggleExpanded()
    }

    override fun setExpanded(expanded: Boolean) {
        overlayView?.setExpanded(expanded)
    }

    override fun show() {
        overlayView?.visibility = android.view.View.VISIBLE
    }

    override fun hide() {
        overlayView?.visibility = android.view.View.GONE
    }

    override fun updateConfig(config: OverlayConfig) {
        this.config = config
        overlayView?.applyConfig(config)
        
        // Restart metrics collection with new interval
        metricsJob?.cancel()
        metricsJob = scope.launch {
            SysMetrics.observeMetrics(config.updateIntervalMs)
                .flowOn(Dispatchers.IO)
                .catch { /* ignore errors */ }
                .collect { metrics ->
                    overlayView?.updateMetrics(metrics)
                }
        }
    }
}
