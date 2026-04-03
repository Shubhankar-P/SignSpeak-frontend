package com.example.voicetotextapp.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicetotextapp.ui.components.RoomCodeBadge
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.PanTool

@Composable
fun RoomOptionsScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onSign2Text: (roomCode: String) -> Unit,
    onVoice2Text: (roomCode: String) -> Unit,
) {
    Scaffold(
        topBar = { SignSpeakTopBar(onNavigateBack = onNavigateBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Room code badge
            if (roomCode.isNotEmpty()) {
                RoomCodeBadge(roomCode = roomCode)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Heading
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Choose your mode",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "How will you communicate in this room?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

// SIGN2TEXT card
            ModeCard(
                icon = Icons.Default.PanTool,
                title = "Sign to Text",
                description = "Use your camera to translate sign language into text in real time",
                onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("sign")
                    onSign2Text(roomCode)
                }
            )

// VOICE2TEXT card
            ModeCard(
                icon = Icons.Default.RecordVoiceOver,
                title = "Voice to Text",
                description = "Speak and your words are transcribed and shared with the room",
                onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("voice")
                    onVoice2Text(roomCode)
                }
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
