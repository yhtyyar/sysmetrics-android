package com.sysmetrics.infrastructure.overlay

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sysmetrics.data.fps.AppMetricsCollector
import com.sysmetrics.data.fps.FpsMetricsCollector
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.FpsPeakEvent
import com.sysmetrics.domain.model.OverlayMetrics
import com.sysmetrics.infrastructure.logger.NoOpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the FPS overlay lifecycle and component orchestration.
 *
 * This class handles:
 * - Lifecycle-aware attachment/detachment
 * - Flow observation and UI updates
 * - Toast notifications for peak events
 * - Database recording (optional)
 * - Resource cleanup
 *
 * ## Usage
 *
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var overlayManager: FpsOverlayManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         overlayManager = FpsOverlayManager.create(this)
 *         overlayManager.attachToActivity(this)
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         overlayManager.detachFromActivity()
 *     }
 * }
 * ```
 *
 * ## Lifecycle
 *
 * The manager automatically responds to lifecycle events:
 * - ON_START: Starts FPS collection
 * - ON_STOP: Pauses collection (conserves battery)
 * - ON_DESTROY: Full cleanup
 *
 * ## Thread Safety
 *
 * All public methods are thread-safe. UI operations are
 * automatically dispatched to the main thread.
 *
 * @property context Application context
 * @property fpsCollector FPS metrics collector
 * @property appCollector App metrics collector
 * @property logger Optional logger for diagnostics
 * @property config Overlay configuration
 */
public class FpsOverlayManager private constructor(
    private val context: Context,
    private val fpsCollector: FpsMetricsCollector,
    private val appCollector: AppMetricsCollector,
    private val logger: MetricsLogger,
    private val config: OverlayConfig
) : LifecycleEventObserver {

    // State
    private val isAttached = AtomicBoolean(false)
    private var activityRef: WeakReference<Activity>? = null
    private var overlayView: FpsOverlayView? = null
    private var scope: CoroutineScope? = null
    private var observerJob: Job? = null

    /**
     * Returns true if overlay is currently attached.
     */
    public val isOverlayVisible: Boolean get() = isAttached.get()

    /**
     * Attaches the overlay to an activity.
     *
     * This will:
     * 1. Create the overlay view
     * 2. Add it to the activity's decor view
     * 3. Start FPS and app metrics collection
     * 4. Begin observing metrics flows
     * 5. Register lifecycle observer
     *
     * @param activity The activity to attach to
     */
    public fun attachToActivity(activity: Activity) {
        if (isAttached.getAndSet(true)) {
            logger.debug(TAG, "Already attached to an activity")
            return
        }

        logger.info(TAG, "Attaching overlay to ${activity.localClassName}")

        activityRef = WeakReference(activity)

        // Create coroutine scope
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Create and add overlay view
        overlayView = FpsOverlayView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = config.gravity
                leftMargin = config.marginLeft
                topMargin = config.marginTop
                rightMargin = config.marginRight
                bottomMargin = config.marginBottom
            }
        }

        // Add to decor view
        val decorView = activity.window.decorView as ViewGroup
        val contentView = decorView.findViewById<ViewGroup>(android.R.id.content)
        contentView.addView(overlayView)

        // Start collectors
        fpsCollector.startCollection()
        appCollector.startCollection(intervalMs = 500L, scope = scope!!)

        // Setup flow observers
        setupObservers()

        // Register lifecycle observer
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
        }
    }

    /**
     * Detaches the overlay from the current activity.
     *
     * This will:
     * 1. Stop FPS and app metrics collection
     * 2. Remove the overlay view
     * 3. Cancel all coroutines
     * 4. Unregister lifecycle observer
     */
    public fun detachFromActivity() {
        if (!isAttached.getAndSet(false)) {
            logger.debug(TAG, "Not attached to any activity")
            return
        }

        logger.info(TAG, "Detaching overlay")

        // Stop collectors
        fpsCollector.stopCollection()
        appCollector.stopCollection()

        // Cancel observers
        observerJob?.cancel()
        observerJob = null

        // Remove overlay view
        overlayView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlayView = null

        // Cancel scope
        scope?.cancel()
        scope = null

        // Unregister lifecycle observer
        activityRef?.get()?.let { activity ->
            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }
        }
        activityRef = null
    }

    /**
     * Toggles overlay visibility.
     */
    public fun toggleVisibility() {
        overlayView?.let { view ->
            view.visibility = if (view.visibility == android.view.View.VISIBLE) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
        }
    }

    /**
     * Shows the overlay.
     */
    public fun show() {
        overlayView?.visibility = android.view.View.VISIBLE
    }

    /**
     * Hides the overlay.
     */
    public fun hide() {
        overlayView?.visibility = android.view.View.GONE
    }

    public override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                if (isAttached.get() && !fpsCollector.isActive) {
                    logger.debug(TAG, "Resuming collection on ON_START")
                    fpsCollector.startCollection()
                    scope?.let { appCollector.startCollection(500L, it) }
                }
            }
            Lifecycle.Event.ON_STOP -> {
                if (fpsCollector.isActive) {
                    logger.debug(TAG, "Pausing collection on ON_STOP")
                    fpsCollector.stopCollection()
                    appCollector.stopCollection()
                }
            }
            Lifecycle.Event.ON_DESTROY -> {
                detachFromActivity()
            }
            else -> { /* ignore other events */ }
        }
    }

    private fun setupObservers() {
        val currentScope = scope ?: return

        observerJob = currentScope.launch {
            // Combine FPS and app metrics flows
            launch {
                fpsCollector.fpsFlow
                    .combine(appCollector.metricsFlow) { fps, app ->
                        OverlayMetrics(fpsMetrics = fps, appMetrics = app)
                    }
                    .collect { metrics ->
                        overlayView?.updateMetrics(metrics)
                    }
            }

            // Observe peak events
            if (config.showToasts) {
                launch {
                    fpsCollector.peakEventFlow.collect { event ->
                        handlePeakEvent(event)
                    }
                }
            }
        }
    }

    private fun handlePeakEvent(event: FpsPeakEvent) {
        val activity = activityRef?.get() ?: return

        val (message, duration) = when (event) {
            is FpsPeakEvent.FrameDrop -> {
                "⚠️ FPS DROP: ${event.fps} fps (-${event.delta})" to Toast.LENGTH_SHORT
            }
            is FpsPeakEvent.HighPerformance -> {
                "✅ High Performance: ${event.fps} fps" to Toast.LENGTH_SHORT
            }
            is FpsPeakEvent.CriticalJank -> {
                "🔴 CRITICAL JANK: ${event.fps} fps" to Toast.LENGTH_LONG
            }
        }

        activity.runOnUiThread {
            Toast.makeText(activity, message, duration).show()
        }

        logger.info(TAG, "Peak event: $message")
    }

    /**
     * Configuration for the FPS overlay.
     */
    public data class OverlayConfig(
        /** Gravity for overlay positioning */
        val gravity: Int = Gravity.TOP or Gravity.START,
        /** Left margin in pixels */
        val marginLeft: Int = 16,
        /** Top margin in pixels */
        val marginTop: Int = 16,
        /** Right margin in pixels */
        val marginRight: Int = 16,
        /** Bottom margin in pixels */
        val marginBottom: Int = 16,
        /** Whether to show toast notifications for peak events */
        val showToasts: Boolean = true,
        /** Whether to record metrics to database */
        val recordToDatabase: Boolean = false
    )

    public companion object {
        private const val TAG = "FpsOverlayManager"

        /**
         * Creates a new FpsOverlayManager with default configuration.
         *
         * @param context Application or activity context
         * @param logger Optional logger
         * @param config Overlay configuration
         * @return New FpsOverlayManager instance
         */
        public fun create(
            context: Context,
            logger: MetricsLogger = NoOpLogger,
            config: OverlayConfig = OverlayConfig()
        ): FpsOverlayManager {
            val fpsCollector = FpsMetricsCollector(logger)
            val appCollector = AppMetricsCollector(context.applicationContext, logger)

            return FpsOverlayManager(
                context = context.applicationContext,
                fpsCollector = fpsCollector,
                appCollector = appCollector,
                logger = logger,
                config = config
            )
        }

        /**
         * Creates a new FpsOverlayManager with custom collectors.
         *
         * @param context Application or activity context
         * @param fpsCollector Custom FPS collector
         * @param appCollector Custom app metrics collector
         * @param logger Optional logger
         * @param config Overlay configuration
         * @return New FpsOverlayManager instance
         */
        public fun create(
            context: Context,
            fpsCollector: FpsMetricsCollector,
            appCollector: AppMetricsCollector,
            logger: MetricsLogger = NoOpLogger,
            config: OverlayConfig = OverlayConfig()
        ): FpsOverlayManager {
            return FpsOverlayManager(
                context = context.applicationContext,
                fpsCollector = fpsCollector,
                appCollector = appCollector,
                logger = logger,
                config = config
            )
        }
    }
}
