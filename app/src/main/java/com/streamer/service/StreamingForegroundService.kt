package com.streamer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.streamer.ui.StreamerActivity

class StreamingForegroundService : Service {
    constructor() : super()

    companion object {
        private const val CHANNEL_ID = "streamer_service_channel"
        private const val NOTIFICATION_ID = 4589
        
        const val ACTION_START = "ACTION_START_FOREGROUND"
        const val ACTION_STOP = "ACTION_STOP_FOREGROUND"
        
        fun startService(context: Context, mediaProjectionData: Intent) {
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra("projectionData", mediaProjectionData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("StreamingService", "Foreground Service onCreate initialized.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("StreamingService", "onStartCommand Action: $action")
        
        if (action == ACTION_START) {
            showNotificationAndStartForeground()
        } else if (action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            // Broad-cast stop action to activity
            sendBroadcast(Intent("com.streamer.ACTION_STOP_CAPTURE"))
        }
        
        return START_NOT_STICKY
    }

    private fun showNotificationAndStartForeground() {
        val notificationIntent = Intent(this, StreamerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, StreamingForegroundService::class.java).apply {
            this.action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StreamerApp البث المباشر نشط")
            .setContentText("يتم الآن بث الشاشة والكاميرات بلحظية فائقة.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف البث", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 (API 29)+, include mediaProjection and camera foreground types
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("StreamingService", "Service successfully moved to foreground.")
        } catch (e: Exception) {
            Log.e("StreamingService", "Failed to start foreground service: ${e.message}", e)
            // Fallback
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fallbackEx: Exception) {
                Log.e("StreamingService", "Critial fallback failed: ${fallbackEx.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Streamer Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة خدمة بث الشاشة والكاميرات"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.d("StreamingService", "Notification Channel registered.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("StreamingService", "Foreground Service destroyed.")
    }
}
