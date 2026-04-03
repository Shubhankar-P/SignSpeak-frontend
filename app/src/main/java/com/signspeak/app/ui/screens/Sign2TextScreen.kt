package com.signspeak.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.signspeak.app.ui.components.AppButton
import com.signspeak.app.ui.components.AppCard
import com.signspeak.app.ui.components.AppOutlinedButton
import com.signspeak.app.ui.components.RoomCodeBadge
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

// ── Builder mode ───────────────────────────────────────────────────────────
private enum class BuilderMode { SENTENCE, WORD }

@Composable
fun Sign2TextScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live gesture prediction from socket (always latest detected gesture)
    var liveGesture by remember { mutableStateOf("") }
    var chatMessage by remember { mutableStateOf("Waiting for voice message…") }

    // Builder state
    var builderMode by remember { mutableStateOf(BuilderMode.SENTENCE) }
    val sentenceWords = remember { mutableStateListOf<String>() }
    val wordLetters   = remember { mutableStateListOf<String>() }

    // ── Firebase ───────────────────────────────────────────────────────────
    val dbRef = remember {
        FirebaseDatabase
            .getInstance("https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("rooms/$roomCode")
    }

    LaunchedEffect(roomCode) {
        dbRef.child("voiceMessage").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null && msg != "Room created.") chatMessage = msg
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ── TTS ────────────────────────────────────────────────────────────────
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) {
        tts.language = Locale.US
        onDispose { tts.shutdown() }
    }

    // ── Socket.IO ──────────────────────────────────────────────────────────
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var lastSentTime by remember { mutableStateOf(0L) }

    val socket: Socket = remember {
        IO.socket("https://archimedes-pragmatic-maliah.ngrok-free.dev")
    }

    DisposableEffect(Unit) {
        socket.on(Socket.EVENT_CONNECT) { Log.d("SOCKET", "Connected") }
        socket.on(Socket.EVENT_CONNECT_ERROR) { Log.e("SOCKET", "Connection error") }
        socket.on("prediction") { args ->
            val data = args[0] as JSONObject
            // Only update liveGesture — builder accumulation is always manual (button press)
            liveGesture = data.getString("text")
        }
        socket.connect()
        onDispose { socket.disconnect(); cameraExecutor.shutdown() }
    }

    // ── Camera permission ──────────────────────────────────────────────────
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }

    LaunchedEffect(Unit) { if (!cameraGranted) permLauncher.launch(Manifest.permission.CAMERA) }

    // ── Derived strings ────────────────────────────────────────────────────
    val builtSentence = sentenceWords.joinToString(" ")
    val builtWord     = wordLetters.joinToString("")

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = { SignSpeakTopBar(onNavigateBack = onNavigateBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Room code + mode toggle chip ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoomCodeBadge(roomCode = roomCode)
                ModeChip(
                    isSentenceMode = builderMode == BuilderMode.SENTENCE,
                    onToggle = {
                        if (builderMode == BuilderMode.SENTENCE) {
                            builderMode = BuilderMode.WORD
                            wordLetters.clear()
                        } else {
                            builderMode = BuilderMode.SENTENCE
                            wordLetters.clear()
                        }
                    }
                )
            }

            // ── Camera preview ─────────────────────────────────────────────
            if (cameraGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(cameraExecutor) { image ->
                                val now = System.currentTimeMillis()
                                if (now - lastSentTime < 500) { image.close(); return@setAnalyzer }
                                lastSentTime = now
                                try {
                                    var bmp = imageProxyToBitmap(image)
                                    val rot = image.imageInfo.rotationDegrees
                                    if (rot != 0) {
                                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                    }
                                    val scaled = Bitmap.createScaledBitmap(bmp, 320, (bmp.height * 320f / bmp.width).toInt(), true)
                                    val out = ByteArrayOutputStream()
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
                                    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    socket.emit("frame", "data:image/jpeg;base64,$b64")
                                } catch (e: Exception) { e.printStackTrace() }
                                finally { image.close() }
                            }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission required", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Live gesture field (read-only) ─────────────────────────────
            OutlinedTextField(
                value = liveGesture,
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        text = if (builderMode == BuilderMode.SENTENCE)
                            "Detected gesture appears here…"
                        else
                            "Detected letter appears here…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = "Live Detection",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Builder progress card — only visible when something is built ─
            AnimatedVisibility(
                visible = (builderMode == BuilderMode.SENTENCE && sentenceWords.isNotEmpty())
                        || (builderMode == BuilderMode.WORD && wordLetters.isNotEmpty()),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit  = fadeOut() + slideOutVertically { it / 2 }
            ) {
                BuilderCard(mode = builderMode, builtWord = builtWord, builtSentence = builtSentence)
            }

            // ── Action buttons — layout stays fixed, only labels swap ──────
            if (builderMode == BuilderMode.SENTENCE) {
                SentenceActionRow(
                    canSend    = sentenceWords.isNotEmpty(),
                    onNextWord = { if (liveGesture.isNotBlank()) sentenceWords.add(liveGesture.trim()) },
                    onSend     = {
                        dbRef.child("signMessage").setValue(builtSentence)
                        sentenceWords.clear()
                        liveGesture = ""
                    }
                )
            } else {
                WordActionRow(
                    canFinish    = wordLetters.isNotEmpty(),
                    onNextLetter = { if (liveGesture.isNotBlank()) wordLetters.add(liveGesture.trim()) },
                    onDone       = {
                        if (wordLetters.isNotEmpty()) sentenceWords.add(builtWord)
                        wordLetters.clear()
                        builderMode = BuilderMode.SENTENCE
                    }
                )
            }

            // ── Live voice message card ────────────────────────────────────
            AppCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LIVE VOICE MESSAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { tts.speak(chatMessage, TextToSpeech.QUEUE_FLUSH, null, null) },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = chatMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────

/**
 * Small text button in the top-right corner that toggles mode.
 * Shows "Word Builder" in sentence mode, "✕ Cancel Word" in word mode.
 */
@Composable
private fun ModeChip(isSentenceMode: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(
            text = if (isSentenceMode) "Word Builder" else "✕  Cancel",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSentenceMode)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Card showing accumulated letters (word mode) or words (sentence mode).
 * Only rendered when there's something to show (parent AnimatedVisibility handles that).
 */
@Composable
private fun BuilderCard(mode: BuilderMode, builtWord: String, builtSentence: String) {
    AppCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = if (mode == BuilderMode.WORD) "BUILDING WORD" else "BUILDING SENTENCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (mode == BuilderMode.WORD) builtWord else builtSentence,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Monospace in word mode makes individual letters easier to read
                fontFamily = if (mode == BuilderMode.WORD) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Action row for sentence mode.
 */
@Composable
private fun SentenceActionRow(
    canSend: Boolean,
    onNextWord: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppOutlinedButton(
            text = "Next Word",
            onClick = onNextWord,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.weight(1f)
        ) {
            Text("Send", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}

/**
 * Action row for word mode.
 */
@Composable
private fun WordActionRow(
    canFinish: Boolean,
    onNextLetter: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppOutlinedButton(
            text = "Next Letter",
            onClick = onNextLetter,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            onClick = onDone,
            enabled = canFinish,
            modifier = Modifier.weight(1f)
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining(); val uSize = uBuffer.remaining(); val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize); vBuffer.get(nv21, ySize, vSize); uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 60, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}