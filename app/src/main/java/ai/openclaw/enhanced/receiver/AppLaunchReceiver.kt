package ai.openclaw.enhanced.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class AppLaunchReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_LAUNCH_APP = "ai.openclaw.enhanced.LAUNCH_APP"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY = "activity"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LAUNCH_APP -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val activity = intent.getStringExtra(EXTRA_ACTIVITY)
                
                if (packageName != null) {
                    launchApp(context, packageName, activity)
                } else {
                    Timber.w("Received launch intent without package name")
                }
            }
        }
    }
    
    private fun launchApp(context: Context, packageName: String, activity: String?) {
        try {
            Timber.i("Launching app via broadcast: $packageName")
            
            val launchIntent = if (activity != null) {
                Intent().apply {
                    setClassName(packageName, activity)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Timber.i("Successfully launched app: $packageName")
            } else {
                Timber.w("Could not create launch intent for: $packageName")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch app: $packageName")
        }
    }
}