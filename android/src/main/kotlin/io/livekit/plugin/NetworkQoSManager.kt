package io.livekit.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class NetworkQoSManager(private val context: Context) {
    companion object {
        private const val TAG = "NetworkQoSManager"

        // DSCP values for different QoS levels
        const val DSCP_EF = 46      // Expedited Forwarding (highest priority)
        const val DSCP_AF41 = 34    // Assured Forwarding (high priority)
        const val DSCP_AF31 = 26    // Assured Forwarding (medium priority)
        const val DSCP_AF21 = 18    // Assured Forwarding (low priority)
        const val DSCP_AF11 = 10    // Assured Forwarding (lowest priority)
        const val DSCP_CS0 = 0      // Best Effort (default)
    }

    private var connectivityManager: ConnectivityManager? = null
    private var currentNetwork: Network? = null

    // Socket registry to map socketId to actual socket objects
    private val socketRegistry = ConcurrentHashMap<String, Any>()

    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Register a socket with a unique ID
     */
    fun registerSocket(socketId: String, socket: Any): Boolean {
        return try {
            socketRegistry[socketId] = socket
            Log.d(TAG, "Socket registered with ID: $socketId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register socket: ${e.message}")
            false
        }
    }

    /**
     * Unregister a socket
     */
    fun unregisterSocket(socketId: String): Boolean {
        return try {
            val removed = socketRegistry.remove(socketId)
            if (removed != null) {
                Log.d(TAG, "Socket unregistered with ID: $socketId")
                true
            } else {
                Log.w(TAG, "Socket not found with ID: $socketId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister socket: ${e.message}")
            false
        }
    }

    /**
     * Get socket by ID
     */
    fun getSocket(socketId: String): Any? {
        return socketRegistry[socketId]
    }

    /**
     * Get all registered socket IDs
     */
    fun getRegisteredSocketIds(): List<String> {
        return socketRegistry.keys.toList()
    }

    /**
     * Set QoS for UDP socket by ID
     */
    fun setUDPQoS(socketId: String, dscpValue: Int): Boolean {
        return try {
            val socket = socketRegistry[socketId]
            if (socket is DatagramSocket) {
                setUDPQoS(socket, dscpValue)
            } else {
                Log.w(TAG, "Socket with ID $socketId is not a UDP socket")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set UDP QoS for socket $socketId: ${e.message}")
            false
        }
    }

    /**
     * Set QoS for TCP socket by ID
     */
    fun setTCPQoS(socketId: String, dscpValue: Int): Boolean {
        return try {
            val socket = socketRegistry[socketId]
            if (socket is Socket) {
                setTCPQoS(socket, dscpValue)
            } else {
                Log.w(TAG, "Socket with ID $socketId is not a TCP socket")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set TCP QoS for socket $socketId: ${e.message}")
            false
        }
    }

    /**
     * Set QoS for UDP socket with specific DSCP value
     */
    fun setUDPQoS(socket: DatagramSocket, dscpValue: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setTrafficClass(socket, dscpValue)
            } else {
                setTrafficClassLegacy(socket, dscpValue)
            }
            Log.d(TAG, "UDP QoS set successfully with DSCP: $dscpValue")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set UDP QoS: ${e.message}")
            false
        }
    }

    /**
     * Set QoS for TCP socket with specific DSCP value
     */
    fun setTCPQoS(socket: Socket, dscpValue: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setTrafficClass(socket, dscpValue)
            } else {
                setTrafficClassLegacy(socket, dscpValue)
            }
            Log.d(TAG, "TCP QoS set successfully with DSCP: $dscpValue")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set TCP QoS: ${e.message}")
            false
        }
    }

    /**
     * Set adaptive QoS for socket by ID
     */
    fun setAdaptiveQoS(socketId: String): Boolean {
        return try {
            val socket = socketRegistry[socketId]
            if (socket != null) {
                val networkType = getCurrentNetworkType()
                val dscpValue = getAdaptiveDSCP(networkType)

                when (socket) {
                    is DatagramSocket -> setUDPQoS(socket, dscpValue)
                    is Socket -> setTCPQoS(socket, dscpValue)
                    else -> false
                }
            } else {
                Log.w(TAG, "Socket not found with ID: $socketId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set adaptive QoS for socket $socketId: ${e.message}")
            false
        }
    }

    /**
     * Get adaptive DSCP value based on network type
     */
    private fun getAdaptiveDSCP(networkType: String): Int {
        return when (networkType) {
            "WIFI" -> DSCP_AF41      // High priority for WiFi
            "CELLULAR" -> DSCP_EF     // Highest priority for cellular
            "ETHERNET" -> DSCP_AF31   // Medium priority for Ethernet
            "BLUETOOTH" -> DSCP_AF21  // Lower priority for Bluetooth
            else -> DSCP_CS0          // Default for unknown networks
        }
    }

    /**
     * Get current network type
     */
    fun getCurrentNetworkType(): String {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork
            val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "BLUETOOTH"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network type: ${e.message}")
            "UNKNOWN"
        }
    }
    /**
     * Set traffic class for modern Android versions
     */
    private fun setTrafficClass(socket: Any, dscpValue: Int) {
        try {
            val setTrafficClassMethod = socket.javaClass.getMethod("setTrafficClass", Int::class.java)
            setTrafficClassMethod.invoke(socket, dscpValue)
        } catch (e: Exception) {
            Log.w(TAG, "setTrafficClass not available, trying legacy method")
            setTrafficClassLegacy(socket, dscpValue)
        }
    }

    /**
     * Set traffic class using reflection for older Android versions
     */
    private fun setTrafficClassLegacy(socket: Any, dscpValue: Int) {
        try {
            val setTrafficClassMethod = socket.javaClass.getMethod("setTrafficClass", Int::class.java)
            setTrafficClassMethod.invoke(socket, dscpValue)
        } catch (e: Exception) {
            Log.w(TAG, "Legacy setTrafficClass failed: ${e.message}")
            // Try alternative method names
            tryAlternativeTrafficClassMethods(socket, dscpValue)
        }
    }

    /**
     * Try alternative method names for setting traffic class
     */
    private fun tryAlternativeTrafficClassMethods(socket: Any, dscpValue: Int) {
        val alternativeMethods = listOf("setTrafficClass", "setDSCP", "setQoS")

        for (methodName in alternativeMethods) {
            try {
                val method = socket.javaClass.getMethod(methodName, Int::class.java)
                method.invoke(socket, dscpValue)
                Log.d(TAG, "Successfully set QoS using method: $methodName")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Method $methodName not available: ${e.message}")
            }
        }

        Log.w(TAG, "No available method found to set QoS")
    }

    /**
     * Clean up all registered sockets
     */
    fun cleanup() {
        try {
            socketRegistry.clear()
            Log.d(TAG, "All sockets cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup sockets: ${e.message}")
        }
    }
}