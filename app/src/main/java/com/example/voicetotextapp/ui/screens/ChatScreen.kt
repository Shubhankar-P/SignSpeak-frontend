package com.example.voicetotextapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voicetotextapp.ui.theme.PurpleLight
import com.example.voicetotextapp.ui.theme.PurplePrimary
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * Replaces: activity_chat.xml + ChatActivity
 *
 * @param roomCode          Room identifier.
 * @param role              "sender" | "receiver" — drives UI visibility.
 * @param initialPredicted  Optional pre-translated message from the previous screen.
 * @param onNavigateBack    Called when the user confirms leaving the room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomCode: String,
    role: String,
    initialPredicted: String? = null,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    // ── State ──────────────────────────────────────────────────────────────
    var outputText by remember {
        mutableStateOf(
            when {
                role == "sender" || role == "receiver" -> "Room created. Share this code with your friend."
                else -> "Waiting for message..."
            }
        )
    }
    var predictedText by remember { mutableStateOf(initialPredicted ?: "Predicted text will appear here...") }
    var speakEnabled by remember { mutableStateOf(true) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    val dbRef = remember {
        FirebaseDatabase
            .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("rooms/$roomCode")
    }

    // ── SpeechRecognizer ───────────────────────────────────────────────────
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    DisposableEffect(Unit) {
        // Notify the room that a receiver joined
        if (role == "receiver") {
            val deviceName = Build.MODEL ?: "Unknown Device"
            dbRef.child("joined").setValue(deviceName)
            Toast.makeText(context, "Connected to room successfully", Toast.LENGTH_SHORT).show()
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { outputText = "Ready to listen..." }
            override fun onBeginningOfSpeech() { outputText = "Listening..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { outputText = "Processing..." }
            override fun onError(error: Int) {
                outputText = "Error. Try again!"
                speakEnabled = true
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    outputText = text
                    dbRef.child("voiceMessage").setValue(text)
                }
                speakEnabled = true
            }
            override fun onPartialResults(partial: android.os.Bundle?) {}
            override fun onEvent(type: Int, params: android.os.Bundle?) {}
        })

        onDispose { speechRecognizer.destroy() }
    }

    // ── Firebase listeners ─────────────────────────────────────────────────
    LaunchedEffect(roomCode) {
        // Sign-message listener
        dbRef.child("signMessage").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null && msg != "Room created.") predictedText = msg
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Joined listener (notify sender)
        dbRef.child("joined").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deviceName = snapshot.getValue(String::class.java)
                if (deviceName != null && role == "sender") {
                    Toast.makeText(context, "$deviceName has joined the room", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ── Leave Room dialog ──────────────────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Room?") },
            text = { Text("Leaving will delete this room for everyone. Do you want to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms")
                        .child(roomCode)
                        .removeValue()
                    Toast.makeText(context, "Room deleted", Toast.LENGTH_SHORT).show()
                    showLeaveDialog = false
                    onNavigateBack()
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("No") }
            }
        )
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            SignSpeakTopBar(onNavigateBack = {
                if (role == "sender") showLeaveDialog = true else onNavigateBack()
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Room Code label
            Text(
                text = "Room Code:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = roomCode,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Output text block — purple background (holo_purple ≈ #FF9800 is orange;
            // the XML used @android:color/holo_purple which is #FF9000 on older APIs but
            // the app's theme uses purple, so we match the design intent here).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PurplePrimary, shape = RoundedCornerShape(4.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = outputText,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // "Live Sign Language Detection" section
            Text(
                text = "Live Sign Language Detection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = PurpleLight)
            ) {
                Text(
                    text = predictedText,
                    color = Color(0xFF2D2D2D),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Speak Now button — hidden for pure-receiver role with no speak capability
            if (role == "sender" || role == "receiver") {
                Button(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }

                        outputText = "Speak now..."
                        speakEnabled = false
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        }
                        speechRecognizer.startListening(speechIntent)
                    },
                    enabled = speakEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Text("🎙️ Speak Now")
                }
            }
        }
    }
}
