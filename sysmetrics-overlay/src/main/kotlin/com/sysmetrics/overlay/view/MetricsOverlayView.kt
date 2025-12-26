package com.sysmetrics.overlay.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sysmetrics.domain.model.FpsMetrics
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.overlay.OverlayConfig

/**
 * Custom view for displaying metrics overlay.
 *
 * Supports collapsed (compact) and expanded (full) modes with
 * optional drag functionality.
 */
@SuppressLint("ViewConstructor")
internal class MetricsOverlayView(
    context: Context,
    private var config: OverlayConfig
) : FrameLayout(context) {

    private val collapsedContainer: LinearLayout
    private val expandedContainer: ScrollView
    private val expandedContent: LinearLayout
    private val toggleButton: TextView

    // Collapsed mode views
    private val fpsText: TextView
    private val cpuText: TextView
    private val ramText: TextView
    private val netText: TextView

    // Expanded mode additional views
    private val expandedMetricsContainer: LinearLayout

    private var isExpanded = false
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f

    var onExpandedChanged: ((Boolean) -> Unit)? = null

    init {
        // Main container
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )

        // Create collapsed view
        collapsedContainer = createCollapsedContainer()
        fpsText = createMetricText("FPS: --")
        cpuText = createMetricText("CPU: --%")
        ramText = createMetricText("RAM: --%")
        netText = createMetricText("NET: ↓-- ↑--")

        collapsedContainer.addView(fpsText)
        collapsedContainer.addView(cpuText)
        collapsedContainer.addView(ramText)
        collapsedContainer.addView(netText)

        // Create toggle button
        toggleButton = createToggleButton()
        collapsedContainer.addView(toggleButton)

        // Create expanded container
        expandedContainer = ScrollView(context).apply {
            layoutParams = LayoutParams(
                dpToPx(280),
                dpToPx(400)
            )
            visibility = View.GONE
            setBackgroundColor(config.backgroundColor)
        }

        expandedContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        // Header in expanded mode
        val expandedHeader = createExpandedHeader()
        expandedContent.addView(expandedHeader)

        // Metrics container for expanded mode
        expandedMetricsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        expandedContent.addView(expandedMetricsContainer)

        expandedContainer.addView(expandedContent)

        // Add views to main container
        addView(collapsedContainer)
        addView(expandedContainer)

        // Apply initial config
        applyConfig(config)

        // Set expanded state
        if (config.startExpanded) {
            toggleExpanded()
        }

        // Setup touch handling for drag
        if (config.draggable) {
            setupDragHandling()
        }
    }

    private fun createCollapsedContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(config.backgroundColor)
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
        }
    }

    private fun createMetricText(initial: String): TextView {
        return TextView(context).apply {
            text = initial
            setTextColor(config.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createToggleButton(): TextView {
        return TextView(context).apply {
            text = "▼ More"
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp - 1)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { toggleExpanded() }
        }
    }

    private fun createExpandedHeader(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL

            // Title
            addView(TextView(context).apply {
                text = "System Metrics"
                setTextColor(config.textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp + 2)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                )
            })

            // Collapse button
            addView(TextView(context).apply {
                text = "▲"
                setTextColor(Color.argb(180, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp + 2)
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                setOnClickListener { toggleExpanded() }
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandling() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    viewStartX = x
                    viewStartY = y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - lastTouchX
                    val deltaY = event.rawY - lastTouchY
                    
                    // Start drag if moved more than touch slop
                    if (!isDragging && (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        x = viewStartX + deltaX
                        y = viewStartY + deltaY
                        
                        // Constrain to parent bounds
                        val parent = parent as? ViewGroup
                        if (parent != null) {
                            x = x.coerceIn(0f, (parent.width - width).toFloat())
                            y = y.coerceIn(0f, (parent.height - height).toFloat())
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    fun toggleExpanded() {
        isExpanded = !isExpanded
        updateExpandedState()
        onExpandedChanged?.invoke(isExpanded)
    }

    fun setExpanded(expanded: Boolean) {
        if (isExpanded != expanded) {
            isExpanded = expanded
            updateExpandedState()
            onExpandedChanged?.invoke(isExpanded)
        }
    }

    private fun updateExpandedState() {
        if (isExpanded) {
            collapsedContainer.visibility = View.GONE
            expandedContainer.visibility = View.VISIBLE
        } else {
            collapsedContainer.visibility = View.VISIBLE
            expandedContainer.visibility = View.GONE
        }
    }

    fun updateFps(fps: FpsMetrics) {
        val color = when {
            fps.isCritical -> config.criticalColor
            fps.isWarning -> config.warningColor
            else -> config.goodColor
        }
        fpsText.text = "FPS: ${fps.currentFps}"
        fpsText.setTextColor(color)
    }

    fun updateMetrics(metrics: SystemMetrics) {
        // Update collapsed view
        updateCollapsedMetrics(metrics)
        
        // Update expanded view
        updateExpandedMetrics(metrics)
    }

    private fun updateCollapsedMetrics(metrics: SystemMetrics) {
        // CPU
        val cpuPercent = metrics.cpuMetrics.usagePercent
        val cpuColor = when {
            cpuPercent >= 90 -> config.criticalColor
            cpuPercent >= 70 -> config.warningColor
            else -> config.goodColor
        }
        cpuText.text = "CPU: ${cpuPercent.toInt()}%"
        cpuText.setTextColor(cpuColor)

        // RAM
        val ramPercent = metrics.memoryMetrics.usagePercent
        val ramColor = when {
            ramPercent >= 90 -> config.criticalColor
            ramPercent >= 75 -> config.warningColor
            else -> config.goodColor
        }
        ramText.text = "RAM: ${ramPercent.toInt()}%"
        ramText.setTextColor(ramColor)

        // Network
        if (config.showNetworkSpeed) {
            val rxSpeed = formatSpeed(metrics.networkMetrics.rxBytesPerSecond)
            val txSpeed = formatSpeed(metrics.networkMetrics.txBytesPerSecond)
            netText.text = "NET: ↓$rxSpeed ↑$txSpeed"
            netText.visibility = View.VISIBLE
        } else {
            netText.visibility = View.GONE
        }
    }

    private fun updateExpandedMetrics(metrics: SystemMetrics) {
        expandedMetricsContainer.removeAllViews()

        // Separator
        addSeparator()

        // CPU Section
        addSectionHeader("CPU")
        addMetricRow("Usage", "${metrics.cpuMetrics.usagePercent.toInt()}%")
        addMetricRow("Cores", "${metrics.cpuMetrics.physicalCores}")
        metrics.cpuMetrics.currentFrequencyKHz?.let {
            addMetricRow("Frequency", "${it / 1000} MHz")
        }

        // Memory Section
        addSectionHeader("Memory")
        addMetricRow("Usage", "${metrics.memoryMetrics.usagePercent.toInt()}%")
        addMetricRow("Used", "${metrics.memoryMetrics.usedMB} MB")
        addMetricRow("Available", "${metrics.memoryMetrics.availableMB} MB")
        addMetricRow("Total", "${metrics.memoryMetrics.totalMB} MB")

        // Battery Section
        addSectionHeader("Battery")
        addMetricRow("Level", "${metrics.batteryMetrics.level}%")
        addMetricRow("Status", metrics.batteryMetrics.status.name)
        addMetricRow("Health", metrics.batteryMetrics.health.name)
        addMetricRow("Temperature", "${metrics.batteryMetrics.temperature / 10.0}°C")

        // Thermal Section
        addSectionHeader("Thermal")
        metrics.thermalMetrics.cpuTemperature?.let {
            addMetricRow("CPU Temp", "${it}°C")
        }
        addMetricRow("Throttling", if (metrics.thermalMetrics.isThrottling) "Yes" else "No")

        // Storage Section
        addSectionHeader("Storage")
        addMetricRow("Used", "${metrics.storageMetrics.usedGB} GB")
        addMetricRow("Available", "${metrics.storageMetrics.availableGB} GB")
        addMetricRow("Total", "${metrics.storageMetrics.totalGB} GB")

        // Network Section
        addSectionHeader("Network")
        addMetricRow("Type", metrics.networkMetrics.connectionType.name)
        addMetricRow("Connected", if (metrics.networkMetrics.isConnected) "Yes" else "No")
        addMetricRow("Download", formatSpeed(metrics.networkMetrics.rxBytesPerSecond))
        addMetricRow("Upload", formatSpeed(metrics.networkMetrics.txBytesPerSecond))
        addMetricRow("Total RX", formatBytes(metrics.networkMetrics.rxBytes))
        addMetricRow("Total TX", formatBytes(metrics.networkMetrics.txBytes))
    }

    private fun addSeparator() {
        expandedMetricsContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(4)
            }
            setBackgroundColor(Color.argb(50, 255, 255, 255))
        })
    }

    private fun addSectionHeader(title: String) {
        expandedMetricsContainer.addView(TextView(context).apply {
            text = title
            setTextColor(config.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp + 1)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(2)
            }
        })
    }

    private fun addMetricRow(label: String, value: String) {
        expandedMetricsContainer.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )

            addView(TextView(context).apply {
                text = "$label:"
                setTextColor(Color.argb(180, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                )
            })

            addView(TextView(context).apply {
                text = value
                setTextColor(config.textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)
                typeface = Typeface.MONOSPACE
            })
        })
    }

    fun applyConfig(newConfig: OverlayConfig) {
        config = newConfig
        
        // Update colors
        collapsedContainer.setBackgroundColor(config.backgroundColor)
        expandedContainer.setBackgroundColor(config.backgroundColor)
        
        // Update text sizes
        val textViews = listOf(fpsText, cpuText, ramText, netText)
        textViews.forEach { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)
        }
        
        // Update opacity
        alpha = config.opacity
        
        // Update network visibility
        if (!config.showNetworkSpeed) {
            netText.visibility = View.GONE
        }
        
        // Update FPS visibility
        if (!config.showFps) {
            fpsText.visibility = View.GONE
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_000_000 -> String.format("%.1fM", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format("%.1fK", bytesPerSecond / 1_000.0)
            else -> "${bytesPerSecond}B"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun getIsExpanded(): Boolean = isExpanded
}
