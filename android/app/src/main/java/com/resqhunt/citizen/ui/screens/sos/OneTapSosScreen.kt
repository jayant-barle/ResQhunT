package com.resqhunt.citizen.ui.screens.sos

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.SeverityLevel
import com.resqhunt.citizen.domain.priority.DeterministicPriorityEngine
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.service.SosRelayForegroundService
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OneTapSosScreen(
    database: AppDatabase,
    relayEngine: StoreAndForwardRelayEngine,
    onSosTriggered: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(EmergencyCategory.MEDICAL) }
    var selectedSeverity by remember { mutableStateOf(SeverityLevel.CRITICAL) }
    var affectedCount by remember { mutableIntStateOf(2) }
    var description by remember { mutableStateOf("Immediate medical assistance required, injuries sustained") }
    var manualLocation by remember { mutableStateOf("") }

    // Accidental Activation Cancellation State
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(5) }

    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            countdownSeconds = 5
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds -= 1
            }
            // Trigger emergency when countdown completes!
            val newRequestId = "sos_" + UUID.randomUUID().toString()
            val evaluation = DeterministicPriorityEngine.evaluate(
                category = selectedCategory,
                severity = selectedSeverity,
                affectedCount = affectedCount,
                createdAtTimestampMs = System.currentTimeMillis()
            )

            val sosEntity = SosEntity(
                requestId = newRequestId,
                category = selectedCategory.name,
                severity = selectedSeverity.name,
                affectedCount = affectedCount,
                description = description,
                latitude = 28.6139, // Simulated/acquired coordinate
                longitude = 77.2090,
                locationAccuracy = 10.0f,
                locationAddress = manualLocation.ifBlank { "Connaught Place Sector 4" },
                deliveryState = DeliveryState.STORED_LOCALLY.name,
                priorityScore = evaluation.priorityScore,
                priorityCategory = evaluation.priorityCategory.name
            )

            // Step 1: Persist to Room local database before doing anything
            database.sosDao().insertSos(sosEntity)

            // Step 2: Hand off to mesh store-and-forward engine
            relayEngine.createAndBroadcastSos(sosEntity)

            // Step 3: Start foreground service for active beacon
            SosRelayForegroundService.start(context)

            isCountingDown = false
            onSosTriggered(newRequestId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Text("Emergency SOS Setup", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Category Picker
        Text("Select Emergency Category", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkText)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmergencyCategory.values().take(3).forEach { cat ->
                CategoryChip(
                    category = cat,
                    isSelected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmergencyCategory.values().drop(3).forEach { cat ->
                CategoryChip(
                    category = cat,
                    isSelected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Severity Selector
        Text("Declared Severity Level", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkText)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeverityLevel.values().forEach { sev ->
                val isSelected = selectedSeverity == sev
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        if (sev == SeverityLevel.CRITICAL) EmergencyRed else NavyPrimary
                    } else CardSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSeverity = sev }
                        .border(
                            1.dp,
                            if (isSelected) Color.Transparent else Color.LightGray,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = sev.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else InkText,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Affected Person Stepper
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Persons in Danger", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkText)
                    Text("Number of victims needing rescue", fontSize = 11.sp, color = MutedGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledIconButton(
                        onClick = { if (affectedCount > 1) affectedCount-- },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CanvasBg),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                    Text("$affectedCount", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
                    FilledIconButton(
                        onClick = { affectedCount++ },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CanvasBg),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                }
            }
        }

        // Description Input
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Emergency Description") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        // Manual Landmark / Location Fallback
        OutlinedTextField(
            value = manualLocation,
            onValueChange = { manualLocation = it },
            label = { Text("Optional Landmark / Nearest Floor / Room") },
            placeholder = { Text("e.g. 2nd floor stairwell near North entrance") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Trigger SOS Button
        Button(
            onClick = { isCountingDown = true },
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRIGGER EMERGENCY SOS", fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }

    // Accidental Activation Cancellation Countdown Modal
    if (isCountingDown) {
        AlertDialog(
            onDismissRequest = { /* Don't dismiss by outside touch */ },
            title = {
                Text(
                    text = "TRANSMITTING SOS IN $countdownSeconds SECONDS",
                    fontWeight = FontWeight.Black,
                    color = EmergencyRed,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdownSeconds",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = EmergencyRed
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "If this was an accidental tap, cancel immediately below. Otherwise, your beacon will be broadcast to all nearby rescue nodes.",
                        fontSize = 12.sp,
                        color = InkText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { isCountingDown = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCEL EMERGENCY (ACCIDENTAL ACTIVATION)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
fun CategoryChip(
    category: EmergencyCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) NavyPrimary else CardSurface,
        modifier = modifier
            .clickable { onClick() }
            .border(
                1.dp,
                if (isSelected) Color.Transparent else Color.LightGray,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else InkText
            )
        }
    }
}
