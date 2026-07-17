package ti.android.app.router

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TiRouterService : Service() {
    @Inject lateinit var controller: TiRouterController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("TiRouter đang khởi động"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                controller.stopProcess()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> controller.restartProcess()
            else -> controller.startProcess()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        controller.stopProcess()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TiRouter", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Ti Android")
            .setContentText(text)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_START = "ti.android.action.START_ROUTER"
        const val ACTION_STOP = "ti.android.action.STOP_ROUTER"
        const val ACTION_RESTART = "ti.android.action.RESTART_ROUTER"
        private const val CHANNEL_ID = "ti-router-runtime"
        private const val NOTIFICATION_ID = 20128
    }
}
