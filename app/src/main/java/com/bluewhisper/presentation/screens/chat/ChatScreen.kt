package com.bluewhisper.presentation.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluewhisper.di.FileManagerEntryPoint
import com.bluewhisper.domain.model.*
import com.bluewhisper.presentation.screens.home.AvatarCircle
import com.bluewhisper.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    onDisconnect: () -> Unit,
    onViewFile: (ReceivedFile) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // FR-07.8: observe file state updates posted by FileViewerScreen via savedStateHandle
    val navBackStackEntry = androidx.navigation.compose.currentBackStackEntryAsState()
    val savedStateHandle = navBackStackEntry.value?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("fileStateUpdate", null)
            ?.collect { update ->
                update?.let {
                    val parts = it.split(":")
                    if (parts.size == 2) {
                        val fileId = parts[0]
                        val newState = com.bluewhisper.domain.model.FileState
                            .valueOf(parts[1])
                        viewModel.updateReceivedFileState(fileId, newState)
                        savedStateHandle.remove<String>("fileStateUpdate")
                    }
                }
            }
    }

    // Show toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }
    val listState = rememberLazyListState()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.Disconnected -> onDisconnect()
                else -> Unit
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // FR-06: File picker — FileManager retrieved via Hilt EntryPoint (Fix 7)
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileManager = remember {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                FileManagerEntryPoint::class.java
            ).fileManager()
    }
    if (uiState.showFilePicker) {
        FilePickerBottomSheet(
            onDismiss = viewModel::hideFilePicker,
            onFileSelected = { uri -> viewModel.sendFile(uri) },
            onError = { viewModel.clearToast() },
            fileManager = fileManager
        )
    }

    // File transfer progress — show as dialog overlay (FR-06.4)
    uiState.activeFileTransfer?.let { transfer ->
        val progress = if (transfer.fileSizeBytes > 0)
            transfer.bytesTransferred.toFloat() / transfer.fileSizeBytes.toFloat()
        else 0f
        FileTransferProgressOverlay(transfer = transfer, progress = progress)
    }

    // Disconnect confirm dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("End this chat?", color = BWColors.OnBackground) },
            text = {
                Text(
                    "All messages will disappear permanently.",
                    color = BWColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    viewModel.disconnect()
                }) {
                    Text("Yes, End Chat", color = BWColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Keep Chatting", color = BWColors.Blue)
                }
            },
            containerColor = BWColors.Surface,
            titleContentColor = BWColors.OnBackground
        )
    }

    Scaffold(
        containerColor = BWColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                session = uiState.remoteUser,
                isConnected = uiState.isConnected,
                isEncrypted = uiState.isEncrypted,
                onDisconnect = { showDisconnectDialog = true }
            )
        },
        bottomBar = {
            ChatInputArea(
                input = uiState.currentInput,
                isLimitReached = uiState.isLimitReached,
                isTransferring = uiState.activeFileTransfer != null,
                isEncrypted = uiState.isEncrypted,
                remaining = uiState.remainingMessages,
                onInputChange = viewModel::onTypingChanged,
                onSend = {
                    viewModel.sendMessage(uiState.currentInput)
                    viewModel.onTypingChanged("")
                },
                onAttachClicked = { viewModel.showFilePicker() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Message progress bar
            MessageProgressBar(
                count = uiState.messageCount,
                max = MAX_MESSAGES
            )

            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    horizontal = Spacing.base,
                    vertical = Spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        when (message.type) {
                            MessageType.TEXT -> MessageBubble(
                                message = message,
                                senderName = uiState.remoteUser?.remoteNickname ?: ""
                            )
                            MessageType.FILE_NOTIFICATION -> {
                                val file = message.fileReference?.let {
                                    uiState.receivedFiles[it]
                                }
                                FileNotificationCard(
                                    message = message,
                                    file = file,
                                    onViewClicked = { if (file != null) onViewFile(file) }
                                )
                            }
                            else -> Unit
                        }
                    }
                }

                // Typing indicator
                if (uiState.isTyping) {
                    item {
                        TypingIndicator(
                            name = uiState.remoteUser?.remoteNickname ?: ""
                        )
                    }
                }
            }

            // Limit reached banner
            if (uiState.isLimitReached) {
                LimitReachedBanner(
                    onEndChat = { viewModel.disconnect() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    session: ActiveSession?,
    isConnected: Boolean,
    isEncrypted: Boolean,
    onDisconnect: () -> Unit
) {
    val pulseAlpha by rememberInfiniteTransition(label = "conn_pulse")
        .animateFloat(
            initialValue = 1f, targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "pulse"
        )

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (session != null) {
                    AvatarCircle(avatarId = session.remoteAvatarId, size = 32)
                    Column {
                        Text(
                            text = session.remoteNickname,
                            style = MaterialTheme.typography.headlineSmall,
                            color = BWColors.OnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !isConnected -> BWColors.Amber.copy(alpha = 0.4f)
                                            !isEncrypted -> BWColors.Amber.copy(alpha = pulseAlpha)
                                            else         -> BWColors.Green.copy(alpha = pulseAlpha)
                                        }
                                    )
                            )
                            Text(
                                // NFR-03.2: show "Securing…" until key exchange is done
                                text = when {
                                    !isConnected -> "Connecting..."
                                    !isEncrypted -> "🔐 Securing..."
                                    else         -> "🔒 Encrypted"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isEncrypted) BWColors.Green else BWColors.Amber
                            )
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Disconnect",
                    tint = BWColors.TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BWColors.Background.copy(alpha = 0.95f)
        )
    )
}

@Composable
private fun MessageProgressBar(count: Int, max: Int) {
    val progress = count.toFloat() / max.toFloat()
    val barColor = when {
        count >= max -> BWColors.ProgressRed
        count >= 17  -> BWColors.ProgressOrange
        count >= 14  -> BWColors.ProgressYellow
        else         -> BWColors.ProgressGreen
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    Column {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = barColor,
            trackColor = BWColors.Border
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "$count/$max",
                style = MaterialTheme.typography.bodySmall,
                color = BWColors.TextSecondary
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message, senderName: String) {
    val isSent = message.direction == MessageDirection.SENT
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            if (!isSent) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.Teal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = if (isSent) 4.dp else 16.dp,
                            bottomStart = if (isSent) 16.dp else 4.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isSent)
                            Brush.linearGradient(listOf(BWColors.BubbleSent1, BWColors.BubbleSent2))
                        else
                            Brush.linearGradient(
                                listOf(BWColors.BubbleReceived, BWColors.BubbleReceived)
                            )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BWColors.OnBackground
                )
            }
            Text(
                text = timeFormat.format(Date(message.timestampEpoch)),
                style = MaterialTheme.typography.bodySmall,
                color = BWColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FileNotificationCard(
    message: Message,
    file: ReceivedFile?,
    onViewClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
        colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
        border = BorderStroke(4.dp, BWColors.Blue)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.SemiBold
                )
                val stateText = when (file?.state) {
                    FileState.SAVED -> "✓ Saved"
                    FileState.VANISHED -> "💨 Vanished"
                    FileState.VIEWING -> "⏱ ${file.remainingSeconds}s left"
                    FileState.RECEIVED_UNVIEWED -> "Tap to view"
                    else -> "Receiving..."
                }
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (file?.state) {
                        FileState.SAVED -> BWColors.Green
                        FileState.VANISHED -> BWColors.Purple
                        else -> BWColors.Blue
                    }
                )
            }

            if (file?.state == FileState.RECEIVED_UNVIEWED ||
                file?.state == FileState.VIEWING) {
                TextButton(onClick = onViewClicked) {
                    Text(
                        "👁 View",
                        color = BWColors.Blue,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(name: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "$name is typing",
            style = MaterialTheme.typography.bodySmall,
            color = BWColors.TextSecondary,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val transition = rememberInfiniteTransition(label = "typing")
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 1f, targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(
                        tween(400, delayMillis = i * 150), RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(BWColors.TextSecondary.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun ChatInputArea(
    input: String,
    isLimitReached: Boolean,
    isTransferring: Boolean,    // FR-06.5: disable during active transfer
    isEncrypted: Boolean,       // NFR-03.1: block ALL input until key exchange done
    remaining: Int,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClicked: () -> Unit
) {
    val attachEnabled = !isLimitReached && !isTransferring && isEncrypted  // FR-06.5 + NFR-03.1
    val inputEnabled  = !isLimitReached && isEncrypted                     // NFR-03.1

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BWColors.Background,
        shadowElevation = 4.dp
    ) {
        Column {
            // NFR-03.1: "Securing connection…" banner shown until AES key is ready
            if (!isEncrypted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BWColors.SurfaceVariant)
                        .padding(horizontal = Spacing.base, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = BWColors.Amber
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "🔐 Securing connection — please wait...",
                        style = MaterialTheme.typography.bodySmall,
                        color = BWColors.Amber
                    )
                }
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // FR-06.5: Attach button disabled during transfer; NFR-06.3: 48dp touch target
            IconButton(
                onClick = { if (attachEnabled) onAttachClicked() },
                enabled = attachEnabled,
                modifier = Modifier.size(48.dp)   // NFR-06.3: minimum 48dp touch target
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach file",
                    tint = when {
                        isTransferring  -> BWColors.Amber        // busy indicator
                        !isLimitReached -> BWColors.TextSecondary
                        else            -> BWColors.TextTertiary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text input
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 200) onInputChange(it) },
                modifier = Modifier.weight(1f),
                enabled = inputEnabled,
                placeholder = {
                    Text(
                        text = when {
                            isLimitReached   -> "Limit reached — end the chat"
                            remaining <= 3   -> "Only $remaining message${if (remaining == 1) "" else "s"} left!"
                            else             -> "Message... ($remaining left)"
                        },
                        color = when {
                            isLimitReached -> BWColors.Red
                            remaining <= 3 -> BWColors.Orange
                            else           -> BWColors.TextTertiary
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BWColors.Blue,
                    unfocusedBorderColor = BWColors.Border,
                    focusedTextColor = BWColors.OnBackground,
                    unfocusedTextColor = BWColors.OnBackground,
                    disabledBorderColor = BWColors.Border,
                    disabledTextColor = BWColors.TextTertiary,
                    focusedContainerColor = BWColors.SurfaceVariant,
                    unfocusedContainerColor = BWColors.SurfaceVariant,
                    disabledContainerColor = BWColors.Surface
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            // Send button — NFR-06.3: 48dp minimum touch target
            val canSend = input.isNotBlank() && !isLimitReached && isEncrypted
            IconButton(
                onClick = { if (canSend) onSend() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) BWColors.Blue else BWColors.Border)
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (canSend) Color.White else BWColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }  // end Row
        }  // end Column
    }      // end Surface
}

@Composable
private fun LimitReachedBanner(onEndChat: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A0A00)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "✋ 20 messages reached",
                style = MaterialTheme.typography.bodyMedium,
                color = BWColors.Red,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEndChat) {
                Text(
                    "End Chat & Clear All",
                    color = BWColors.Red,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── FR-06.4: File Transfer Progress Overlay ───────────────────────
// Shows on BOTH sender and receiver during active transfer
@Composable
private fun FileTransferProgressOverlay(
    transfer: FileTransferState,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "transfer_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
            border = BorderStroke(1.dp, BWColors.Blue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (transfer.isIncoming) "📥 ${transfer.fileName}"
                               else "📤 ${transfer.fileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BWColors.OnBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (transfer.status) {
                            TransferStatus.SUCCESS  -> "Done ✓"
                            TransferStatus.FAILED   -> "Failed ✗"
                            TransferStatus.CANCELLED -> "Cancelled"
                            else -> "${(animatedProgress * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (transfer.status) {
                            TransferStatus.SUCCESS  -> BWColors.Green
                            TransferStatus.FAILED   -> BWColors.Red
                            else -> BWColors.Blue
                        }
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when (transfer.status) {
                        TransferStatus.SUCCESS -> BWColors.Green
                        TransferStatus.FAILED  -> BWColors.Red
                        else -> BWColors.Blue
                    },
                    trackColor = BWColors.Border
                )
            }
        }
    }
}
