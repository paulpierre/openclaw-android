package ai.openclaw.enhanced.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.openclaw.enhanced.R
import ai.openclaw.enhanced.controller.AppController
import ai.openclaw.enhanced.controller.InputController
import ai.openclaw.enhanced.controller.UIController
import ai.openclaw.enhanced.gateway.GatewayConnection
import ai.openclaw.enhanced.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

class NodeService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "openclaw_enhanced_node"
        private const val CHANNEL_NAME = "OpenClaw Enhanced Node"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var gatewayConnection: GatewayConnection
    private lateinit var appController: AppController
    private lateinit var inputController: InputController
    private lateinit var uiController: UIController

    override fun onCreate() {
        super.onCreate()
        Timber.i("NodeService onCreate")
        
        createNotificationChannel()
        
        // Initialize controllers
        appController = AppController(this)
        inputController = InputController(this)
        uiController = UIController(this)
        
        // Initialize gateway connection
        gatewayConnection = GatewayConnection(
            context = this,
            appController = appController,
            inputController = inputController,
            uiController = uiController,
            scope = serviceScope
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("NodeService onStartCommand")
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Connect to OpenClaw Gateway
        gatewayConnection.connect()
        
        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("NodeService onDestroy")
        
        gatewayConnection.disconnect()
        serviceScope.cancel()
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OpenClaw Enhanced Node background service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenClaw Enhanced Node")
            .setContentText("Connected to OpenClaw Gateway")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}