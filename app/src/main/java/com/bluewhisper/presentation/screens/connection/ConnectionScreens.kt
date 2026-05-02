package com.bluewhisper.presentation.screens.connection

import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.presentation.screens.home.AvatarCircle
import com.bluewhisper.presentation.theme.*

// ── SENDER SCREEN ─────────────────────────────────────────────────
@Composable
fun ConnectionSenderScreen(
    endpointId: String,
    nickname: String,
    avatarId: Int,
    onConnected: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val timeoutLeft by viewModel.timeoutSecondsLeft.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startSenderTimeout()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectionUiEvent.Connected -> onConnected()
                is ConnectionUiEvent.Rejected,
                is ConnectionUiEvent.Timeout -> onCancelled()
                is ConnectionUiEvent.Error -> onCancelled()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BWColors.Background)
            .padding(Spacing.base)
    ) {
        // Cancel button top-left
        TextButton(
            onClick = {
                viewModel.cancelRequest()
                onCancelled()
            },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text("← Cancel", color = BWColors.Red)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Text(
                "Connecting to...",
                style = MaterialTheme.typography.bodyLarge,
                color = BWColors.TextSecondary
            )

            // Pulsing avatar
            PulsingAvatar(avatarId = avatarId, size = 96)

            Text(
                text = nickname,
                style = MaterialTheme.typography.displayLarge,
                color = BWColors.OnBackground,
                fontWeight = FontWeight.Bold
            )

            // Status card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
                border = BorderStroke(1.dp, BWColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        "Waiting for $nickname to accept...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BWColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    AnimatedDots()
                }
            }

            // Countdown arc
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { timeoutLeft / 30f },
                    modifier = Modifier.size(72.dp),
                    color = if (timeoutLeft > 10) BWColors.Blue else BWColors.Amber,
                    trackColor = BWColors.Border,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${timeoutLeft}s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            // Cancel button
            OutlinedButton(
                onClick = {
                    viewModel.cancelRequest()
                    onCancelled()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BWColors.Red)
            ) {
                Text("✕ Cancel Request", color = BWColors.Red)
            }
        }
    }
}

// ── RECEIVER SCREEN ───────────────────────────────────────────────
@Composable
fun ConnectionReceiverScreen(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val timeoutLeft by viewModel.timeoutSecondsLeft.collectAsState()

    // Get request info from state
    val requestInfo = (connectionState as? ConnectionState.IncomingRequest)

    LaunchedEffect(requestInfo) {
        requestInfo?.let {
            viewModel.startReceiverTimeout(it.endpointId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectionUiEvent.Connected -> onAccepted()
                is ConnectionUiEvent.Timeout -> onDeclined()
                else -> onDeclined()
            }
        }
    }

    if (requestInfo == null) return

    val context = androidx.compose.ui.platform.LocalContext.current

    // FR-03.2: Sound alert on incoming request
    LaunchedEffect(Unit) {
        try {
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION
            )
            val ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
        } catch (_: Exception) { /* device may have no sound */ }
    }

    // FR-03.2: Haptic vibration on incoming request
    LaunchedEffect(requestInfo) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.os.VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        // Repeat double vibration every 2.5 seconds until dismissed
        repeat(12) {
            vibrator?.let { v ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 180, 100, 180), -1)
                }
            }
            delay(2500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BWColors.Background.copy(alpha = 0.95f))
            .padding(Spacing.base),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier = Modifier.fillMaxWidth()
        ) {

            // Incoming pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D2B5E))
                    .border(1.dp, BWColors.Blue, RoundedCornerShape(20.dp))
                    .padding(horizontal = Spacing.base, vertical = Spacing.sm)
            ) {
                Text(
                    "🔵 Incoming Chat Request",
                    style = MaterialTheme.typography.labelMedium,
                    color = BWColors.Blue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Pulsing avatar
            PulsingAvatar(avatarId = requestInfo.requesterAvatarId, size = 96)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = requestInfo.requesterNickname,
                    style = MaterialTheme.typography.displayLarge,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "wants to chat!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BWColors.TextSecondary
                )
                Text(
                    text = "📍 Nearby",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BWColors.Teal
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // Accept button
            Button(
                onClick = {
                    viewModel.acceptConnection(requestInfo.endpointId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(BWColors.Green, BWColors.Teal)),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✅ Accept & Chat",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Decline button
            OutlinedButton(
                onClick = {
                    viewModel.declineConnection(requestInfo.endpointId)
                    onDeclined()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BWColors.Red)
            ) {
                Text("✕ Not Now", color = BWColors.Red, fontWeight = FontWeight.SemiBold)
            }

            // Countdown bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                LinearProgressIndicator(
                    progress = { timeoutLeft / 30f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (timeoutLeft > 10) BWColors.Green else BWColors.Orange,
                    trackColor = BWColors.Border
                )
                Text(
                    "Auto-closes in ${timeoutLeft}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.TextTertiary
                )
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────
@Composable
fun PulsingAvatar(avatarId: Int, size: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            tween(900, delayMillis = 300), RepeatMode.Reverse
        ),
        label = "ring2"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer ring
        Box(
            modifier = Modifier
                .size((size + 24).dp)
                .scale(scale2)
                .clip(CircleShape)
                .background(BWColors.Blue.copy(alpha = 0.12f))
        )
        // Inner ring
        Box(
            modifier = Modifier
                .size((size + 12).dp)
                .scale(scale1)
                .clip(CircleShape)
                .background(BWColors.Blue.copy(alpha = 0.22f))
        )
        AvatarCircle(avatarId = avatarId, size = size)
    }
}

@Composable
fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    tween(400, delayMillis = index * 150),
                    RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BWColors.Blue.copy(alpha = alpha))
            )
        }
    }
}
