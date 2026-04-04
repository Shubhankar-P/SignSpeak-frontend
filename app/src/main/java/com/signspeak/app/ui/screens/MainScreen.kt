package com.signspeak.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.signspeak.app.BuildConfig
import com.signspeak.app.R
import com.signspeak.app.ui.components.AppButton
import com.signspeak.app.ui.components.AppOutlinedButton
import java.util.concurrent.Executors

@Composable
fun MainScreen(
    onRoomCreated: (roomCode: String) -> Unit,
    onJoinAsReceiver: (roomCode: String) -> Unit,
    onJoinAsSign: (roomCode: String) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    var showJoinSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            // ── Bottom action area ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    onClick = { showJoinSheet = true },
                    icon = Icons.Default.Login
                )
            }
        }

        if (showJoinSheet) {
            JoinRoomSheet(
                onDismiss = { showJoinSheet = false },
                onJoinAsReceiver = { code -> showJoinSheet = false; onJoinAsReceiver(code) },
                onJoinAsSign = { code -> showJoinSheet = false; onJoinAsSign(code) }
            )
        }
    }
}

// ── Join Room Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinRoomSheet(
    onDismiss: () -> Unit,
    onJoinAsReceiver: (String) -> Unit,
    onJoinAsSign: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var manualCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var scanComplete by remember { mutableStateOf(false) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    fun checkAndJoin(roomCode: String) {
        if (isChecking || scanComplete) return
        val code = roomCode.trim().uppercase()
        if (code.isEmpty()) { errorMessage = "Please enter a room code"; return }
        isChecking = true
        errorMessage = null
        FirebaseDatabase
            .getInstance(BuildConfig.FIREBASE_URL)
            .getReference("rooms/$code")
            .get()
            .addOnSuccessListener { snapshot ->
                isChecking = false
                if (snapshot.exists()) {
                    when (snapshot.child("mode").getValue(String::class.java)) {
                        "sign"  -> { scanComplete = true; onJoinAsReceiver(code) }
                        "voice" -> { scanComplete = true; onJoinAsSign(code) }
                        else    -> errorMessage = "Room mode not selected yet"
                    }
                } else {
                    errorMessage = "Room not found"
                }
            }
            .addOnFailureListener { errorMessage = "Connection error. Try again." }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Join Room",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Camera scanner ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (cameraGranted && !scanComplete) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()

                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(previewView.surfaceProvider)

                                val scanner = BarcodeScanning.getClient(
                                    BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                )

                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                analysis.setAnalyzer(cameraExecutor) { proxy ->
                                    val mediaImage = proxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage, proxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull()?.rawValue?.let { code ->
                                                    checkAndJoin(code)
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } else {
                                        proxy.close()
                                    }
                                }

                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── Corner bracket targeting overlay ───────────────────
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            val stroke = 3.dp.toPx()
                            val arm = 28.dp.toPx()
                            val c = Color(0xFF6C5CE7)
                            val w = size.width; val h = size.height
                            drawLine(c, Offset(0f, 0f), Offset(arm, 0f), stroke, StrokeCap.Round)
                            drawLine(c, Offset(0f, 0f), Offset(0f, arm), stroke, StrokeCap.Round)
                            drawLine(c, Offset(w, 0f), Offset(w - arm, 0f), stroke, StrokeCap.Round)
                            drawLine(c, Offset(w, 0f), Offset(w, arm), stroke, StrokeCap.Round)
                            drawLine(c, Offset(0f, h), Offset(arm, h), stroke, StrokeCap.Round)
                            drawLine(c, Offset(0f, h), Offset(0f, h - arm), stroke, StrokeCap.Round)
                            drawLine(c, Offset(w, h), Offset(w - arm, h), stroke, StrokeCap.Round)
                            drawLine(c, Offset(w, h), Offset(w, h - arm), stroke, StrokeCap.Round)
                        }
                    }
                } else if (!cameraGranted) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera permission needed to scan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant permission")
                        }
                    }
                }

                // Loading overlay while Firebase checks the code
                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text(
                text = "Point your camera at the QR code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Divider ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "or type manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ── Manual entry ───────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = {
                        manualCode = it.uppercase().take(5)
                        errorMessage = null
                    },
                    placeholder = { Text("Enter room code") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (manualCode.length == 5) checkAndJoin(manualCode) }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                AppButton(
                    onClick = { checkAndJoin(manualCode) },
                    enabled = manualCode.length == 5 && !isChecking,
                ) {
                    Text(
                        text = "Join",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..5).map { chars.random() }.joinToString("")
}