package ai.openclaw.enhanced.controller

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import ai.openclaw.enhanced.model.AppLaunchParams
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

class AppController(private val context: Context) {
    
    suspend fun launchApp(params: AppLaunchParams): JsonObject {
        return try {
            Timber.i("Launching app: ${params.packageName}")
            
            val packageManager = context.packageManager
            
            // Check if app is installed
            val packageInfo = try {
                packageManager.getPackageInfo(params.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("App not installed: ${params.packageName}")
                ))
            }
            
            // Create launch intent
            val intent = if (params.activity != null) {
                // Launch specific activity
                Intent().apply {
                    setClassName(params.packageName, params.activity)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                // Launch main activity
                val launchIntent = packageManager.getLaunchIntentForPackage(params.packageName)
                    ?: return JsonObject(mapOf(
                        "success" to JsonPrimitive(false),
                        "error" to JsonPrimitive("No launch intent found for ${params.packageName}")
                    ))
                
                launchIntent.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            
            // Launch the app
            context.startActivity(intent)
            
            // Wait for app to launch if requested
            if (params.waitForLaunch) {
                delay(2000) // Give app time to launch
            }
            
            Timber.i("Successfully launched app: ${params.packageName}")
            JsonObject(mapOf(
                "success" to JsonPrimitive(true),
                "packageName" to JsonPrimitive(params.packageName),
                "activity" to JsonPrimitive(params.activity ?: "main")
            ))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch app: ${params.packageName}")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Launch failed: ${e.message}")
            ))
        }
    }
    
    fun getInstalledApps(): JsonObject {
        return try {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val appList = packages.filter { appInfo ->
                // Only return launchable apps
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }.map { appInfo ->
                JsonObject(mapOf(
                    "packageName" to JsonPrimitive(appInfo.packageName),
                    "appName" to JsonPrimitive(appInfo.loadLabel(packageManager).toString()),
                    "enabled" to JsonPrimitive(appInfo.enabled)
                ))
            }
            
            JsonObject(mapOf(
                "success" to JsonPrimitive(true),
                "apps" to JsonObject(appList.mapIndexed { index, app -> index.toString() to app }.toMap())
            ))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get installed apps")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Failed to get apps: ${e.message}")
            ))
        }
    }
    
    fun isAppRunning(packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            true // App is installed, more sophisticated checking would require different permissions
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}