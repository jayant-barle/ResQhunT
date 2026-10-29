package com.resqhunt.citizen.ui.screens.sos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.resqhunt.citizen.ResQhunTApp
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
import java.util.Locale
import java.util.UUID

@Composable
fun OneTapSosScreen(
    database: AppDatabase,
    relayEngine: StoreAndForwardRelayEngine,
    onSosTriggered: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ResQhunTApp
    val locationManager = app.locationManager
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(EmergencyCategory.MEDICAL) }
    var selectedSeverity by remember { mutableStateOf(SeverityLevel.CRITICAL) }
    var affectedCount by remember { mutableIntStateOf(2) }
    var description by remember { mutableStateOf("Immediate medical assistance required, injuries sustained") }
    var manualLocation by remember { mutableStateOf("") }

    val currentFix by locationManager.currentFix.collectAsState()
    var hasPermission by remember { mutableStateOf(locationManager.hasLocationPermission()) }
    var isApproximate by remember { mutableStateOf(locationManager.isApproximateOnly()) }
    var isGpsEnabled by remember { mutableStateOf(locationManager.isLocationEnabled()) }
    var isLocAcquiring by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPermission = locationManager.hasLocationPermission()
        isApproximate = locationManager.isApproximateOnly()
        isGpsEnabled = locationManager.isLocationEnabled()
        coroutineScope.launch {
            isLocAcquiring = true
            locationManager.acquireCurrentOrLastKnownLocation(3000L)
            isLocAcquiring = false
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = locationManager.hasLocationPermission()
        isApproximate = locationManager.isApproximateOnly()
        isGpsEnabled = locationManager.isLocationEnabled()
        isLocAcquiring = true
        locationManager.acquireCurrentOrLastKnownLocation(2500L)
        isLocAcquiring = false
    }

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

            // Step 1: Obtain genuine location from EmergencyLocationManager (never fabricate coordinates or 0,0)
            val cachedFix = locationManager.getBestCachedFix()
            val lat: Double? = cachedFix?.latitude
            val lon: Double? = cachedFix?.longitude
            val acc: Float? = cachedFix?.accuracy
            val locTimestamp: Long? = cachedFix?.timestamp
            val locSource: String = cachedFix?.source ?: "UNAVAILABLE"
            val locAddress: String = if (lat != null && lon != null) {
                manualLocation.ifBlank { "Coordinates Locked ($locSource)" }
            } else {
                manualLocation.ifBlank { "Location unavailable" }
            }

            val sosEntity = SosEntity(
                requestId = newRequestId,
                category = selectedCategory.name,
                severity = selectedSeverity.name,
                affectedCount = affectedCount,
                description = description,
                latitude = lat,
                longitude = lon,
                locationAccuracy = acc,
                locationAddress = locAddress,
                locationTimestamp = locTimestamp,
                locationSource = locSource,
                deliveryState = DeliveryState.STORED_LOCALLY.name,
                priorityScore = evaluation.priorityScore,
                priorityCategory = evaluation.priorityCategory.name
            )

            // Step 2: Persist to Room local database before doing anything
            database.sosDao().insertSos(sosEntity)

            // Step 3: Hand off to mesh store-and-forward engine
            relayEngine.createAndBroadcastSos(sosEntity)

            // Step 4: Start foreground service for active beacon
            SosRelayForegroundService.start(context)

            // Step 5: Asynchronously acquire fresh high-accuracy GPS fix with timeout (never blocking SOS dispatch)
            coroutineScope.launch {
                try {
                    if (locationManager.hasLocationPermission() && locationManager.isLocationEnabled()) {
                        Log.d("OneTapSosScreen", "Acquiring background fresh high-accuracy fix for $newRequestId...")
                        val freshFix = locationManager.acquireCurrentOrLastKnownLocation(maxWaitMs = 5000L)
                        if (freshFix != null) {
                            relayEngine.updateSosLocationAndBroadcast(newRequestId, freshFix)
                            Log.i("OneTapSosScreen", "Updated and broadcasted fresh fix for $newRequestId (${freshFix.latitude}, ${freshFix.longitude})")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("OneTapSosScreen", "Background GPS update exception: ${e.message}")
                }
            }

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

        // Category Selection
        Text("Emergency Type", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
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

        // Severity Selection
        Text("Severity Level", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeverityLevel.values().forEach { sev ->
                val isSelected = selectedSeverity == sev
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        when (sev) {
                            SeverityLevel.CRITICAL -> EmergencyRed
                            SeverityLevel.HIGH -> Color(0xFFE65100)
                            SeverityLevel.MEDIUM -> Color(0xFFF57C00)
                            SeverityLevel.LOW -> TealAccent
                        }
                    } else CardSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSeverity = sev }
                        .border(1.dp, if (isSelected) Color.Transparent else Color.LightGray, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = sev.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else InkText
                        )
                    }
                }
            }
        }

        // Affected Count Stepper
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

        // Genuine GPS Rescue Location Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (currentFix != null) EmergencyRed else MutedGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Rescue Location Lock", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                    }

                    if (currentFix != null) {
                        val fix = currentFix!!
                        val badgeColor = when {
                            fix.isFresh -> TealDark
                            fix.isApproximate -> Color(0xFFE65100)
                            else -> Color(0xFFF57C00)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = fix.source,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!hasPermission) {
                    Text(
                        text = "Location permission is required to embed exact coordinates into your emergency beacon.",
                        fontSize = 11.sp,
                        color = EmergencyRed
                    )
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Grant Location Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (!isGpsEnabled) {
                    Text(
                        text = "Device Location Services are disabled. Device cannot acquire satellite lock.",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Enable Location in Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (currentFix != null) {
                    val fix = currentFix!!
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Coordinates:", fontSize = 11.sp, color = MutedGray)
                        Text(
                            String.format(Locale.US, "%.6f°, %.6f°", fix.latitude, fix.longitude),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Accuracy:", fontSize = 11.sp, color = MutedGray)
                        Text(
                            "± ${String.format(Locale.US, "%.1f", fix.accuracy)} meters",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark
                        )
                    }

                    if (isApproximate) {
                        Text(
                            text = "Note: Approximate location granted. Precise location recommended for exact building/room pinpointing.",
                            fontSize = 10.sp,
                            color = Color(0xFFE65100),
                            lineHeight = 14.sp
                        )
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Upgrade to Precise Location", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLocAcquiring) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NavyPrimary)
                        }
                        Text(
                            text = if (isLocAcquiring) "Acquiring GPS satellite fix..." else "No recent GPS lock. SOS will transmit and acquire fix in background.",
                            fontSize = 11.sp,
                            color = MutedGray
                        )
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
