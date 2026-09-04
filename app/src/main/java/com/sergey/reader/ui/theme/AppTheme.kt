package com.sergey.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF67508F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF24143E),
    secondary = Color(0xFF665A70),
    secondaryContainer = Color(0xFFEDE0F3),
    background = Color(0xFFFCF9FD),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE9E2EB),
    onSurface = Color(0xFF201D21),
    onSurfaceVariant = Color(0xFF4C454E),
    outline = Color(0xFF7E757F)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD3BCFD),
    onPrimary = Color(0xFF382457),
    primaryContainer = Color(0xFF503B72),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFD2C2D8),
    secondaryContainer = Color(0xFF4D4355),
    background = Color(0xFF121014),
    surface = Color(0xFF1A171D),
    surfaceVariant = Color(0xFF49434C),
    onSurface = Color(0xFFEAE5EA),
    onSurfaceVariant = Color(0xFFCDC5CF),
    outline = Color(0xFF978E99)
)

private val ReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ReaderAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = ReaderShapes,
        content = content
    )
}
