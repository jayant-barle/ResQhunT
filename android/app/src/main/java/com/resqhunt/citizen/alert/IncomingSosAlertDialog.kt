package com.resqhunt.citizen.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.resqhunt.citizen.ui.theme.*
import java.util.Locale

@Composable
fun IncomingSosAlertDialog(
    alert: ActiveAlert,
    onSilence: () -> Unit,
    onViewDetails: (String) -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* Force explicit user acknowledgement */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (alert.isTest) NavyPrimary.copy(alpha = 0.1f) else EmergencyRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (alert.isTest) Icons.Default.Notifications else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (alert.isTest) NavyPrimary else EmergencyRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (alert.isTest) "SYSTEM TEST ALARM" else "INCOMING SOS EMERGENCY",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = if (alert.isTest) NavyPrimary else EmergencyRed
                        )
                    }
                }

                // Title & Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.category,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NavyPrimary
                    )
                    Text(
                        text = "${alert.severity} • ${alert.affectedCount} Person(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alert.severity == "CRITICAL") EmergencyRed else InkText
                    )
                }

                // Description
                Text(
                    text = alert.description,
                    fontSize = 13.sp,
                    color = InkText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Location Details: Display coordinates, accuracy, source, and landmark
                val hasCoordinates = alert.latitude != null && alert.longitude != null && alert.latitude != 0.0
                val hasAddress = !alert.locationAddress.isNullOrBlank()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyPrimary.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasCoordinates) {
                            val accStr = if (alert.locationAccuracy != null) " (±${String.format(Locale.US, "%.1f", alert.locationAccuracy)}m)" else ""
                            Text(
                                "Location: ${String.format(Locale.US, "%.6f, %.6f", alert.latitude, alert.longitude)}$accStr",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        } else {
                            Text(
                                "Location: Location unavailable",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MutedGray
                            )
                        }
                        if (hasAddress && alert.locationAddress != "Location unavailable") {
                            Text(
                                alert.locationAddress.orEmpty(),
                                fontSize = 11.sp,
                                color = InkText
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSilence,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkText)
                    ) {
                        Text("Silence", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onSilence()
                            onViewDetails(alert.requestId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f).height(42.dp)
                    ) {
                        Text("View SOS", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
