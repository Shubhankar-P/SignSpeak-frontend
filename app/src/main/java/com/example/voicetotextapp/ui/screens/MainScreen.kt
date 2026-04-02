package com.example.voicetotextapp.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.voicetotextapp.R
import com.google.firebase.database.FirebaseDatabase

/**
 * Replaces: activity_main.xml + MainActivity
 *
 * Navigation callbacks are passed in so this composable stays side-effect free
 * and easy to preview/test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRoomCreated: (roomCode: String) -> Unit,
    onJoinAsReceiver: (roomCode: String) -> Unit,
    onJoinAsSign: (roomCode: String) -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = { SignSpeakTopBar() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ASL image — replaces ImageView (height 400dp)
            Image(
                painter = painterResource(id = R.drawable.asl),
                contentDescription = "ASL illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Create Room button
            Button(
                onClick = {
                    val roomCode = generateRoomCode()
                    val dbRef = FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms/$roomCode")
                    dbRef.child("message").setValue("Room created.")
                    onRoomCreated(roomCode)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Create Room")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Join Room button — shows the same AlertDialog as before
            Button(
                onClick = { showJoinRoomDialog(context, onJoinAsReceiver, onJoinAsSign) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Join Room")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers (unchanged logic from MainActivity.kt)
// ---------------------------------------------------------------------------

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..5).map { chars.random() }.joinToString("")
}

/**
 * Shows the join-room dialog.
 * Uses the classic AlertDialog because Compose AlertDialog doesn't support
 * an arbitrary View as content (needed for the plain EditText here).
 * You can replace this with a Compose Dialog + OutlinedTextField if preferred.
 */
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
                .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
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
