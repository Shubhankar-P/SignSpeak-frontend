package com.signspeak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = Violet500,
    onPrimary          = Color.White,
    primaryContainer   = Violet600,
    onPrimaryContainer = Color.White,
    secondary          = Violet400,
    onSecondary        = Color.White,
    background         = Dark900,
    onBackground       = TextPrimary,
    surface            = Dark800,
    onSurface          = TextPrimary,
    surfaceVariant     = Dark700,
    onSurfaceVariant   = TextSecondary,
    outline            = Dark600,
    error              = Error,
    onError            = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary            = Violet500,
    onPrimary          = Color.White,
    primaryContainer   = Light200,
    onPrimaryContainer = Violet600,
    secondary          = Violet400,
    onSecondary        = Color.White,
    background         = Light50,
    onBackground       = TextOnLight,
    surface            = Color.White,
    onSurface          = TextOnLight,
    surfaceVariant     = Light100,
    onSurfaceVariant   = TextSecOnLight,
    outline            = Light200,
    error              = Error,
    onError            = Color.White,
)

@Composable
fun VoiceToTextAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
