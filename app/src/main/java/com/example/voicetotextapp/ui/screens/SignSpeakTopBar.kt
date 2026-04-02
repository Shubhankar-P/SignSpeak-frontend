package com.example.voicetotextapp.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Reusable top bar replacing the custom_toolbar.xml include.
 *
 * @param onNavigateBack  If non-null, shows a back arrow and calls this lambda when tapped.
 *                        Pass null on screens that have no back navigation (e.g. MainActivity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignSpeakTopBar(onNavigateBack: (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Text(
                text = "SignSpeak",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}
