package com.example.voicetotextapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicetotextapp.ui.theme.PurplePrimary
import com.google.firebase.database.FirebaseDatabase

/**
 * Replaces: activity_room_options.xml + RoomOptionsActivity
 *
 * @param roomCode          The room code passed from the previous screen.
 * @param onNavigateBack    Called when the back arrow is tapped (replaces finish()).
 * @param onSign2Text       Navigate to Sign2TextActivity equivalent.
 * @param onVoice2Text      Navigate to ChatActivity as sender.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomOptionsScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onSign2Text: (roomCode: String) -> Unit,
    onVoice2Text: (roomCode: String) -> Unit,
) {
    Scaffold(
        topBar = { SignSpeakTopBar(onNavigateBack = onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Large top spacer mirrors the 180dp margin from the original XML
            verticalArrangement = Arrangement.Top
        ) {
            // Room Code label — visibility was "gone" in XML but shown here once available
            if (roomCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "Room Code: $roomCode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // The original XML had 180dp top margin on btnSign2Text measured from roomCodeText.
            // Replicate the visual gap with a Spacer.
            Spacer(modifier = Modifier.height(180.dp))

            // SIGN2TEXT button
            Button(
                onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("sign")
                    onSign2Text(roomCode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text(
                    text = "SIGN2TEXT",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // VOICE2TEXT button
            Button(
                onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("voice")
                    onVoice2Text(roomCode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text(
                    text = "VOICE2TEXT",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }
    }
}
