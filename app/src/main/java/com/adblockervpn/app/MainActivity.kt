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
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
        }
    }
    
    private fun stopVpn() {
        val intent = Intent(this, AdBlockerVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        startService(intent)
        updateUI()
        Toast.makeText(this, "VPN stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun testVpnConnection() {
        if (!AdBlockerVpnService.isRunning) {
            Toast.makeText(this, "Please start VPN first", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Test internet connectivity
                val url = URL("https://www.google.com")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                
                Toast.makeText(this@MainActivity, "Internet working! VPN is active.", Toast.LENGTH_LONG).show()
                Log.d(TAG, "VPN connection test successful")
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Internet connection failed. Check VPN settings.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "VPN connection test failed", e)
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                val intent = Intent(this, AdBlockerVpnService::class.java).apply {
                    action = "START_VPN"
                }
                startService(intent)
                
                // Wait a bit for VPN to actually start
                lifecycleScope.launch {
                    delay(2000) // Wait 2 seconds
                    updateUI()
                    
                    if (AdBlockerVpnService.isRunning) {
                        Toast.makeText(this@MainActivity, "VPN started successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "VPN failed to start. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                Toast.makeText(this, "VPN failed to start: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateUI() {
        try {
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