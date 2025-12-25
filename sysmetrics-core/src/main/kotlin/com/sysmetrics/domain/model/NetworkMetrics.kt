package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents network-related metrics for the device.
 *
 * Contains information about network traffic including bytes transmitted
 * and received, as well as current connection state and type.
 *
 * @property rxBytes Total bytes received since device boot
 * @property txBytes Total bytes transmitted since device boot
 * @property rxBytesPerSecond Current download speed in bytes per second
 * @property txBytesPerSecond Current upload speed in bytes per second
 * @property isConnected Whether the device has network connectivity
 * @property connectionType Type of network connection (WiFi, Mobile, Ethernet, etc.)
 * @property networkName Name of the connected network (SSID for WiFi), null if unavailable
 * @property signalStrength Signal strength in dBm, null if unavailable
 */
@Serializable
public data class NetworkMetrics(
    val rxBytes: Long,
    val txBytes: Long,
    val rxBytesPerSecond: Long,
    val txBytesPerSecond: Long,
    val isConnected: Boolean,
    val connectionType: NetworkType,
    val networkName: String? = null,
    val signalStrength: Int? = null
) {
    init {
        require(rxBytes >= 0) { "rxBytes must be non-negative" }
        require(txBytes >= 0) { "txBytes must be non-negative" }
        require(rxBytesPerSecond >= 0) { "rxBytesPerSecond must be non-negative" }
        require(txBytesPerSecond >= 0) { "txBytesPerSecond must be non-negative" }
    }

    /**
     * Returns the total bytes transferred (rx + tx).
     */
    public val totalBytes: Long get() = rxBytes + txBytes

    /**
     * Returns the current total speed in bytes per second.
     */
    public val totalBytesPerSecond: Long get() = rxBytesPerSecond + txBytesPerSecond

    /**
     * Returns download speed formatted as human-readable string.
     */
    public fun getFormattedDownloadSpeed(): String = formatBytesPerSecond(rxBytesPerSecond)

    /**
     * Returns upload speed formatted as human-readable string.
     */
    public fun getFormattedUploadSpeed(): String = formatBytesPerSecond(txBytesPerSecond)

    private fun formatBytesPerSecond(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_000_000_000 -> String.format("%.2f GB/s", bytesPerSecond / 1_000_000_000.0)
            bytesPerSecond >= 1_000_000 -> String.format("%.2f MB/s", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format("%.2f KB/s", bytesPerSecond / 1_000.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    public companion object {
        /**
         * Creates an empty NetworkMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): NetworkMetrics = NetworkMetrics(
            rxBytes = 0,
            txBytes = 0,
            rxBytesPerSecond = 0,
            txBytesPerSecond = 0,
            isConnected = false,
            connectionType = NetworkType.NONE
        )
    }
}

/**
 * Represents the type of network connection.
 */
@Serializable
public enum class NetworkType {
    /** No network connection */
    NONE,
    /** WiFi connection */
    WIFI,
    /** Mobile data connection (2G, 3G, 4G, 5G) */
    MOBILE,
    /** Ethernet connection */
    ETHERNET,
    /** Bluetooth tethering */
    BLUETOOTH,
    /** VPN connection */
    VPN,
    /** Unknown connection type */
    UNKNOWN
}
