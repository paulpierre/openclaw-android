package ai.openclaw.enhanced

import android.app.Application
import ai.openclaw.enhanced.BuildConfig
import timber.log.Timber

class EnhancedNodeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("OpenClaw Enhanced Node Application starting...")
    }
}