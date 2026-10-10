package com.resqhunt.citizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ui.theme.EmergencyRed
import com.resqhunt.citizen.ui.theme.InkText
import com.resqhunt.citizen.ui.theme.NavyPrimary

@Composable
fun VolumeSosCountdownDialog(
    remainingSeconds: Int,
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "🚨 EMERGENCY SHORTCUT ACTIVE",
                fontWeight = FontWeight.Black,
                color = EmergencyRed,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Volume Up + Down Held Simultaneously",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )

                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = EmergencyRed,
                        trackColor = EmergencyRed.copy(alpha = 0.2f),
                        strokeWidth = 6.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$remainingSeconds",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = EmergencyRed
                        )
                    }
                }

                Text(
                    text = "Keep both volume keys held for 3 seconds to broadcast emergency beacon.\n\nRelease either volume key to cancel immediately.",
                    fontSize = 12.sp,
                    color = InkText,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CANCEL SHORTCUT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}
