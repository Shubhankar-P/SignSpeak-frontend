package com.example.voicetotextapp.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.voicetotextapp.ui.theme.PurpleDeep
import com.example.voicetotextapp.ui.theme.PurpleLight
import com.example.voicetotextapp.ui.theme.PurplePrimary
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sign2TextScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var translatedText by remember { mutableStateOf("") }
    var chatMessage by remember { mutableStateOf("🎧 Waiting for voice message...") }

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

    // ── TextToSpeech ───────────────────────────────────────────────────────
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
        socket.on(Socket.EVENT_CONNECT) { Log.d("SOCKET", "Backend Connected") }
        socket.on(Socket.EVENT_CONNECT_ERROR) { Log.e("SOCKET", "Backend Connection Error") }
        socket.on("prediction") { args ->
            val data = args[0] as JSONObject
            val text = data.getString("text")
            translatedText = text
            dbRef.child("signMessage").setValue(text)
        }
        socket.connect()
        onDispose {
            socket.disconnect()
            cameraExecutor.shutdown()
        }
    }

    // ── Camera permission ──────────────────────────────────────────────────
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = { SignSpeakTopBar(onNavigateBack = onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),  // allows scroll on small screens
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Room Code
            Text(
                text = "Room Code : $roomCode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Camera preview — aspect ratio 4:3 scales proportionately on all screen sizes
            if (cameraPermissionGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                                val now = System.currentTimeMillis()
                                if (now - lastSentTime < 500) { image.close(); return@setAnalyzer }
                                lastSentTime = now
                                try {
                                    var bitmap = imageProxyToBitmap(image)
                                    val rotation = image.imageInfo.rotationDegrees
                                    if (rotation != 0) {
                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    val scaled = Bitmap.createScaledBitmap(
                                        bitmap, 320, (bitmap.height * (320f / bitmap.width)).toInt(), true
                                    )
                                    val out = ByteArrayOutputStream()
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
                                    val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    socket.emit("frame", "data:image/jpeg;base64,$base64")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    image.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)   // ← key fix: scales with screen width, not fixed px
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    text = "Camera permission required",
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }

            // Translated text field
            OutlinedTextField(
                value = translatedText,
                onValueChange = { translatedText = it },
                placeholder = { Text("Translated text") },
                modifier = Modifier.fillMaxWidth()
            )

            // Reset + Speak buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { translatedText = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Reset") }

                Button(
                    onClick = { tts.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, null) },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Speak") }
            }

            // Live Voice Message section
            Text(
                text = "🟣 Live Voice Message",
                color = PurpleDeep,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = PurpleLight)
            ) {
                Text(
                    text = chatMessage,
                    color = Color.Black,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 60, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}
