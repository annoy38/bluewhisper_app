package com.bluewhisper.presentation.screens.disconnect

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluewhisper.presentation.theme.*
import kotlinx.coroutines.delay

@Composable
fun DisconnectScreen(onAnimationComplete: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2200)
        visible = false
        delay(300)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BWColors.Background),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                modifier = Modifier.padding(Spacing.xxl)
            ) {
                val inf = rememberInfiniteTransition(label = "wind")
                val tx by inf.animateFloat(
                    initialValue = 0f, targetValue = 12f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "wind_x"
                )

                Text("💨", fontSize = 72.sp)

                Text(
                    "Chat Cleared",
                    style = MaterialTheme.typography.displayLarge,
                    color = BWColors.OnBackground,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Everything is gone.\nYour conversation was completely private.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BWColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BWColors.Surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BWColors.Teal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.base),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒", fontSize = 20.sp)
                        Column {
                            Text(
                                "No trace. No record.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BWColors.Teal,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Not even we know what you said.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BWColors.TextSecondary
                            )
                        }
                    }
                }

                Text(
                    "Returning to home...",
                    style = MaterialTheme.typography.bodySmall,
                    color = BWColors.TextTertiary
                )
            }
        }
    }
}
