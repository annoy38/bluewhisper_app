package com.bluewhisper.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bluewhisper.bluetooth.BTEvent
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * FR-09.1: Foreground service keeps BT scanning alive when app is backgrounded.
 * FR-09.2: Shows persistent "Scanning nearby..." notification.
 * FR-09.3: Shows alert when connection request arrives in background.
 * FR-09.4: Tapping request notification opens accept/decline screen.
 * FR-09.5: Shows notification when file received in background.
 *
 * CRITICAL FIX: This service owns startDiscovery/startAdvertising so that
 * scanning continues even when HomeScreen is not composed (app backgrounded).
 */
@AndroidEntryPoint
class BluetoothForegroundService : Service() {

    @Inject lateinit var nearbyManager: NearbyConnectionsManager
    @Inject lateinit var userProfileDataStore: UserProfileDataStore

    companion object {
        const val SCANNING_CHANNEL_ID   = "bw_scanning"
        const val ALERT_CHANNEL_ID      = "bw_alerts"
        const val SCANNING_NOTIF_ID     = 1001
        const val REQUEST_NOTIF_ID      = 1002
        const val FILE_NOTIF_ID         = 1003
        const val ACTION_START                 = "com.bluewhisper.START"
        const val ACTION_STOP                  = "com.bluewhisper.STOP"
        const val ACTION_STOP_SCANNING         = "com.bluewhisper.STOP_SCANNING"
        const val ACTION_START_SCANNING        = "com.bluewhisper.START_SCANNING"
        // Tier 3 #12 (B-04): discoverability is owned by the service.
        // HomeViewModel sends these intents; the service is the only caller of
        // startAdvertising / stopAdvertising.
        const val ACTION_BECOME_DISCOVERABLE   = "com.bluewhisper.BECOME_DISCOVERABLE"
        const val ACTION_BECOME_INVISIBLE      = "com.bluewhisper.BECOME_INVISIBLE"
        const val EXTRA_OPEN_REQUEST           = "open_incoming_request"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START               -> { startForegroundNotification(); startScanningIfNeeded(); observeEvents() }
            ACTION_STOP                -> stopSelf()
            // HomeViewModel calls these to hand off / take back scanning ownership
            ACTION_STOP_SCANNING       -> stopScanningInternal()
            ACTION_START_SCANNING      -> startScanningIfNeeded()
            // Tier 3 #12 (B-04): the service owns advertise/stopAdvertise.
            ACTION_BECOME_DISCOVERABLE -> setDiscoverableInternal(true)
            ACTION_BECOME_INVISIBLE    -> setDiscoverableInternal(false)
        }
        return START_STICKY
    }

    private fun setDiscoverableInternal(visible: Boolean) {
        serviceScope.launch {
            try {
                if (visible) {
                    val profile = userProfileDataStore.userProfile.first()
                    val name = nearbyManager.formatAdvertisingName(
                        profile.nickname, profile.avatarId
                    )
                    withContext(Dispatchers.Main) { nearbyManager.startAdvertising(name) }
                } else {
                    withContext(Dispatchers.Main) { nearbyManager.stopAdvertising() }
                }
            } catch (e: Exception) {
                android.util.Log.e("BWService", "setDiscoverable failed: ${e.message}")
            }
        }
    }

    // ── FR-09.1: start BT scanning in the service ─────────────────
    private fun startScanningIfNeeded() {
        if (isScanning) return
        serviceScope.launch {
            try {
                val profile = userProfileDataStore.userProfile.first()
                if (!profile.isDiscoverable) return@launch
                val advertisingName = nearbyManager.formatAdvertisingName(
                    profile.nickname, profile.avatarId
                )
                withContext(Dispatchers.Main) {
                    nearbyManager.startAdvertising(advertisingName)
                    nearbyManager.startDiscovery()
                }
                isScanning = true
            } catch (e: Exception) {
                android.util.Log.e("BWService", "startScanning failed: ${e.message}")
            }
        }
    }

    private fun stopScanningInternal() {
        if (!isScanning) return
        nearbyManager.stopDiscovery()
        nearbyManager.stopAdvertising()
        isScanning = false
    }

    // ── FR-09.3/09.5: observe BT events ──────────────────────────
    private fun observeEvents() {
        serviceScope.launch {
            nearbyManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.IncomingRequest -> {
                        // FR-09.3: show notification for background request
                        showRequestNotification(state.requesterNickname)
                    }
                    is ConnectionState.Connected -> {
                        // Connected — stop scanning (FR-02.7), cancel request notif
                        stopScanningInternal()
                        cancelRequestNotification()
                    }
                    is ConnectionState.Idle -> {
                        // Session ended — restart scanning
                        cancelRequestNotification()
                        cancelFileNotification()
                        startScanningIfNeeded()
                    }
                    else -> Unit
                }
            }
        }

        serviceScope.launch {
            nearbyManager.events.collect { event ->
                if (event is BTEvent.Disconnected) {
                    cancelRequestNotification()
                    cancelFileNotification()
                    // Resume scanning after disconnect
                    isScanning = false
                    startScanningIfNeeded()
                }
            }
        }

        serviceScope.launch {
            nearbyManager.filePayloads.collect { _ ->
                // FR-09.5: file payload arrived while backgrounded
                val senderNickname = (nearbyManager.connectionState.value
                    as? ConnectionState.Connected)
                    ?.session?.remoteNickname ?: "Someone"
                showFileReceivedNotification(senderNickname)
            }
        }
    }

    // ── FR-09.2: Persistent scanning notification ─────────────────
    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SCANNING_CHANNEL_ID)
            .setContentTitle("BlueWhisper")
            .setContentText("🔵 Looking for people nearby...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(SCANNING_NOTIF_ID, notification)
    }

    // ── FR-09.3: Connection request notification ──────────────────
    private fun showRequestNotification(requesterNickname: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_REQUEST, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, REQUEST_NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Incoming Chat Request")
            .setContentText("$requesterNickname wants to chat with you!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // FR-09.4: wake screen
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()
        NotificationManagerCompat.from(this).notify(REQUEST_NOTIF_ID, notification)
    }

    // ── FR-09.5: File received notification ──────────────────────
    private fun showFileReceivedNotification(senderNickname: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, FILE_NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("File Received")
            .setContentText("$senderNickname sent you a file — 10 seconds to open!")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(FILE_NOTIF_ID, notification)
    }

    private fun cancelRequestNotification() =
        NotificationManagerCompat.from(this).cancel(REQUEST_NOTIF_ID)

    private fun cancelFileNotification() =
        NotificationManagerCompat.from(this).cancel(FILE_NOTIF_ID)

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(SCANNING_CHANNEL_ID, "BlueWhisper Scanning",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background Bluetooth scanning"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "BlueWhisper Alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Connection requests and file notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
