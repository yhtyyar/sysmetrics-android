package com.sysmetrics.infrastructure.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import com.sysmetrics.domain.model.NetworkMetrics
import com.sysmetrics.domain.model.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Provides network-related metrics using Android APIs.
 *
 * Collects network traffic statistics, connection state, and network type.
 * Uses TrafficStats for bandwidth measurement and ConnectivityManager for
 * connection information.
 *
 * @property context Application context for accessing system services
 */
public class NetworkMetricsProvider(private val context: Context) {

    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val mutex = Mutex()
    
    @Volatile
    private var lastRxBytes: Long = 0L
    
    @Volatile
    private var lastTxBytes: Long = 0L
    
    @Volatile
    private var lastMeasurementTime: Long = 0L

    /**
     * Retrieves current network metrics.
     *
     * Calculates current bandwidth by comparing with previous measurements.
     * First call establishes baseline and returns zero speeds.
     *
     * @return [Result] containing [NetworkMetrics] or an error
     */
    public suspend fun getNetworkMetrics(): Result<NetworkMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTxBytes = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()

                val (rxBytesPerSecond, txBytesPerSecond) = calculateSpeeds(
                    currentRxBytes, currentTxBytes, currentTime
                )

                // Update last measurements
                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes
                lastMeasurementTime = currentTime

                val (isConnected, networkType, networkName) = getConnectionInfo()

                NetworkMetrics(
                    rxBytes = if (currentRxBytes != TrafficStats.UNSUPPORTED.toLong()) currentRxBytes else 0L,
                    txBytes = if (currentTxBytes != TrafficStats.UNSUPPORTED.toLong()) currentTxBytes else 0L,
                    rxBytesPerSecond = rxBytesPerSecond,
                    txBytesPerSecond = txBytesPerSecond,
                    isConnected = isConnected,
                    connectionType = networkType,
                    networkName = networkName,
                    signalStrength = null // Requires additional permissions
                )
            }
        }
    }

    private fun calculateSpeeds(
        currentRxBytes: Long,
        currentTxBytes: Long,
        currentTime: Long
    ): Pair<Long, Long> {
        if (lastMeasurementTime == 0L) {
            return Pair(0L, 0L)
        }

        val timeDiff = (currentTime - lastMeasurementTime) / 1000.0
        if (timeDiff <= 0) {
            return Pair(0L, 0L)
        }

        val rxDiff = currentRxBytes - lastRxBytes
        val txDiff = currentTxBytes - lastTxBytes

        // Handle counter reset or overflow
        val validRxDiff = if (rxDiff < 0) 0L else rxDiff
        val validTxDiff = if (txDiff < 0) 0L else txDiff

        val rxBytesPerSecond = (validRxDiff / timeDiff).toLong().coerceAtLeast(0L)
        val txBytesPerSecond = (validTxDiff / timeDiff).toLong().coerceAtLeast(0L)

        return Pair(rxBytesPerSecond, txBytesPerSecond)
    }

    private fun getConnectionInfo(): Triple<Boolean, NetworkType, String?> {
        val cm = connectivityManager ?: return Triple(false, NetworkType.NONE, null)

        val activeNetwork = cm.activeNetwork
            ?: return Triple(false, NetworkType.NONE, null)

        val capabilities = cm.getNetworkCapabilities(activeNetwork)
            ?: return Triple(false, NetworkType.NONE, null)

        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.UNKNOWN
        }

        return Triple(isConnected, networkType, null)
    }

    /**
     * Resets internal measurement state.
     *
     * Call this when reinitializing to ensure accurate speed calculations.
     */
    public fun reset() {
        lastRxBytes = 0L
        lastTxBytes = 0L
        lastMeasurementTime = 0L
    }
}
