package com.bluewhisper.presentation.screens.chat

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.presentation.theme.*

// Picker options:
//   Camera, Gallery, Document, Audio, Video.
// Deviation from docx FR-06.1 (which lists 3 entry points: Camera / Gallery /
// File manager) is intentional. FR-06.3 explicitly allows MP4, AAC and MP3
// transfer; routing them through the generic File picker had two problems:
// (a) Document picker hides the audio/video MIME categories on most OEMs and
// (b) users couldn't share recordings or short videos without first making
// them visible to the system Files app. Splitting into 5 dedicated options
// keeps every supported MIME class one tap away. PRD update tracked separately.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerBottomSheet(
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    onError: (String) -> Unit,
    fileManager: FileManager
) {
    val context = LocalContext.current

    // ── Camera launcher ───────────────────────────────────────────
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { uri ->
                validateAndReturn(uri, fileManager, onFileSelected, onError)
                onDismiss()
            }
        }
    }

    // ── Gallery launcher ──────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            validateAndReturn(it, fileManager, onFileSelected, onError)
            onDismiss()
        }
    }

    // ── Document launcher ─────────────────────────────────────────
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            validateAndReturn(it, fileManager, onFileSelected, onError)
            onDismiss()
        }
    }

    // ── Audio launcher ────────────────────────────────────────────
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            validateAndReturn(it, fileManager, onFileSelected, onError)
            onDismiss()
        }
    }

    // ── Video launcher (FR-06.3: MP4 support) ─────────────────────
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            validateAndReturn(it, fileManager, onFileSelected, onError)
            onDismiss()
        }
    }

    // ── Camera permission launcher ────────────────────────────────
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Create temp URI for camera output
            val photoFile = java.io.File.createTempFile(
                "photo_", ".jpg", context.cacheDir
            )
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            onError("Camera permission denied")
        }
    }

    // Media permission launcher (API 33+)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            galleryLauncher.launch("image/*")
        } else {
            onError("Storage permission denied")
        }
    }

    // ── Bottom Sheet UI ───────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BWColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = Spacing.md)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BWColors.Border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Title
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Send a File",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Max 25 MB per file",
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.TextSecondary
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            // 2×2 option grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                FilePickerOption(
                    emoji = "📷",
                    label = "Camera",
                    color = BWColors.Blue,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                FilePickerOption(
                    emoji = "🖼️",
                    label = "Gallery",
                    color = BWColors.Purple,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO
                                )
                            )
                        } else {
                            galleryLauncher.launch("image/*")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                FilePickerOption(
                    emoji = "📄",
                    label = "Document",
                    color = BWColors.Orange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        documentLauncher.launch(
                            arrayOf("application/pdf", "text/plain")
                        )
                    }
                )
                FilePickerOption(
                    emoji = "🎵",
                    label = "Audio",
                    color = BWColors.Green,
                    modifier = Modifier.weight(1f),
                    onClick = { audioLauncher.launch("audio/*") }
                )
                FilePickerOption(
                    emoji = "🎥",
                    label = "Video",
                    color = BWColors.Purple,
                    modifier = Modifier.weight(1f),
                    onClick = { videoLauncher.launch("video/*") }   // FR-06.3 MP4
                )
            }

            // Cancel
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BWColors.Border)
            ) {
                Text("Cancel", color = BWColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun FilePickerOption(
    emoji: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(emoji, fontSize = 32.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Validate and return ───────────────────────────────────────────
private fun validateAndReturn(
    uri: Uri,
    fileManager: FileManager,
    onFileSelected: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    when (val result = fileManager.validate(uri)) {
        is FileManager.ValidationResult.OK -> onFileSelected(uri)
        is FileManager.ValidationResult.TooLarge ->
            onError("File is too large (${"%.1f".format(result.sizeMb)}MB). Maximum is 25MB.")
        is FileManager.ValidationResult.UnsupportedType ->
            onError("File type not supported.")
        is FileManager.ValidationResult.FileNotFound ->
            onError("Could not read file. Please try again.")
    }
}
