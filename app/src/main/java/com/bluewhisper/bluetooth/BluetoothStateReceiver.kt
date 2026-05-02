package com.bluewhisper.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FR-08.2 Trigger 3: BT turned off mid-session
 * Listens for BluetoothAdapter.ACTION_STATE_CHANGED and triggers disconnect+wipe
 * when Bluetooth transitions to STATE_TURNING_OFF or STATE_OFF while in a session.
 */
@AndroidEntryPoint
class BluetoothStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var nearbyConnectionsManager: NearbyConnectionsManager

    companion object {
        private const val TAG = "BlueWhisper_BTReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(
            BluetoothAdapter.EXTRA_STATE,
            BluetoothAdapter.ERROR
        )

        when (state) {
            BluetoothAdapter.STATE_TURNING_OFF,
            BluetoothAdapter.STATE_OFF -> {
                Log.d(TAG, "Bluetooth turning off — triggering wipe (FR-08.2)")
                // NearbyConnectionsManager will fire onDisconnected callback
                // which triggers the full wipe chain in ChatViewModel
                nearbyConnectionsManager.stopAll()
            }
            BluetoothAdapter.STATE_ON -> {
                Log.d(TAG, "Bluetooth turned on — app can resume scanning")
                // HomeScreen's LaunchedEffect will restart scanning automatically
            }
        }
    }
}
