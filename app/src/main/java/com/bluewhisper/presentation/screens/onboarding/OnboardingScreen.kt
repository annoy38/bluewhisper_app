package com.bluewhisper.presentation.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.presentation.theme.*

// Avatar emoji list
val AVATARS = listOf(
    "😊","😎","🦁","🐯","🦊","🐼",
    "🤖","👾","🦋","🌟","⚡","🔥"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BWColors.Background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {

            // ── Greeting ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = if (uiState.selectedLanguage == AppLanguage.BANGLA)
                        "হ্যালো! 👋" else "Hey there! 👋",
                    style = MaterialTheme.typography.displayLarge,
                    color = BWColors.OnBackground
                )
                Text(
                    text = if (uiState.selectedLanguage == AppLanguage.BANGLA)
                        "মানুষ আপনাকে কী বলে ডাকবে?"
                    else "What should people call you?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BWColors.TextSecondary
                )
            }

            // ── Nickname Input ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = viewModel::onNicknameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = if (uiState.selectedLanguage == AppLanguage.BANGLA)
                                "আপনার নাম..." else "Your nickname...",
                            color = BWColors.TextTertiary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BWColors.Blue,
                        unfocusedBorderColor = BWColors.Border,
                        focusedTextColor = BWColors.OnBackground,
                        unfocusedTextColor = BWColors.OnBackground,
                        cursorColor = BWColors.Blue,
                        focusedContainerColor = BWColors.SurfaceVariant,
                        unfocusedContainerColor = BWColors.SurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Text(
                            text = "${uiState.nickname.length}/15",
                            style = MaterialTheme.typography.bodySmall,
                            color = BWColors.TextTertiary,
                            modifier = Modifier.padding(end = Spacing.sm)
                        )
                    }
                )
            }

            // ── Avatar Grid ───────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = if (uiState.selectedLanguage == AppLanguage.BANGLA)
                        "আপনার অবতার বেছে নিন" else "Pick your avatar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.SemiBold
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BWColors.Surface)
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(AVATARS) { index, emoji ->
                        AvatarItem(
                            emoji = emoji,
                            avatarId = index + 1,
                            isSelected = uiState.selectedAvatarId == index + 1,
                            onClick = { viewModel.onAvatarSelected(index + 1) }
                        )
                    }
                }
            }

            // ── Language ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Language / ভাষা",
                    style = MaterialTheme.typography.headlineSmall,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        LanguagePill(
                            label = if (lang == AppLanguage.ENGLISH) "English" else "বাংলা",
                            isSelected = uiState.selectedLanguage == lang,
                            onClick = { viewModel.onLanguageSelected(lang) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // ── Start Button ──────────────────────────────────────
            StartButton(
                isEnabled = uiState.isStartEnabled && !uiState.isSaving,
                isLoading = uiState.isSaving,
                language = uiState.selectedLanguage,
                onClick = { viewModel.saveProfileAndComplete(onOnboardingComplete) }
            )
        }
    }
}

@Composable
private fun AvatarItem(
    emoji: String,
    avatarId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "avatar_scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) Color(0xFF0D2B5E) else BWColors.SurfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) BWColors.Blue else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LanguagePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BWColors.Blue else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) BWColors.Blue else BWColors.Border,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.base),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else BWColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StartButton(
    isEnabled: Boolean,
    isLoading: Boolean,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val gradientBrush = Brush.horizontalGradient(
        listOf(BWColors.Blue, BWColors.Teal)
    )

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = BWColors.Border
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = if (isEnabled) ButtonDefaults.buttonElevation(4.dp)
                    else ButtonDefaults.buttonElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isEnabled) gradientBrush else Brush.horizontalGradient(
                        listOf(BWColors.Border, BWColors.Border)
                    ),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (language == AppLanguage.BANGLA)
                        "✦ শুরু করুন →" else "✦ Start Chatting →",
                    color = if (isEnabled) Color.White else BWColors.TextTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
