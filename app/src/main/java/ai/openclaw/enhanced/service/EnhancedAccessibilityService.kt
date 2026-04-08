package ai.openclaw.enhanced.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

class EnhancedAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: EnhancedAccessibilityService? = null
        
        fun getInstance(): EnhancedAccessibilityService? = instance
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("EnhancedAccessibilityService connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("EnhancedAccessibilityService destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can log accessibility events here if needed for debugging
        // For now, we just use the service for gesture and UI interaction
    }
    
    override fun onInterrupt() {
        Timber.w("EnhancedAccessibilityService interrupted")
    }
}