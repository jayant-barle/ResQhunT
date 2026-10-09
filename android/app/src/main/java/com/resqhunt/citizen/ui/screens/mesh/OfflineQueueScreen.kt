package com.resqhunt.citizen.ui.screens.mesh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OfflineQueueScreen(
    database: AppDatabase,
    relayEngine: StoreAndForwardRelayEngine,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val messages by database.relayMessageDao().getAllMessagesFlow().collectAsState(initial = emptyList())
    var isFlushing by remember { mutableStateOf(false) }

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
            Text("Store-and-Forward Outbox", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Flush button
        Button(
            onClick = {
                coroutineScope.launch {
                    isFlushing = true
                    relayEngine.flushPendingOutbox()
                    isFlushing = false
                }
            },
            enabled = !isFlushing,
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isFlushing) "Flushing to Connected Peers..." else "Broadcast Outbox to Peers", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Text("Buffered Envelopes (${messages.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No buffered mesh messages in local queue.", fontSize = 12.sp, color = MutedGray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { msg ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (msg.status == "PENDING_FORWARD") EmergencyRed else TealDark
                                )
                                Text(
                                    text = "Hop ${msg.hopCount} / ${msg.maxHops}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                            Text(
                                text = "Message ID: ${msg.messageId}",
                                fontSize = 11.sp,
                                color = InkText,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = "Target SOS: ${msg.requestId}",
                                fontSize = 11.sp,
                                color = MutedGray
                            )
                        }
                    }
                }
            }
        }
    }
}
