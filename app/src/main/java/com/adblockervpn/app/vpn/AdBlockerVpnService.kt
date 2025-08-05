package com.adblockervpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adblockervpn.app.MainActivity
import com.adblockervpn.app.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.net.InetAddress
import java.net.Socket
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

class AdBlockerVpnService : VpnService() {
    
    companion object {
        private const val TAG = "AdBlockerVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AdBlockerVPN"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val DNS_SERVER = "8.8.8.8"
        
        var isRunning = false
        var adsBlocked = 0
        
        // Add method to get detailed status for debugging
        fun getStatusInfo(): String {
            return "VPN Status: isRunning=$isRunning, adsBlocked=$adsBlocked"
        }
        
        // Add method to get detailed error information
        fun getDetailedStatus(): String {
            return try {
                val hasPermission = try {
                    val intent = VpnService.prepare(null)
                    intent == null
                } catch (e: Exception) {
                    false
                }
                "VPN Status: isRunning=$isRunning, adsBlocked=$adsBlocked, hasPermission=$hasPermission"
            } catch (e: Exception) {
                "VPN Status: Error getting status - ${e.message}"
            }
        }
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private var tunnelJob: Job? = null
    
    // Ad domain blocklist
    private val adDomains = setOf(
        "ads.google.com",
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "ad.doubleclick.net",
        "pagead2.googlesyndication.com",
        "static.doubleclick.net",
        "www.googleadservices.com",
        "adclick.g.doubleclick.net",
        "googleads.g.doubleclick.net",
        "www.googletagmanager.com",
        "googletagmanager.com",
        "www.google-analytics.com",
        "google-analytics.com",
        "ssl.google-analytics.com",
        "www.facebook.com",
        "facebook.com",
        "ads.facebook.com",
        "an.facebook.com",
        "www.youtube.com",
        "youtube.com",
        "ads.youtube.com",
        "www.instagram.com",
        "instagram.com",
        "ads.instagram.com"
    )
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
            when (intent?.action) {
                "START_VPN" -> startVpn()
                "STOP_VPN" -> stopVpn()
                else -> Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            isRunning = false
            // Try to show error to user
            try {
                val errorMsg = "VPN Service Error: ${e.message}"
                Log.e(TAG, errorMsg, e)
            } catch (e2: Exception) {
                Log.e(TAG, "Error showing error message", e2)
            }
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        if (isRunning) {
            Log.d(TAG, "VPN already running, ignoring start request")
            return
        }
        
        try {
            Log.d(TAG, "Starting VPN service...")
            
            // Check if we have the necessary permissions
            if (!hasVpnPermission()) {
                val errorMsg = "VPN permission not granted"
                Log.e(TAG, errorMsg)
                throw Exception(errorMsg)
            }
            
            // Create VPN interface with proper configuration
            val builder = Builder()
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_SERVER)
                .addRoute(VPN_ROUTE, 0)
                .setSession("AdBlockerVPN")
                .setMtu(1500)
                .allowFamily(4) // IPv4
                .allowFamily(6) // IPv6
            
            Log.d(TAG, "Establishing VPN interface with config: address=$VPN_ADDRESS, dns=$DNS_SERVER, route=$VPN_ROUTE")
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Log.d(TAG, "VPN interface established successfully")
                isRunning = true
                startForeground(NOTIFICATION_ID, createNotification())
                startTunnel()
                Log.d(TAG, "VPN started successfully")
            } else {
                val errorMsg = "Failed to establish VPN interface - builder.establish() returned null"
                Log.e(TAG, errorMsg)
                isRunning = false
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start VPN: ${e.message}"
            Log.e(TAG, errorMsg, e)
            isRunning = false
            throw e
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        serviceJob?.cancel()
        tunnelJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }
    
    private fun startTunnel() {
        tunnelJob = CoroutineScope(Dispatchers.IO).launch {
            var inputStream: FileInputStream? = null
            var outputStream: FileOutputStream? = null
            
            try {
                vpnInterface?.let { vpn ->
                    Log.d(TAG, "Setting up tunnel streams...")
                    inputStream = FileInputStream(vpn.fileDescriptor)
                    outputStream = FileOutputStream(vpn.fileDescriptor)
                    Log.d(TAG, "Tunnel streams created successfully")
                } ?: run {
                    val errorMsg = "VPN interface is null - cannot start tunnel"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }
                
                val buffer = ByteArray(32767)
                
                Log.d(TAG, "Tunnel started, processing packets...")
                
                while (isRunning) {
                    try {
                        val length = inputStream?.read(buffer) ?: -1
                        if (length > 0) {
                            // Process packet and check for ads
                            if (isAdDomain(buffer, length)) {
                                adsBlocked++
                                Log.d(TAG, "Ad blocked! Total: $adsBlocked")
                                // Don't forward ad traffic
                                continue
                            }
                            
                            // Forward legitimate traffic
                            outputStream?.write(buffer, 0, length)
                            outputStream?.flush()
                        } else if (length == -1) {
                            // End of stream
                            Log.d(TAG, "End of VPN stream")
                            break
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error reading VPN data", e)
                            delay(100) // Small delay before retry
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error in tunnel", e)
                        if (isRunning) {
                            delay(100) // Small delay before retry
                        }
                    }
                }
                
                Log.d(TAG, "Tunnel stopped")
            } catch (e: Exception) {
                val errorMsg = "Error in tunnel: ${e.message}"
                Log.e(TAG, errorMsg, e)
                isRunning = false
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.close()
                    Log.d(TAG, "Tunnel streams closed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing streams", e)
                }
            }
        }
    }
    
    private fun isAdDomain(packet: ByteArray, length: Int): Boolean {
        try {
            if (length < 12) return false
            
            // Check if it's a DNS query (port 53)
            if (packet[2] == 0x01.toByte() && packet[3] == 0x00.toByte()) {
                // Parse domain name from DNS packet
                var offset = 12
                val domain = StringBuilder()
                
                while (offset < length && packet[offset] != 0x00.toByte()) {
                    val labelLength = packet[offset].toInt() and 0xFF
                    offset++
                    
                    if (offset + labelLength <= length) {
                        for (i in 0 until labelLength) {
                            domain.append(packet[offset + i].toChar())
                        }
                        offset += labelLength
                        domain.append('.')
                    }
                }
                
                val domainName = domain.toString().removeSuffix(".")
                Log.d(TAG, "Checking domain: $domainName")
                
                val isAdDomain = adDomains.any { adDomain ->
                    domainName.contains(adDomain, ignoreCase = true)
                }
                
                if (isAdDomain) {
                    Log.d(TAG, "Ad domain detected: $domainName")
                }
                
                return isAdDomain
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing packet", e)
        }
        return false
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ad Blocker VPN")
            .setContentText("VPN is running - Ads blocked: $adsBlocked")
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ad Blocker VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
    
    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }

    private fun hasVpnPermission(): Boolean {
        return try {
            // Try to prepare VPN service to check if we have permission
            val intent = VpnService.prepare(this)
            intent == null // If null, we already have permission
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPN permission", e)
            false
        }
    }
} 