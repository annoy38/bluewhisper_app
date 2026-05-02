package com.bluewhisper.presentation.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluewhisper.domain.model.*
import com.bluewhisper.presentation.navigation.BTErrorType
import com.bluewhisper.presentation.screens.onboarding.AVATARS
import com.bluewhisper.presentation.theme.*

@Composable
fun HomeScreen(
    onChatNowClicked: (endpointId: String, nickname: String, avatarId: Int) -> Unit,
    onSettingsClicked: () -> Unit,
    onBluetoothError: (BTErrorType) -> Unit,
    onIncomingRequest: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScanning()
        } else {
            onBluetoothError(BTErrorType.PERMISSION_DENIED)
        }
    }

    // Handle incoming request navigation
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.IncomingRequest) {
            onIncomingRequest()
        }
    }

    // Handle BT errors
    LaunchedEffect(uiState.btErrorType) {
        uiState.btErrorType?.let {
            onBluetoothError(it)
            viewModel.clearBTError()
        }
    }

    // Start scanning when screen appears
    LaunchedEffect(Unit) {
        if (viewModel.checkPermissions()) {
            viewModel.startScanning()
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            permissionLauncher.launch(permissions)
        }
    }

    // FR-09.1: Do NOT stop scanning when HomeScreen is disposed.
    // BluetoothForegroundService continues scanning in background.
    // HomeViewModel.stopScanning() only updates local isScanning UI state.
    DisposableEffect(Unit) {
        onDispose {
            // Update UI state only — service keeps scanning
            viewModel.stopScanning()
        }
    }

    Scaffold(
        containerColor = BWColors.Background,
        topBar = {
            HomeTopBar(
                isDiscoverable = uiState.isDiscoverable,
                onVisibilityToggle = viewModel::toggleDiscoverability,
                onSettingsClicked = onSettingsClicked
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.base)
        ) {
            Spacer(Modifier.height(Spacing.sm))

            // Scan status pill
            ScanStatusPill(isScanning = uiState.isScanning)

            Spacer(Modifier.height(Spacing.base))

            if (uiState.nearbyDevices.isEmpty()) {
                EmptyState()
            } else {
                Text(
                    text = "${uiState.nearbyDevices.size} PEOPLE NEARBY",
                    style = MaterialTheme.typography.labelMedium,
                    color = BWColors.TextSecondary,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(
                        items = uiState.nearbyDevices,
                        key = { it.endpointId }
                    ) { device ->
                        val isAlreadyConnected = connectionState is ConnectionState.Connected ||
                            connectionState is ConnectionState.Requesting
                        UserCard(
                            device = device,
                            isAlreadyConnected = isAlreadyConnected,
                            onChatNow = {
                                if (!isAlreadyConnected) {
                                    onChatNowClicked(
                                        device.endpointId,
                                        device.nickname,
                                        device.avatarId
                                    )
                                    viewModel.requestConnection(device.endpointId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    isDiscoverable: Boolean,
    onVisibilityToggle: () -> Unit,
    onSettingsClicked: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                "BlueWhisper",
                style = MaterialTheme.typography.headlineMedium,
                color = BWColors.OnBackground,
                fontWeight = FontWeight.SemiBold
            )
        },
        actions = {
            // Visibility toggle
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (isDiscoverable) Icons.Filled.Visibility
                                  else Icons.Filled.VisibilityOff,
                    contentDescription = if (isDiscoverable) "Visible" else "Hidden",
                    tint = if (isDiscoverable) BWColors.Green else BWColors.TextSecondary
                )
            }
            // Settings
            IconButton(onClick = onSettingsClicked) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = BWColors.TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BWColors.Background
        )
    )
}

@Composable
private fun ScanStatusPill(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100.dp))
            .background(BWColors.Surface)
            .border(1.dp, BWColors.Border, RoundedCornerShape(100.dp))
            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BWColors.Blue.copy(alpha = if (isScanning) alpha else 0.3f))
            )
            Text(
                text = if (isScanning) "Scanning nearby..." else "Scanning paused",
                style = MaterialTheme.typography.bodyMedium,
                color = BWColors.Teal
            )
        }
    }
}

@Composable
private fun UserCard(
    device: NearbyDevice,
    isAlreadyConnected: Boolean = false,
    onChatNow: () -> Unit
) {
    val (distanceLabel, signalColor) = when (device.signalStrength) {
        SignalStrength.STRONG -> "Very close" to BWColors.Green
        SignalStrength.MEDIUM -> "Nearby" to BWColors.Amber
        SignalStrength.WEAK   -> "Far away" to BWColors.Red
    }

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
        border = BorderStroke(1.dp, BWColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Avatar
            AvatarCircle(avatarId = device.avatarId, size = 48)

            // Name + distance
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.nickname,
                    style = MaterialTheme.typography.headlineSmall,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(signalColor)
                    )
                    Text(
                        text = distanceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = BWColors.TextSecondary
                    )
                }
            }

            // Chat Now button
            Button(
                onClick = onChatNow,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(BWColors.Blue, BWColors.PurpleVariant)
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!isAlreadyConnected) "✦ Chat Now" else "Busy",
                        color = if (!isAlreadyConnected) Color.White else BWColors.TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarCircle(avatarId: Int, size: Int = 52) {
    val emoji = AVATARS.getOrElse(avatarId - 1) { "😊" }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        listOf(BWColors.Blue, BWColors.Teal),
                        listOf(BWColors.Amber, BWColors.Orange),
                        listOf(BWColors.Purple, BWColors.Blue),
                        listOf(BWColors.Green, BWColors.Teal),
                        listOf(BWColors.Red, BWColors.Orange)
                    )[(avatarId - 1) % 5]
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size * 0.5).sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.base)
    ) {
        Text("👥", fontSize = 72.sp, textAlign = TextAlign.Center)
        Text(
            text = "No one nearby yet",
            style = MaterialTheme.typography.headlineMedium,
            color = BWColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Ask a friend to open BlueWhisper\nand come within 30 metres!",
            style = MaterialTheme.typography.bodyMedium,
            color = BWColors.TextTertiary,
            textAlign = TextAlign.Center
        )
    }
}
