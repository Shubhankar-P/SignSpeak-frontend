package com.signspeak.app.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signspeak.app.R
import com.signspeak.app.ui.components.AppButton
import com.signspeak.app.ui.components.AppOutlinedButton
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Login
import com.signspeak.app.BuildConfig
import androidx.compose.foundation.layout.size

@Composable
fun MainScreen(
    onRoomCreated: (roomCode: String) -> Unit,
    onJoinAsReceiver: (roomCode: String) -> Unit,
    onJoinAsSign: (roomCode: String) -> Unit,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ){ innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Hero image with gradient fade at the bottom ────────────────
            // ── Full-bleed logo hero ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.asl),
                        contentDescription = "SignSpeak logo",
                        modifier = Modifier.size(120.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SignSpeak",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Bridge every conversation",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Bottom sheet-style action area ────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                Text(
//                    text = "Bridge any conversation",
//                    style = MaterialTheme.typography.titleMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )

                Spacer(modifier = Modifier.height(4.dp))

                AppButton(
                    onClick = {
                        val roomCode = generateRoomCode()
                        FirebaseDatabase
                            .getInstance(BuildConfig.FIREBASE_URL)
                            .getReference("rooms/$roomCode")
                            .child("message").setValue("Room created.")
                        onRoomCreated(roomCode)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddBox,
                        contentDescription = "Create Room"
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Create Room",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.3.sp
                    )
                }

                AppOutlinedButton(
                    text = "Join Room",
                    onClick = { showJoinRoomDialog(context, onJoinAsReceiver, onJoinAsSign) },
                    icon = Icons.Default.Login
                )
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..5).map { chars.random() }.joinToString("")
}

private fun showJoinRoomDialog(
    context: Context,
    onJoinAsReceiver: (String) -> Unit,
    onJoinAsSign: (String) -> Unit,
) {
    val input = EditText(context).apply { hint = "Enter Room Code" }
    AlertDialog.Builder(context)
        .setTitle("Join Room")
        .setView(input)
        .setPositiveButton("Join") { _, _ ->
            val roomCode = input.text.toString().trim()
            if (roomCode.isEmpty()) {
                Toast.makeText(context, "Please enter a room code", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val dbRef = FirebaseDatabase
                .getInstance(BuildConfig.FIREBASE_URL)
                .getReference("rooms/$roomCode")
            dbRef.get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        when (snapshot.child("mode").getValue(String::class.java)) {
                            "sign"  -> onJoinAsReceiver(roomCode)
                            "voice" -> onJoinAsSign(roomCode)
                            else    -> Toast.makeText(context, "Room mode not selected yet", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Invalid Room Code", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error checking room", Toast.LENGTH_SHORT).show()
                }
        }
        .setNegativeButton("Cancel", null)
        .show()
}
