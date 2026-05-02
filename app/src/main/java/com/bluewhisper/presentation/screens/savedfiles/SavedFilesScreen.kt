package com.bluewhisper.presentation.screens.savedfiles

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.data.local.SavedFileDao
import com.bluewhisper.data.local.SavedFileEntity
import com.bluewhisper.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────
@HiltViewModel
class SavedFilesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedFileDao: SavedFileDao
) : ViewModel() {

    val files: StateFlow<List<SavedFileEntity>> = savedFileDao.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteFile(file: SavedFileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Q+ saved via MediaStore — delete the public copy via ContentResolver.
                file.mediaStoreUri?.let { uriStr ->
                    runCatching {
                        appContext.contentResolver.delete(Uri.parse(uriStr), null, null)
                    }
                }
                // Legacy / fallback — delete the file directly if we still have a real path.
                if (file.mediaStoreUri == null) {
                    runCatching { File(file.localPath).delete() }
                }
            }
            savedFileDao.softDeleteFile(file.fileId)
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024         -> "${bytes}B"
        bytes < 1024 * 1024  -> "${"%.1f".format(bytes / 1024f)}KB"
        else                 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
    }
}

// ── Screen ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFilesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SavedFilesViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Scaffold(
        containerColor = BWColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            "Received Files",
                            color = BWColors.OnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (files.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(BWColors.Surface, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${files.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BWColors.TextSecondary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, null, tint = BWColors.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BWColors.Background
                )
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.base)
                ) {
                    Text("📭", fontSize = 64.sp)
                    Text(
                        "No saved files yet",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BWColors.TextSecondary
                    )
                    Text(
                        "Files you save during chats will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BWColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.xxl)
                    )
                }
            }
        } else {
            val grouped = files.groupBy {
                dateFormat.format(Date(it.savedAtEpoch))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                grouped.forEach { (date, dateFiles) ->
                    item {
                        Text(
                            text = date.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = BWColors.TextSecondary,
                            modifier = Modifier.padding(
                                top = Spacing.base,
                                bottom = Spacing.xs
                            )
                        )
                    }
                    items(
                        items = dateFiles,
                        key = { it.fileId }
                    ) { file ->
                        SavedFileCard(
                            file = file,
                            sizeText = viewModel.formatSize(file.fileSizeBytes),
                            onDelete = { viewModel.deleteFile(file) }
                        )
                    }
                }
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun SavedFileCard(
    file: SavedFileEntity,
    sizeText: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete file?", color = BWColors.OnBackground) },
            text = {
                Text(
                    "\"${file.fileName}\" will be permanently deleted.",
                    color = BWColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = BWColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = BWColors.Blue)
                }
            },
            containerColor = BWColors.Surface
        )
    }

    val (icon, iconColor) = when (file.fileType) {
        "IMAGE" -> "🖼️" to BWColors.Blue
        "AUDIO" -> "🎵" to BWColors.Green
        "VIDEO" -> "🎥" to BWColors.Purple
        else    -> "📄" to BWColors.Red
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
        border = BorderStroke(1.dp, BWColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // File type icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        iconColor.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "$sizeText · ${file.senderNickname} · " +
                            timeFormat.format(Date(file.savedAtEpoch)),
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.TextSecondary
                )
            }

            // FR-10.3: 3-dot menu — Open, Share, Delete
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = BWColors.TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            showMenu = false
                            SavedFileActions.openFile(context, file)
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            SavedFileActions.shareFile(context, file)
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Share, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = BWColors.Red) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = BWColors.Red
                            )
                        }
                    )
                }
            }
        }
    }
}
