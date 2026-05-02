package com.bluewhisper.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.presentation.navigation.BlueWhisperNavHost
import com.bluewhisper.presentation.navigation.Routes
import com.bluewhisper.presentation.theme.BlueWhisperTheme
import com.bluewhisper.service.BluetoothForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userProfileDataStore: UserProfileDataStore
    @Inject lateinit var nearbyManager: NearbyConnectionsManager

    private var navController: NavHostController? = null

    // NFR-01.4 FIX: async splash hold instead of runBlocking. Both fields are
    // mutableStateOf so setContent { } recomposes when they update.
    private val startDestinationState = mutableStateOf<String?>(null)
    // Tier 4 #24 (B-19): make splashDone Compose-observable. setKeepOnScreenCondition
    // polls each frame, but if Compose ever takes over the gate we want a real
    // observation hook here, not a plain Boolean.
    private val splashDoneState = mutableStateOf(false)

    // Tier 4 #25 (B-18): if a notification deep-link arrives before the
    // NavController is ready, buffer it and replay once Compose mounts.
    private val pendingDeepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // NFR-01.4: read DataStore async while splash is visible — no main thread block
        splashScreen.setKeepOnScreenCondition { !splashDoneState.value }

        lifecycleScope.launch {
            val isOnboardingComplete = userProfileDataStore.isOnboardingComplete.first()
            startDestinationState.value =
                if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
            splashDoneState.value = true
        }

        setContent {
            BlueWhisperTheme {
                val destination = startDestinationState.value
                if (destination != null) {
                    val nc = rememberNavController()
                    SideEffect { navController = nc }

                    // Replay any deep-link that arrived before NavController mounted.
                    LaunchedEffect(nc, pendingDeepLink.value) {
                        pendingDeepLink.value?.let { link ->
                            applyDeepLink(nc, link)
                            pendingDeepLink.value = null
                        }
                    }

                    BlueWhisperNavHost(
                        navController    = nc,
                        startDestination = destination
                    )
                }
            }
        }

        startBluetoothService()
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    // FR-09.4: notification tap opens accept/decline screen directly.
    private fun handleNotificationIntent(intent: Intent?) {
        val openRequest = intent?.getBooleanExtra(
            BluetoothForegroundService.EXTRA_OPEN_REQUEST, false
        ) ?: false
        if (!openRequest) return

        val link = DeepLink.IncomingRequest
        val nc = navController
        if (nc != null) {
            applyDeepLink(nc, link)
        } else {
            // NavController not ready yet (cold start path) — buffer and replay.
            pendingDeepLink.value = link
        }
    }

    private fun applyDeepLink(nc: NavHostController, link: DeepLink) {
        when (link) {
            DeepLink.IncomingRequest -> {
                if (nearbyManager.connectionState.value !is ConnectionState.IncomingRequest) return
                runOnUiThread {
                    try {
                        nc.navigate(Routes.CONNECTION_RECV) { launchSingleTop = true }
                    } catch (e: IllegalStateException) {
                        // NavController detached mid-navigation — re-buffer for next mount.
                        pendingDeepLink.value = link
                    }
                }
            }
        }
    }

    private fun startBluetoothService() {
        startForegroundService(
            Intent(this, BluetoothForegroundService::class.java).apply {
                action = BluetoothForegroundService.ACTION_START
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        navController = null
    }

    private sealed interface DeepLink {
        object IncomingRequest : DeepLink
    }
}
