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
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import kotlinx.coroutines.*

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
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    
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
        "adclick.g.doubleclick.net"
    )
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_VPN" -> startVpn()
            "STOP_VPN" -> stopVpn()
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        if (isRunning) return
        
        try {
            // Create VPN interface
            val builder = Builder()
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_SERVER)
                .addRoute(VPN_ROUTE, 0)
                .setSession("AdBlockerVPN")
            
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                isRunning = true
                startForeground(NOTIFICATION_ID, createNotification())
                startVpnThread()
                Log.d(TAG, "VPN started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        serviceJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }
    
    private fun startVpnThread() {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                
                val buffer = ByteBuffer.allocate(32767)
                
                while (isRunning) {
                    val length = inputStream.read(buffer.array())
                    if (length > 0) {
                        buffer.limit(length)
                        
                        // Process packet and check for ads
                        if (isAdDomain(buffer)) {
                            adsBlocked++
                            Log.d(TAG, "Ad blocked! Total: $adsBlocked")
                            continue
                        }
                        
                        // Forward legitimate traffic
                        outputStream.write(buffer.array(), 0, length)
                    }
                    buffer.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread", e)
            }
        }
    }
    
    private fun isAdDomain(packet: ByteBuffer): Boolean {
        // Simple DNS packet parsing to check domain names
        try {
            val data = packet.array()
            if (data.size < 12) return false
            
            // Check if it's a DNS query
            if (data[2] == 0x01.toByte() && data[3] == 0x00.toByte()) {
                // Parse domain name from DNS packet
                var offset = 12
                val domain = StringBuilder()
                
                while (offset < data.size && data[offset] != 0x00.toByte()) {
                    val length = data[offset].toInt() and 0xFF
                    offset++
                    
                    for (i in 0 until length) {
                        if (offset < data.size) {
                            domain.append(data[offset].toChar())
                            offset++
                        }
                    }
                    domain.append('.')
                }
                
                val domainName = domain.toString().removeSuffix(".")
                return adDomains.any { adDomain ->
                    domainName.contains(adDomain, ignoreCase = true)
                }
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
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ad Blocker VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
} 