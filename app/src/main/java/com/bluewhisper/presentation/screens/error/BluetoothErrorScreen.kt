package com.bluewhisper.presentation.screens.error

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluewhisper.presentation.navigation.BTErrorType
import com.bluewhisper.presentation.theme.*

@Composable
fun BluetoothErrorScreen(errorType: BTErrorType, onRetry: () -> Unit) {
    val (icon, title, message) = when (errorType) {
        BTErrorType.BLUETOOTH_OFF ->
            Triple("📵", "Bluetooth is Off",
                "BlueWhisper needs Bluetooth to find people nearby.\nNo internet needed — ever.")
        BTErrorType.PERMISSION_DENIED ->
            Triple("🔒", "Permission Required",
                "BlueWhisper needs Bluetooth permission to find nearby users.\nPlease grant it to continue.")
        BTErrorType.BLE_NOT_SUPPORTED ->
            Triple("⚠️", "Device Not Compatible",
                "Your device doesn't support Bluetooth Low Energy (BLE 4.0+), which BlueWhisper requires.")
        BTErrorType.BLUETOOTH_ERROR ->
            Triple("📻", "Bluetooth Error",
                "A Bluetooth error occurred. Please restart Bluetooth and try again.")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BWColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier = Modifier.padding(Spacing.xxl)
        ) {
            Text(icon, fontSize = 80.sp)

            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = BWColors.OnBackground,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = BWColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.md))

            // Primary action button
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(BWColors.Blue, BWColors.Teal)),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (errorType) {
                            BTErrorType.BLUETOOTH_OFF -> "Turn On Bluetooth →"
                            BTErrorType.PERMISSION_DENIED -> "Grant Permission →"
                            else -> "Try Again →"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BWColors.Border)
            ) {
                Text("Open Settings", color = BWColors.TextSecondary)
            }

            Text(
                "App resumes automatically when Bluetooth turns on.",
                style = MaterialTheme.typography.bodySmall,
                color = BWColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
