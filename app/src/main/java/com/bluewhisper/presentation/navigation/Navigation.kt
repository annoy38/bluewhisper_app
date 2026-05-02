package com.bluewhisper.presentation.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bluewhisper.domain.model.ReceivedFile
import com.bluewhisper.presentation.screens.chat.ChatScreen
import com.bluewhisper.presentation.screens.connection.ConnectionReceiverScreen
import com.bluewhisper.presentation.screens.connection.ConnectionSenderScreen
import com.bluewhisper.presentation.screens.disconnect.DisconnectScreen
import com.bluewhisper.presentation.screens.error.BluetoothErrorScreen
import com.bluewhisper.presentation.screens.fileviewer.FileViewerScreen
import com.bluewhisper.presentation.screens.home.HomeScreen
import com.bluewhisper.presentation.screens.onboarding.OnboardingScreen
import com.bluewhisper.presentation.screens.savedfiles.SavedFilesScreen
import com.bluewhisper.presentation.screens.settings.SettingsScreen

// ── Route constants ───────────────────────────────────────────────
object Routes {
    const val ONBOARDING      = "onboarding"
    const val HOME            = "home"
    const val CONNECTION_SEND = "connection_send/{endpointId}/{nickname}/{avatarId}"
    const val CONNECTION_RECV = "connection_recv"
    const val CHAT            = "chat"
    const val FILE_VIEWER     = "file_viewer"   // no arg — file passed via savedStateHandle
    const val DISCONNECT      = "disconnect"
    const val SETTINGS        = "settings"
    const val SAVED_FILES     = "saved_files"
    const val BT_ERROR        = "bt_error/{errorType}"

    fun connectionSend(endpointId: String, nickname: String, avatarId: Int) =
        "connection_send/$endpointId/$nickname/$avatarId"

    fun btError(errorType: String) = "bt_error/$errorType"
}

enum class BTErrorType {
    BLUETOOTH_OFF,
    PERMISSION_DENIED,
    BLE_NOT_SUPPORTED,
    BLUETOOTH_ERROR
}

@Composable
fun BlueWhisperNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        // ── Onboarding ────────────────────────────────────────────
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ── Home ──────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onChatNowClicked = { endpointId, nickname, avatarId ->
                    navController.navigate(Routes.connectionSend(endpointId, nickname, avatarId))
                },
                onSettingsClicked = { navController.navigate(Routes.SETTINGS) },
                onBluetoothError  = { navController.navigate(Routes.btError(it.name)) },
                onIncomingRequest = { navController.navigate(Routes.CONNECTION_RECV) }
            )
        }

        // ── Connection sender ─────────────────────────────────────
        composable(Routes.CONNECTION_SEND) { backStackEntry ->
            val endpointId = backStackEntry.arguments?.getString("endpointId") ?: ""
            val nickname   = backStackEntry.arguments?.getString("nickname") ?: ""
            val avatarId   = backStackEntry.arguments?.getString("avatarId")?.toIntOrNull() ?: 1
            ConnectionSenderScreen(
                endpointId = endpointId,
                nickname   = nickname,
                avatarId   = avatarId,
                onConnected  = {
                    navController.navigate(Routes.CHAT) { popUpTo(Routes.HOME) }
                },
                onCancelled  = { navController.navigateUp() }
            )
        }

        // ── Connection receiver ───────────────────────────────────
        composable(Routes.CONNECTION_RECV) {
            ConnectionReceiverScreen(
                onAccepted = {
                    navController.navigate(Routes.CHAT) { popUpTo(Routes.HOME) }
                },
                onDeclined = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // ── Chat ──────────────────────────────────────────────────
        composable(Routes.CHAT) { backStackEntry ->
            // FR-07.4: ChatViewModel is scoped to CHAT entry — countdown lives here
            val chatViewModel: com.bluewhisper.presentation.screens.chat.ChatViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(backStackEntry)
            ChatScreen(
                onDisconnect = {
                    navController.navigate(Routes.DISCONNECT) { popUpTo(Routes.HOME) }
                },
                onViewFile = { file ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("receivedFile", file)
                    navController.navigate(Routes.FILE_VIEWER)
                },
                viewModel = chatViewModel
            )
        }

        // ── File Viewer ───────────────────────────────────────────
        composable(Routes.FILE_VIEWER) { backStackEntry ->
            val chatEntry = navController.getBackStackEntry(Routes.CHAT)
            val file = chatEntry.savedStateHandle.get<ReceivedFile>("receivedFile")
            // FR-07.4: pass ChatViewModel (CHAT-scoped) so countdown survives viewer close
            val chatViewModel: com.bluewhisper.presentation.screens.chat.ChatViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(chatEntry)

            if (file != null) {
                FileViewerScreen(
                    file         = file,
                    onClose      = { navController.navigateUp() },
                    onSaved      = { navController.navigateUp() },
                    onVanished   = { navController.navigateUp() },
                    chatViewModel = chatViewModel,
                    onStateChanged = { fileId, newState ->
                        chatEntry.savedStateHandle["fileStateUpdate"] =
                            "$fileId:${newState.name}"
                    }
                )
            } else {
                LaunchedEffect(Unit) { navController.navigateUp() }
            }
        }

        // ── Disconnect animation ──────────────────────────────────
        composable(Routes.DISCONNECT) {
            DisconnectScreen(
                onAnimationComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateUp      = { navController.navigateUp() },
                onSavedFilesClicked = { navController.navigate(Routes.SAVED_FILES) }
            )
        }

        // ── Saved Files ───────────────────────────────────────────
        composable(Routes.SAVED_FILES) {
            SavedFilesScreen(onNavigateUp = { navController.navigateUp() })
        }

        // ── Bluetooth Error ───────────────────────────────────────
        composable(Routes.BT_ERROR) { backStackEntry ->
            val errorType = backStackEntry.arguments
                ?.getString("errorType")
                ?.let { BTErrorType.valueOf(it) }
                ?: BTErrorType.BLUETOOTH_OFF
            BluetoothErrorScreen(
                errorType = errorType,
                onRetry   = { navController.navigateUp() }
            )
        }
    }
}
