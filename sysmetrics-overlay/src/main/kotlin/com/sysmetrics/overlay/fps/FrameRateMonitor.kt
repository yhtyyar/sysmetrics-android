package com.sysmetrics.overlay.fps

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import com.sysmetrics.domain.model.FpsMetrics
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors frame rate using Choreographer (API 16+) with FrameMetricsAggregator
 * fallback on API 24+.
 *
 * Uses exponential moving average (EMA) for smoothed FPS values.
 *
 * ## Usage
 *
 * ```kotlin
 * val monitor = FrameRateMonitor()
 * monitor.start(activity)
 *
 * // Observe FPS
 * monitor.fpsFlow.collect { fps ->
 *     updateUI(fps)
 * }
 *
 * // Stop when done
 * monitor.stop()
 * ```
 */
public class FrameRateMonitor {

    private val _fpsFlow = MutableSharedFlow<FpsMetrics>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Flow of FPS metrics, emits approximately once per second */
    public val fpsFlow: SharedFlow<FpsMetrics> = _fpsFlow.asSharedFlow()

    private val isRunning = AtomicBoolean(false)
    private val frameCount = AtomicInteger(0)
    private val jankCount = AtomicInteger(0)
    private val lastFrameTimeNanos = AtomicLong(0)
    private val windowStartTimeNanos = AtomicLong(0)
    
    private var minFps = Int.MAX_VALUE
    private var maxFps = 0
    private var emaFps = 0f
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var choreographer: Choreographer? = null
    private var frameMetricsListener: Any? = null
    private var attachedWindow: Window? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning.get()) return

            val lastTime = lastFrameTimeNanos.get()
            if (lastTime > 0) {
                val frameDurationMs = (frameTimeNanos - lastTime) / 1_000_000f
                
                // Detect jank (frame time > 16.67ms for 60fps target)
                if (frameDurationMs > JANK_THRESHOLD_MS) {
                    jankCount.incrementAndGet()
                }
            }
            
            lastFrameTimeNanos.set(frameTimeNanos)
            frameCount.incrementAndGet()

            // Check if window has elapsed
            val windowStart = windowStartTimeNanos.get()
            val elapsedNanos = frameTimeNanos - windowStart
            
            if (elapsedNanos >= WINDOW_SIZE_NANOS) {
                calculateAndEmitFps(elapsedNanos)
                windowStartTimeNanos.set(frameTimeNanos)
                frameCount.set(0)
                jankCount.set(0)
            }

            // Schedule next frame
            choreographer?.postFrameCallback(this)
        }
    }

    /**
     * Starts FPS monitoring for the given activity.
     *
     * @param activity The activity to monitor
     */
    public fun start(activity: Activity) {
        if (isRunning.getAndSet(true)) return

        mainHandler.post {
            resetCounters()
            choreographer = Choreographer.getInstance()
            
            // Try to use FrameMetrics on API 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                attachFrameMetricsListener(activity.window)
            }
            
            // Always use Choreographer as primary/fallback
            windowStartTimeNanos.set(System.nanoTime())
            choreographer?.postFrameCallback(frameCallback)
        }
    }

    /**
     * Stops FPS monitoring and releases resources.
     */
    public fun stop() {
        if (!isRunning.getAndSet(false)) return

        mainHandler.post {
            choreographer?.removeFrameCallback(frameCallback)
            choreographer = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                detachFrameMetricsListener()
            }
            
            resetCounters()
        }
    }

    /**
     * Returns the current FPS value (latest calculated).
     */
    public fun getCurrentFps(): Int = emaFps.toInt()

    @RequiresApi(Build.VERSION_CODES.N)
    private fun attachFrameMetricsListener(window: Window) {
        attachedWindow = window
        val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val totalDurationMs = totalDurationNs / 1_000_000f
            
            if (totalDurationMs > JANK_THRESHOLD_MS) {
                // Additional jank detection from FrameMetrics
                // Note: We already count janks in Choreographer callback
            }
        }
        frameMetricsListener = listener
        window.addOnFrameMetricsAvailableListener(listener, mainHandler)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun detachFrameMetricsListener() {
        val window = attachedWindow ?: return
        val listener = frameMetricsListener as? Window.OnFrameMetricsAvailableListener ?: return
        
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (e: Exception) {
            // Window might be destroyed
        }
        
        attachedWindow = null
        frameMetricsListener = null
    }

    private fun calculateAndEmitFps(elapsedNanos: Long) {
        val frames = frameCount.get()
        val janks = jankCount.get()
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        
        if (elapsedSeconds <= 0) return
        
        val currentFps = (frames / elapsedSeconds).toInt().coerceIn(0, MAX_FPS)
        
        // Update min/max
        if (currentFps > 0) {
            minFps = minOf(minFps, currentFps)
            maxFps = maxOf(maxFps, currentFps)
        }
        
        // Calculate EMA (exponential moving average)
        emaFps = if (emaFps == 0f) {
            currentFps.toFloat()
        } else {
            EMA_ALPHA * currentFps + (1 - EMA_ALPHA) * emaFps
        }
        
        val metrics = FpsMetrics(
            currentFps = currentFps,
            averageFps = emaFps,
            minFps = if (minFps == Int.MAX_VALUE) 0 else minFps,
            maxFps = maxFps,
            frameCount = frames,
            frameTimeMs = if (currentFps > 0) 1000f / currentFps else 0f,
            jankCount = janks
        )
        
        _fpsFlow.tryEmit(metrics)
    }

    private fun resetCounters() {
        frameCount.set(0)
        jankCount.set(0)
        lastFrameTimeNanos.set(0)
        windowStartTimeNanos.set(0)
        minFps = Int.MAX_VALUE
        maxFps = 0
        emaFps = 0f
    }

    public companion object {
        /** Window size for FPS calculation (1 second) */
        private const val WINDOW_SIZE_NANOS = 1_000_000_000L
        
        /** Frame time threshold for jank detection (60fps = 16.67ms) */
        private const val JANK_THRESHOLD_MS = 16.67f
        
        /** EMA smoothing factor (0-1, higher = more responsive) */
        private const val EMA_ALPHA = 0.3f
        
        /** Maximum reasonable FPS value */
        private const val MAX_FPS = 240
    }
}
