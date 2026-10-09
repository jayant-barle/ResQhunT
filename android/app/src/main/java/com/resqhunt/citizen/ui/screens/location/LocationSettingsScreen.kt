package com.resqhunt.citizen.ui.screens.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ui.theme.*

@Composable
fun LocationSettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Text("GPS & Location Services", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmergencyRed)
                    Text("Last Known Coordinate Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InkText)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Latitude:", fontSize = 12.sp, color = MutedGray)
                    Text("28.613939° N", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Longitude:", fontSize = 12.sp, color = MutedGray)
                    Text("77.209021° E", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Accuracy Radius:", fontSize = 12.sp, color = MutedGray)
                    Text("± 8.5 meters", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fallback Mode:", fontSize = 12.sp, color = MutedGray)
                    Text("Manual Landmark Permitted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
            }
        }

        Text(
            text = "ResQhunT automatically embeds GPS coordinates into emergency envelopes when available. If you are inside collapsed structures or tunnels where satellite lock is unavailable, the app preserves manual landmark notes without delaying transmission.",
            fontSize = 11.sp,
            color = MutedGray,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
