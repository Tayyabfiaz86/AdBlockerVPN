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
            // TODO: Implement settings activity
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show()
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
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, AdBlockerVpnService::class.java).apply {
                action = "START_VPN"
            }
            startService(intent)
            updateUI()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateUI() {
        if (AdBlockerVpnService.isRunning) {
            binding.toggleButton.text = getString(R.string.stop_vpn)
            binding.statusText.text = getString(R.string.vpn_connected)
            binding.statusText.setTextColor(getColor(R.color.connected))
        } else {
            binding.toggleButton.text = getString(R.string.start_vpn)
            binding.statusText.text = getString(R.string.vpn_disconnected)
            binding.statusText.setTextColor(getColor(R.color.disconnected))
        }
        
        binding.adsBlockedText.text = AdBlockerVpnService.adsBlocked.toString()
    }
    
    private fun startStatusUpdate() {
        lifecycleScope.launch {
            while (true) {
                updateUI()
                delay(1000) // Update every second
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
} 