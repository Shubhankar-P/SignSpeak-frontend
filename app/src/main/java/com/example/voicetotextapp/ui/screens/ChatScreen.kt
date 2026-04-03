package com.example.voicetotextapp.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voicetotextapp.ui.components.AppButton
import com.example.voicetotextapp.ui.components.AppCard
import com.example.voicetotextapp.ui.components.RoomCodeBadge
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon

@Composable
fun ChatScreen(
    roomCode: String,
    role: String,
    initialPredicted: String? = null,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    var outputText by remember {
        mutableStateOf(
            if (role == "sender" || role == "receiver")
                "Room created. Share this code with your friend."
            else "Waiting for message..."
        )
    }
    var predictedText by remember { mutableStateOf(initialPredicted ?: "Predicted sign language text will appear here…") }
    var speakEnabled by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
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
    ) {}

    DisposableEffect(Unit) {
        if (role == "receiver") {
            dbRef.child("joined").setValue(Build.MODEL ?: "Unknown Device")
            Toast.makeText(context, "Connected to room successfully", Toast.LENGTH_SHORT).show()
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: android.os.Bundle?) { outputText = "Ready…" }
            override fun onBeginningOfSpeech() { outputText = "Listening…"; isListening = true }
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { outputText = "Processing…"; isListening = false }
            override fun onError(e: Int) { outputText = "Couldn't catch that. Try again."; speakEnabled = true; isListening = false }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    outputText = matches[0]
                    dbRef.child("voiceMessage").setValue(matches[0])
                }
                speakEnabled = true
                isListening = false
            }
            override fun onPartialResults(p: android.os.Bundle?) {}
            override fun onEvent(t: Int, p: android.os.Bundle?) {}
        })

        onDispose { speechRecognizer.destroy() }
    }

    LaunchedEffect(roomCode) {
        dbRef.child("signMessage").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null && msg != "Room created.") predictedText = msg
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        dbRef.child("joined").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java)
                if (name != null && role == "sender")
                    Toast.makeText(context, "$name has joined the room", Toast.LENGTH_SHORT).show()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    // ── Pulse animation for the mic button ────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.06f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseScale"
    )

    // ── Leave dialog ───────────────────────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Room?") },
            text  = { Text("Leaving will delete this room for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseDatabase
                        .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("rooms").child(roomCode).removeValue()
                    Toast.makeText(context, "Room deleted", Toast.LENGTH_SHORT).show()
                    showLeaveDialog = false
                    onNavigateBack()
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Stay") }
            }
        )
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            SignSpeakTopBar(onNavigateBack = {
                if (role == "sender") showLeaveDialog = true else onNavigateBack()
            })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Room code badge
            RoomCodeBadge(roomCode = roomCode, modifier = Modifier.align(Alignment.Start))

            // ── Output / status card ───────────────────────────────────────
            val borderColor by animateColorAsState(
                targetValue = if (isListening)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                animationSpec = tween(400),
                label = "borderColor"
            )

            AppCard(
                modifier = Modifier.border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Voice Output",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = outputText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Sign language prediction card ──────────────────────────────
            AppCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "LIVE SIGN DETECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = predictedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Speak button ───────────────────────────────────────────────
            if (role == "sender" || role == "receiver") {
                AppButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@AppButton
                        }

                        outputText = "Speak now…"
                        speakEnabled = false

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        }

                        speechRecognizer.startListening(intent)
                    },
                    enabled = speakEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isListening) Modifier.scale(pulseScale) else Modifier)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Speak"
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isListening) "Listening…" else "Speak Now",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
