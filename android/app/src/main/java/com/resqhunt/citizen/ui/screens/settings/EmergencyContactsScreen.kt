package com.resqhunt.citizen.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ui.theme.*

@Composable
fun EmergencyContactsScreen(onBack: () -> Unit) {
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
            Text("Emergency (ICE) Contacts", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Text("Pre-configured Family & First Responder Hotlines", fontSize = 12.sp, color = MutedGray)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactItem(name = "National Disaster Management Authority (NDMA)", number = "1078")
                Divider(color = CanvasBg)
                ContactItem(name = "Disaster Medical Services / Ambulance", number = "108")
                Divider(color = CanvasBg)
                ContactItem(name = "Police Field Coordination", number = "112")
                Divider(color = CanvasBg)
                ContactItem(name = "Primary ICE Family Contact", number = "+91 98110 02233")
            }
        }
    }
}

@Composable
fun ContactItem(name: String, number: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkText)
            Text(number, fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Default.Phone, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
    }
}
