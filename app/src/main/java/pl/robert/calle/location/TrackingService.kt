package pl.robert.calle.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.LocationRequest as PlatformLocationRequest
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.robert.calle.CalleApp
import pl.robert.calle.MainActivity
import pl.robert.calle.R
import pl.robert.calle.db.WalkedWayEntity
import pl.robert.calle.graph.GraphHolder
import pl.robert.calle.graph.LatLon
import pl.robert.calle.graph.StreetSnapper

class TrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationManager: LocationManager
    private lateinit var snapper: StreetSnapper

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        snapper = StreetSnapper(GraphHolder.get(this))
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
        }
        startInForeground()
        TrackingHub.setTracking(true)
        requestUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        stopUpdates()
        TrackingHub.setTracking(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 45f) return
        val raw = LatLon(lat = location.latitude, lon = location.longitude)
        val snap = snapper.snap(raw, TrackingHub.lastWayId)
        TrackingHub.publish(raw, snap)
        val way = snap?.hit?.way ?: return
        scope.launch {
            val dao = (application as CalleApp).database.walkedWayDao()
            dao.mark(
                WalkedWayEntity(
                    wayId = way.id,
                    markedAtEpochMs = System.currentTimeMillis(),
                    source = "gps",
                ),
            )
        }
    }

    private fun requestUpdates() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val request = PlatformLocationRequest.Builder(2_000L)
                    .setMinUpdateIntervalMillis(1_000L)
                    .setQuality(PlatformLocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateDistanceMeters(1.5f)
                    .build()
                val providers = buildList {
                    add(LocationManager.GPS_PROVIDER)
                    if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
                }
                for (provider in providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, request, mainExecutor, this)
                    }
                }
            } else {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    @Suppress("DEPRECATION")
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1_500L,
                        1.5f,
                        this,
                    )
                }
            }
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun stopUpdates() {
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
            // Already lost permission.
        }
    }

    private fun stopTracking() {
        stopUpdates()
        TrackingHub.setTracking(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_STOP = "pl.robert.calle.STOP_TRACKING"
        private const val CHANNEL_ID = "calle_tracking"
        private const val NOTIFICATION_ID = 17

        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
