package com.signspeak.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.signspeak.app.BuildConfig
import com.signspeak.app.ui.components.AppButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Tracks which mode the room creator has selected
private enum class SelectedMode { NONE, SIGN, VOICE }

@Composable
fun RoomOptionsScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onSign2Text: (roomCode: String) -> Unit,
    onVoice2Text: (roomCode: String) -> Unit,
) {
    var selectedMode by remember { mutableStateOf(SelectedMode.NONE) }

    Scaffold(
        topBar = {
            SignSpeakTopBar(
                title = "Mode Selection",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Phase 1: Mode selection (always visible, centered when no mode picked) ──
            if (selectedMode == SelectedMode.NONE) {
                // Vertically center the mode selection when QR is not yet shown
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

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

            ModeCard(
                icon = Icons.Default.PanTool,
                title = "Sign to Text",
                description = "Use your camera to translate sign language into text in real time",
                isSelected = selectedMode == SelectedMode.SIGN,
                onClick = {
                    selectedMode = SelectedMode.SIGN
                    FirebaseDatabase
                        .getInstance(BuildConfig.FIREBASE_URL)
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("sign")
                }
            )

            ModeCard(
                icon = Icons.Default.RecordVoiceOver,
                title = "Voice to Text",
                description = "Speak and your words are transcribed and shared with the room",
                isSelected = selectedMode == SelectedMode.VOICE,
                onClick = {
                    selectedMode = SelectedMode.VOICE
                    FirebaseDatabase
                        .getInstance(BuildConfig.FIREBASE_URL)
                        .getReference("rooms/$roomCode")
                        .child("mode").setValue("voice")
                }
            )

            if (selectedMode == SelectedMode.NONE) {
                Spacer(modifier = Modifier.weight(1f))
            }

            // ── Phase 2: QR card + Continue (only after mode is selected) ─────────────
            AnimatedVisibility(
                visible = selectedMode != SelectedMode.NONE,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (roomCode.isNotEmpty()) {
                        QrShareCard(roomCode = roomCode)
                    }

                    AppButton(
                        onClick = {
                            when (selectedMode) {
                                SelectedMode.SIGN  -> onSign2Text(roomCode)
                                SelectedMode.VOICE -> onVoice2Text(roomCode)
                                SelectedMode.NONE  -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Continue",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = 0.3.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun QrShareCard(roomCode: String) {
    val density = LocalDensity.current
    val sizePx = with(density) { 180.dp.roundToPx() }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(roomCode) {
        withContext(Dispatchers.Default) {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(
                roomCode, BarcodeFormat.QR_CODE, sizePx, sizePx, hints
            )
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(
                        x, y,
                        if (matrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            qrBitmap = bmp
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SHARE THIS ROOM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            // White bg required — QR codes won't scan on dark backgrounds
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code for room $roomCode",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = roomCode,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 6.sp
            )

            Text(
                text = "Ask the other person to scan this,\nor share the code manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline

    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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