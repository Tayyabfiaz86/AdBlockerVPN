package com.adblockervpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adblockervpn.app.databinding.ActivityMainBinding
import com.adblockervpn.app.vpn.AdBlockerVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URL

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val VPN_REQUEST_CODE = 1
    }
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        startStatusUpdate()
    }
    
    private fun setupUI() {
        binding.toggleButton.setOnClickListener {
            if (AdBlockerVpnService.isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }
        
        binding.settingsButton.setOnClickListener {
            // Test VPN functionality
            testVpnConnection()
        }
        
        updateUI()
    }
    
    private fun startVpn() {
        try {
            Log.d(TAG, "Starting VPN preparation...")
            val intent = VpnService.prepare(this)
            if (intent != null) {
                Log.d(TAG, "VPN permission needed, launching permission dialog")
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                Log.d(TAG, "VPN permission already granted, proceeding directly")
                onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing VPN", e)
            val errorMsg = "VPN preparation failed: ${e.message}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Full error details", e)
        }
    }
    
    private fun stopVpn() {
        try {
            Log.d(TAG, "Stopping VPN...")
            val intent = Intent(this, AdBlockerVpnService::class.java).apply {
                action = "STOP_VPN"
            }
            startService(intent)
            updateUI()
            Toast.makeText(this, "VPN stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
            Toast.makeText(this, "Error stopping VPN: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testVpnConnection() {
        if (!AdBlockerVpnService.isRunning) {
            val statusInfo = checkVpnServiceStatus()
            val errorMsg = "Please start VPN first. Current status: $statusInfo"
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Testing VPN connection...")
                // Test internet connectivity
                val url = URL("https://www.google.com")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                
                Toast.makeText(this@MainActivity, "Internet working! VPN is active.", Toast.LENGTH_LONG).show()
                Log.d(TAG, "VPN connection test successful")
                
            } catch (e: Exception) {
                val statusInfo = checkVpnServiceStatus()
                val errorMsg = "Internet connection failed: ${e.message}. VPN Status: $statusInfo"
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                Log.e(TAG, "VPN connection test failed", e)
                Log.e(TAG, "VPN Status during test: $statusInfo")
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    Log.d(TAG, "VPN permission granted, starting service...")
                    val intent = Intent(this, AdBlockerVpnService::class.java).apply {
                        action = "START_VPN"
                    }
                    startService(intent)
                    
                    // Wait a bit for VPN to actually start
                    lifecycleScope.launch {
                        delay(1000) // Wait 1 second first
                        updateUI()
                        
                        if (AdBlockerVpnService.isRunning) {
                            Log.d(TAG, "VPN is running")
                            Toast.makeText(this@MainActivity, "VPN started successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d(TAG, "VPN is not running, waiting more...")
                            delay(2000) // Wait 2 more seconds
                            updateUI()
                            
                            if (AdBlockerVpnService.isRunning) {
                                Log.d(TAG, "VPN started after delay")
                                Toast.makeText(this@MainActivity, "VPN started successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.d(TAG, "VPN failed to start - service not running")
                                val statusInfo = checkVpnServiceStatus()
                                val errorMsg = "VPN failed to start. Status: $statusInfo. Check logs for details."
                                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                                Log.e(TAG, "VPN failed to start. Status: $statusInfo")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting VPN", e)
                    val errorMsg = "VPN failed to start: ${e.message}"
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Full error details", e)
                }
            } else {
                Log.d(TAG, "VPN permission denied by user")
                Toast.makeText(this, "VPN permission denied by user", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun checkVpnServiceStatus(): String {
        return try {
            val statusInfo = AdBlockerVpnService.getDetailedStatus()
            Log.d(TAG, "VPN Service Status: $statusInfo")
            statusInfo
        } catch (e: Exception) {
            val errorMsg = "Error checking VPN status: ${e.message}"
            Log.e(TAG, errorMsg, e)
            errorMsg
        }
    }
    
    private fun updateUI() {
        try {
            Log.d(TAG, "Updating UI, VPN running: ${AdBlockerVpnService.isRunning}")
            
            if (AdBlockerVpnService.isRunning) {
                binding.toggleButton.text = getString(R.string.stop_vpn)
                binding.statusText.text = getString(R.string.vpn_connected)
                binding.statusText.setTextColor(getColor(R.color.connected))
                Log.d(TAG, "VPN is running")
            } else {
                binding.toggleButton.text = getString(R.string.start_vpn)
                binding.statusText.text = getString(R.string.vpn_disconnected)
                binding.statusText.setTextColor(getColor(R.color.disconnected))
                Log.d(TAG, "VPN is not running")
            }
            
            // Update ads blocked counter
            val adsBlocked = AdBlockerVpnService.adsBlocked
            binding.adsBlockedText.text = adsBlocked.toString()
            Log.d(TAG, "Ads blocked: $adsBlocked")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
            // Set default values on error
            binding.toggleButton.text = getString(R.string.start_vpn)
            binding.statusText.text = getString(R.string.vpn_disconnected)
            binding.statusText.setTextColor(getColor(R.color.disconnected))
            binding.adsBlockedText.text = "0"
        }
    }
    
    private fun startStatusUpdate() {
        lifecycleScope.launch {
            while (true) {
                try {
                    updateUI()
                    delay(500) // Update every 500ms for better responsiveness
                } catch (e: Exception) {
                    Log.e(TAG, "Error in status update", e)
                    delay(1000) // Longer delay on error
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onPause() {
        super.onPause()
        // Keep UI updates running in background
    }
} 