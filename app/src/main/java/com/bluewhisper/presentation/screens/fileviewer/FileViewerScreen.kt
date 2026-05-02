package com.bluewhisper.presentation.screens.fileviewer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bluewhisper.domain.model.FileState
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.ReceivedFile
import com.bluewhisper.presentation.screens.chat.ChatViewModel
import com.bluewhisper.presentation.theme.*
import java.io.File

/**
 * FR-07.2–07.6: Full-screen file viewer with 10-second countdown.
 *
 * FR-07.4 FIX: countdown runs in ChatViewModel (session-scoped) so it
 * continues even if the user closes and reopens this screen.
 *
 * chatViewModel is provided by the caller (same instance as ChatScreen).
 */
@Composable
fun FileViewerScreen(
    file: ReceivedFile,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onVanished: () -> Unit,
    onStateChanged: (fileId: String, newState: com.bluewhisper.domain.model.FileState) -> Unit = { _, _ -> },
    chatViewModel: ChatViewModel,                           // FR-07.4: session-scoped countdown
    viewModel: FileViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // FR-07.4: read countdown seconds from ChatViewModel, not local VM
    val remainingSeconds by remember {
        derivedStateOf { chatViewModel.getCountdownSeconds(file.id) }
    }
    val countdownActive by remember {
        derivedStateOf {
            chatViewModel.uiState.value.countdownFileId == file.id &&
            chatViewModel.uiState.value.countdownActive
        }
    }

    // Load file metadata into local VM for save logic
    LaunchedEffect(file) {
        viewModel.loadFile(file)
        // FR-07.2: start countdown in ChatViewModel on first open
        chatViewModel.startFileCountdown(file.id)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FileViewerEvent.FileVanished -> {
                    onStateChanged(file.id, FileState.VANISHED)
                    chatViewModel.updateReceivedFileState(file.id, FileState.VANISHED)
                    onVanished()
                }
                is FileViewerEvent.FileSaved -> {
                    onStateChanged(file.id, FileState.SAVED)
                    chatViewModel.updateReceivedFileState(file.id, FileState.SAVED)
                    // Stop countdown since file is saved
                    chatViewModel.stopFileCountdown(file.id)
                    onSaved()
                }
                else -> Unit
            }
        }
    }

    // Vanish triggered from ChatViewModel (countdown reached 0)
    val receivedFileState = chatViewModel.uiState.collectAsState().value.receivedFiles[file.id]?.state
    LaunchedEffect(receivedFileState) {
        if (receivedFileState == FileState.VANISHED && !uiState.isSaved) {
            onVanished()
        }
    }

    val bgTint = when (remainingSeconds) {
        in 0..2 -> Color(0x1FF85149)
        in 3..4 -> Color(0x12E86B00)
        else    -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(bgTint)
    ) {
        // File preview
        if (!uiState.isVanished && receivedFileState != FileState.VANISHED) {
            FilePreviewArea(
                file = file,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            )
        }

        // Top bar
        TopBar(
            fileName = file.fileName,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Vanished overlay
        AnimatedVisibility(
            visible = uiState.isVanished || receivedFileState == FileState.VANISHED,
            enter = fadeIn(tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.base)
            ) {
                Text("💨", fontSize = 72.sp)
                Text(
                    "Vanished",
                    style = MaterialTheme.typography.displayLarge,
                    color = BWColors.Purple,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Bottom panel
        if (!uiState.isVanished && !uiState.isSaved &&
            receivedFileState != FileState.VANISHED && receivedFileState != FileState.SAVED) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // FR-07.3: countdown card with seconds from ChatViewModel
                CountdownCard(remainingSeconds = remainingSeconds, isCounting = countdownActive)

                // FR-07.5: Save button
                Button(
                    onClick = viewModel::saveFile,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.SaveAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("💾 Save to Phone", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    "File vanishes when timer ends. Saving is permanent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Saved overlay
        if (uiState.isSaved || receivedFileState == FileState.SAVED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.base)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BWColors.Green)
                    .padding(Spacing.base),
                contentAlignment = Alignment.Center
            ) {
                Text("✅ Saved to BlueWhisper folder!", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TopBar(fileName: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = BWColors.OnBackground) }
        Text(fileName, style = MaterialTheme.typography.bodyMedium, color = BWColors.OnBackground, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
private fun FilePreviewArea(file: ReceivedFile, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (file.fileType) {
            FileType.IMAGE -> AsyncImage(
                model = File(file.tempPath),
                contentDescription = file.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            FileType.AUDIO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text("🎵", fontSize = 64.sp)
                Text(file.fileName, color = BWColors.OnBackground, textAlign = TextAlign.Center)
            }
            FileType.VIDEO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text("🎥", fontSize = 64.sp)
                Text(file.fileName, color = BWColors.OnBackground, textAlign = TextAlign.Center)
            }
            FileType.DOCUMENT -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text("📄", fontSize = 64.sp)
                Text(file.fileName, color = BWColors.OnBackground, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CountdownCard(remainingSeconds: Int, isCounting: Boolean) {
    val barColor = when (remainingSeconds) {
        in 0..2 -> BWColors.Red
        in 3..5 -> BWColors.Orange
        else    -> BWColors.Purple
    }
    val pulseScale by rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = if (remainingSeconds <= 3) 1.05f else 1f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "scale"
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0018)),
        border = BorderStroke(2.dp, Brush.horizontalGradient(listOf(BWColors.Purple, barColor)))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.base),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text("⏱ Vanishes in", style = MaterialTheme.typography.bodyMedium, color = BWColors.Purple)
            Text(
                text = "$remainingSeconds",
                fontSize = (48 * pulseScale).sp,
                fontWeight = FontWeight.Black,
                color = if (remainingSeconds <= 3) BWColors.Red else BWColors.OnBackground
            )
            LinearProgressIndicator(
                progress = { remainingSeconds / 10f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = BWColors.Border
            )
        }
    }
}
