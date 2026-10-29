package com.resqhunt.citizen.ui.screens.location

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.location.LocationPermissionState
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ResQhunTApp
    val locationManager = app.locationManager
    val coroutineScope = rememberCoroutineScope()

    val currentFix by locationManager.currentFix.collectAsState()
    var isAcquiring by remember { mutableStateOf(false) }
    var permissionState by remember { mutableStateOf(locationManager.getPermissionState()) }
    var isLocationEnabled by remember { mutableStateOf(locationManager.isLocationEnabled()) }
    var isGpsProviderEnabled by remember { mutableStateOf(locationManager.isGpsProviderEnabled()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionState = locationManager.getPermissionState()
        isLocationEnabled = locationManager.isLocationEnabled()
        isGpsProviderEnabled = locationManager.isGpsProviderEnabled()
        coroutineScope.launch {
            isAcquiring = true
            locationManager.acquireCurrentOrLastKnownLocation(3000L)
            isAcquiring = false
        }
    }

    val refreshLocation: () -> Unit = {
        coroutineScope.launch {
            isAcquiring = true
            permissionState = locationManager.getPermissionState()
            isLocationEnabled = locationManager.isLocationEnabled()
            isGpsProviderEnabled = locationManager.isGpsProviderEnabled()
            locationManager.acquireCurrentOrLastKnownLocation(3000L)
            isAcquiring = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLocation()
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
            Text("GPS & Location Services", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            IconButton(
                onClick = { refreshLocation() },
                enabled = !isAcquiring
            ) {
                if (isAcquiring) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NavyPrimary)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh GPS", tint = NavyPrimary)
                }
            }
        }

        // Hardware & Permission Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sensor & Hardware Subsystems", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)

                // Permission State Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Location Permission:", fontSize = 12.sp, color = MutedGray)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = when (permissionState) {
                                LocationPermissionState.PRECISE -> Icons.Default.CheckCircle
                                LocationPermissionState.APPROXIMATE -> Icons.Default.Warning
                                LocationPermissionState.DENIED -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when (permissionState) {
                                LocationPermissionState.PRECISE -> TealDark
                                LocationPermissionState.APPROXIMATE -> Color(0xFFE65100)
                                LocationPermissionState.DENIED -> EmergencyRed
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (permissionState) {
                                LocationPermissionState.PRECISE -> "PRECISE (FINE)"
                                LocationPermissionState.APPROXIMATE -> "APPROXIMATE ONLY"
                                LocationPermissionState.DENIED -> "DENIED"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (permissionState) {
                                LocationPermissionState.PRECISE -> TealDark
                                LocationPermissionState.APPROXIMATE -> Color(0xFFE65100)
                                LocationPermissionState.DENIED -> EmergencyRed
                            }
                        )
                    }
                }

                // Device Location Services Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device Location Services:", fontSize = 12.sp, color = MutedGray)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (isLocationEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isLocationEnabled) TealDark else EmergencyRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isGpsProviderEnabled -> "ACTIVE (GPS ON)"
                                isLocationEnabled -> "ACTIVE (NETWORK ONLY)"
                                else -> "DISABLED"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocationEnabled) TealDark else EmergencyRed
                        )
                    }
                }

                // Provider Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Provider Client:", fontSize = 12.sp, color = MutedGray)
                    Text("Google FusedLocationProvider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkText)
                }

                // Upgrade / Fix Button if approximate or disabled
                if (permissionState == LocationPermissionState.APPROXIMATE) {
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("Upgrade to Precise Location", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (permissionState == LocationPermissionState.DENIED) {
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("Grant Location Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isLocationEnabled) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("Turn On Location Services in Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Coordinate Lock Card
        val fix = currentFix
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (fix != null) EmergencyRed else MutedGray)
                        Text("Active Location Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InkText)
                    }

                    if (fix != null) {
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

                if (fix != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Latitude:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = String.format(Locale.US, "%.6f° %s", Math.abs(fix.latitude), if (fix.latitude >= 0) "N" else "S"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Longitude:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = String.format(Locale.US, "%.6f° %s", Math.abs(fix.longitude), if (fix.longitude >= 0) "E" else "W"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Accuracy Radius:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = "± ${String.format(Locale.US, "%.1f", fix.accuracy)} meters",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark
                        )
                    }
                    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    val ageMs = fix.ageMs
                    val ageStr = when {
                        ageMs < 60_000L -> "${ageMs / 1000}s ago"
                        ageMs < 3600_000L -> "${ageMs / 60_000L}m ago"
                        else -> "${ageMs / 3600_000L}h ago"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lock Acquired:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = "${sdf.format(Date(fix.timestamp))} ($ageStr)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Provider:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = fix.provider,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                    }
                } else {
                    Text(
                        text = "No GPS fix lock acquired yet. Ensure location permission is granted and device GPS is turned on.",
                        fontSize = 12.sp,
                        color = MutedGray,
                        lineHeight = 16.sp
                    )
                }

                Button(
                    onClick = { refreshLocation() },
                    enabled = !isAcquiring,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isAcquiring) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acquiring GPS Satellite Lock...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acquire Fresh Satellite Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Explanatory Note
        Text(
            text = "ResQhunT embeds genuine GPS coordinates into emergency envelopes when available. If you are inside collapsed structures or underground tunnels where satellite lock is unavailable, the app preserves manual landmark notes without delaying transmission and never fabricates false coordinates (0,0).",
            fontSize = 11.sp,
            color = MutedGray,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
