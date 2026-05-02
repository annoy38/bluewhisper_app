package com.bluewhisper.presentation.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.domain.model.NicknameRules
import com.bluewhisper.presentation.screens.home.AvatarCircle
import com.bluewhisper.presentation.theme.*

// ── Settings Screen ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onSavedFilesClicked: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showNicknameEdit by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf(uiState.profile.nickname) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Saved Files?", color = BWColors.OnBackground) },
            text = { Text("This will permanently delete all files you saved. This cannot be undone.", color = BWColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllFiles(); showClearDialog = false }) {
                    Text("Delete All", color = BWColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = BWColors.Blue)
                }
            },
            containerColor = BWColors.Surface
        )
    }

    Scaffold(
        containerColor = BWColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = BWColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, null, tint = BWColors.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BWColors.Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ── Profile ─────────────────────────────────────────
            SettingsSectionLabel("MY PROFILE")
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
                border = BorderStroke(1.dp, BWColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.base),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier.clickable { showAvatarPicker = true }
                    ) {
                        AvatarCircle(avatarId = uiState.profile.avatarId, size = 56)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(BWColors.Blue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Edit, null, tint = BWColors.OnBackground, modifier = Modifier.size(10.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (showNicknameEdit) {
                            OutlinedTextField(
                                value = nicknameInput,
                                onValueChange = { nicknameInput = NicknameRules.sanitize(it) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BWColors.Blue,
                                    unfocusedBorderColor = BWColors.Border,
                                    focusedTextColor = BWColors.OnBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(uiState.profile.nickname, style = MaterialTheme.typography.headlineSmall, color = BWColors.OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Tap avatar to change", style = MaterialTheme.typography.bodySmall, color = BWColors.Blue)
                    }
                    if (showNicknameEdit) {
                        TextButton(onClick = { viewModel.updateNickname(nicknameInput); showNicknameEdit = false }) {
                            Text("Save", color = BWColors.Blue)
                        }
                    } else {
                        TextButton(onClick = { nicknameInput = uiState.profile.nickname; showNicknameEdit = true }) {
                            Text("Edit", color = BWColors.Blue)
                        }
                    }
                }
            }

            // ── Visibility ──────────────────────────────────────
            SettingsSectionLabel("PRIVACY")
            SettingsRow(
                icon = if (uiState.isDiscoverable) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                title = "Show me to others",
                subtitle = if (uiState.isDiscoverable) "Visible to nearby users" else "Hidden from all users",
                trailingContent = {
                    Switch(
                        checked = uiState.isDiscoverable,
                        onCheckedChange = { viewModel.toggleDiscoverability() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BWColors.OnBackground,
                            checkedTrackColor = BWColors.Blue,
                            uncheckedTrackColor = BWColors.Border
                        )
                    )
                }
            )

            // ── Language ────────────────────────────────────────
            SettingsSectionLabel("LANGUAGE")
            val ctx = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppLanguage.entries.forEach { lang ->
                    Button(
                        onClick = {
                                    viewModel.updateLanguage(lang, ctx)
                                },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.profile.language == lang) BWColors.Blue else BWColors.Surface
                        ),
                        border = BorderStroke(1.dp, if (uiState.profile.language == lang) BWColors.Blue else BWColors.Border),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (lang == AppLanguage.ENGLISH) "English" else "বাংলা",
                            color = BWColors.OnBackground
                        )
                    }
                }
            }

            // ── Storage ─────────────────────────────────────────
            SettingsSectionLabel("STORAGE")
            SettingsRow(
                icon = Icons.Filled.Folder,
                title = "Received Files",
                subtitle = "${uiState.fileCount} files · ${viewModel.formatStorageSize(uiState.totalStorageBytes)}",
                onClick = onSavedFilesClicked
            )
            SettingsRow(
                icon = Icons.Filled.DeleteForever,
                title = "Clear All Files",
                subtitle = "Free up storage",
                iconTint = BWColors.Red,
                titleColor = BWColors.Red,
                onClick = { showClearDialog = true }
            )

            // ── About ────────────────────────────────────────────
            SettingsSectionLabel("ABOUT")
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "BlueWhisper",
                subtitle = "Version 1.0.0 · Private. Nearby. Gone."
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BWColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.base, bottom = Spacing.xs, start = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = BWColors.Blue,
    titleColor: androidx.compose.ui.graphics.Color = BWColors.OnBackground,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
        border = BorderStroke(1.dp, BWColors.Border),
        modifier = Modifier.fillMaxWidth().let {
            if (onClick != null) it.clickable(onClick = onClick) else it
        }
    ) {
        Row(
            modifier = Modifier.padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BWColors.TextSecondary)
                }
            }
            if (trailingContent != null) trailingContent()
            else if (onClick != null) {
                Icon(Icons.Filled.ChevronRight, null, tint = BWColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
