package com.silentalarm.app.fire

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.silentalarm.app.R
import com.silentalarm.app.SilentAlarmApp
import com.silentalarm.app.data.AlarmRepository
import com.silentalarm.app.scheduling.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Holds the vibration and firing notification while one or more alarms are firing
 * (SPEC.md #5). A foreground service of type specialUse: exact alarms grant a temporary
 * exemption from the background foreground-service-start restriction that would otherwise
 * apply (SPEC.md Assumption A9).
 *
 * Tracks a *set* of currently-firing alarm ids rather than one at a time, because SPEC.md #5
 * defines a single Dismiss as clearing every alarm firing together, sharing one vibration
 * waveform and one notification.
 */
class AlarmService : Service() {

    private val firingAlarmIds = mutableSetOf<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private lateinit var vibrator: android.os.Vibrator
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureNotificationChannel()

        val graph = (application as SilentAlarmApp).graph
        repository = graph.repository
        scheduler = graph.scheduler
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FIRE -> handleFire(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
            ACTION_DISMISS -> handleDismissAll()
            ACTION_DISMISS_ONE -> handleDismissOne(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        cancelAutoStop()
        vibrator.cancel()
        super.onDestroy()
    }

    private fun handleFire(alarmId: Long) {
        if (alarmId < 0) return
        val isFirstAlarm = firingAlarmIds.isEmpty()
        firingAlarmIds += alarmId

        val notification = buildNotification()
        if (isFirstAlarm) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        // One waveform regardless of how many alarms are in the firing set (SPEC.md #5).
        startVibration()
        scheduleAutoStop()
    }

    /** The notification action and the on-screen Dismiss button both call this (SPEC.md #4). */
    private fun handleDismissAll() {
        val idsToReschedule = firingAlarmIds.toList()
        firingAlarmIds.clear()
        stopFiring()

        serviceScope.launch {
            for (id in idsToReschedule) {
                val alarm = repository.getById(id) ?: continue
                scheduler.schedule(alarm)
            }
        }
    }

    /**
     * Called when a *specific* firing alarm is disabled, deleted, or edited from the list
     * (SPEC.md #5, "Interactions while an alarm is firing"). Unlike [handleDismissAll], this
     * never reschedules - the caller (AlarmRepository) already did whatever is correct for
     * that mutation (cancel on delete/disable, a fresh schedule on edit).
     */
    private fun handleDismissOne(alarmId: Long) {
        if (!firingAlarmIds.remove(alarmId)) return
        if (firingAlarmIds.isEmpty()) {
            stopFiring()
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun stopFiring() {
        cancelAutoStop()
        vibrator.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startVibration() {
        // 1000ms on / 1000ms off, repeating from index 0, at the default (maximum) amplitude
        // (SPEC.md Assumption A7). USAGE_ALARM is what makes DND's "allow alarms" switch and
        // the ringer-mode rules apply correctly (SPEC.md tests T19-T22).
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0)
        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        vibrator.vibrate(effect, attributes)
    }

    private fun scheduleAutoStop() {
        // Only ever scheduled once, from the first alarm to join the firing set (SPEC.md
        // Assumption A6): a second alarm joining later doesn't extend the window.
        if (autoStopRunnable != null) return
        val runnable = Runnable { handleDismissAll() }
        autoStopRunnable = runnable
        handler.postDelayed(runnable, AUTO_STOP_MILLIS)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let(handler::removeCallbacks)
        autoStopRunnable = null
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // SPEC.md #4.4: sound is explicitly null. Vibration is owned by this service, not
            // the channel, so channel vibration is off.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dismissIntent = Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS)
        val dismissPendingIntent = PendingIntent.getService(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(getString(R.string.notification_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, getString(R.string.dismiss), dismissPendingIntent)
            .build()
    }

    companion object {
        const val ACTION_FIRE = "com.silentalarm.app.fire.ACTION_FIRE"
        const val ACTION_DISMISS = "com.silentalarm.app.fire.ACTION_DISMISS"
        private const val ACTION_DISMISS_ONE = "com.silentalarm.app.fire.ACTION_DISMISS_ONE"
        const val EXTRA_ALARM_ID = "com.silentalarm.app.extra.ALARM_ID"

        private const val CHANNEL_ID = "alarm_fire_v1"
        private const val NOTIFICATION_ID = 1
        private const val AUTO_STOP_MILLIS = 120_000L

        @Volatile private var instance: AlarmService? = null

        /**
         * Called by AlarmRepository when a specific alarm is disabled, deleted, or edited, so
         * that mutation also stops it if it happens to be firing right now. A no-op if that
         * alarm isn't currently firing, so it's safe to call unconditionally.
         */
        fun notifyAlarmChanged(context: Context, alarmId: Long) {
            if (instance?.firingAlarmIds?.contains(alarmId) != true) return
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_DISMISS_ONE)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            context.startService(intent)
        }
    }
}
