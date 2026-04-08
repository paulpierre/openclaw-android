package ai.openclaw.enhanced.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ai.openclaw.enhanced.databinding.ActivityMainBinding
import ai.openclaw.enhanced.service.EnhancedAccessibilityService
import ai.openclaw.enhanced.service.NodeService
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun setupUI() {
        binding.btnStartService.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                startNodeService()
            } else {
                showAccessibilityPrompt()
            }
        }
        
        binding.btnStopService.setOnClickListener {
            stopNodeService()
        }
        
        binding.btnAccessibilitySettings.setOnClickListener {
            openAccessibilitySettings()
        }
        
        binding.btnOverlaySettings.setOnClickListener {
            openOverlaySettings()
        }
        
        updateStatus()
    }
    
    private fun updateStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val serviceRunning = isNodeServiceRunning()
        
        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) "✓ Enabled" else "✗ Disabled"
        binding.tvOverlayStatus.text = if (overlayEnabled) "✓ Enabled" else "✗ Disabled"
        binding.tvServiceStatus.text = if (serviceRunning) "✓ Running" else "✗ Stopped"
        
        binding.btnStartService.isEnabled = accessibilityEnabled && overlayEnabled && !serviceRunning
        binding.btnStopService.isEnabled = serviceRunning
        
        if (accessibilityEnabled && overlayEnabled) {
            binding.tvInstructions.text = "All permissions granted! You can start the node service."
        } else {
            binding.tvInstructions.text = "Please enable required permissions to use OpenClaw Enhanced Node."
        }
    }
    
    private fun checkPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityPrompt()
        } else if (!Settings.canDrawOverlays(this)) {
            showOverlayPrompt()
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        return EnhancedAccessibilityService.getInstance() != null
    }
    
    private fun isNodeServiceRunning(): Boolean {
        // Simple check - in a production app you'd want a more robust way to check service state
        return false // TODO: Implement proper service state checking
    }
    
    private fun startNodeService() {
        try {
            val intent = Intent(this, NodeService::class.java)
            startForegroundService(intent)
            
            Toast.makeText(this, "OpenClaw Enhanced Node service started", Toast.LENGTH_SHORT).show()
            Timber.i("Started NodeService from MainActivity")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start NodeService")
            Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        updateStatus()
    }
    
    private fun stopNodeService() {
        try {
            val intent = Intent(this, NodeService::class.java)
            stopService(intent)
            
            Toast.makeText(this, "OpenClaw Enhanced Node service stopped", Toast.LENGTH_SHORT).show()
            Timber.i("Stopped NodeService from MainActivity")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop NodeService")
            Toast.makeText(this, "Failed to stop service: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        updateStatus()
    }
    
    private fun showAccessibilityPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage("OpenClaw Enhanced Node requires accessibility service to control apps and input. Please enable it in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showOverlayPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("OpenClaw Enhanced Node needs permission to display over other apps for UI automation.")
            .setPositiveButton("Open Settings") { _, _ ->
                openOverlaySettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open accessibility settings")
            Toast.makeText(this, "Failed to open accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openOverlaySettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open overlay settings")
            Toast.makeText(this, "Failed to open overlay settings", Toast.LENGTH_SHORT).show()
        }
    }
}