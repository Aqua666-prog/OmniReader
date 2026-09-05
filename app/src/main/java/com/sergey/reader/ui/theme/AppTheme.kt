package com.sergey.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sergey.reader.data.settings.AppAppearance

private val Light = lightColorScheme(
    primary = Color(0xFF245C4A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EADF), onPrimaryContainer = Color(0xFF143C2F),
    secondary = Color(0xFF785B3F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E3CF), onSecondaryContainer = Color(0xFF49331E),
    tertiary = Color(0xFF825452),
    background = Color(0xFFF8F6F0), onBackground = Color(0xFF252C28),
    surface = Color(0xFFFFFDF8), onSurface = Color(0xFF252C28),
    surfaceVariant = Color(0xFFE8EAE1), onSurfaceVariant = Color(0xFF596158),
    surfaceContainerLowest = Color(0xFFFFFDF8), surfaceContainerLow = Color(0xFFF3F1EA),
    surfaceContainer = Color(0xFFEEEEE5), surfaceContainerHigh = Color(0xFFE8E9DF),
    surfaceContainerHighest = Color(0xFFE2E4DA),
    outline = Color(0xFF747E72), outlineVariant = Color(0xFFD5D9CE)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA6D4B9), onPrimary = Color(0xFF103828),
    primaryContainer = Color(0xFF284D3D), onPrimaryContainer = Color(0xFFD1ECDA),
    secondary = Color(0xFFDFC3A0), onSecondary = Color(0xFF402D18),
    secondaryContainer = Color(0xFF4B3C2C), onSecondaryContainer = Color(0xFFF3DFC3),
    tertiary = Color(0xFFE4B5B0),
    background = Color(0xFF121814), onBackground = Color(0xFFE4E7DE),
    surface = Color(0xFF18201B), onSurface = Color(0xFFE4E7DE),
    surfaceVariant = Color(0xFF303C33), onSurfaceVariant = Color(0xFFB6C2B6),
    surfaceContainerLowest = Color(0xFF101611), surfaceContainerLow = Color(0xFF19221B),
    surfaceContainer = Color(0xFF1F2921), surfaceContainerHigh = Color(0xFF29332B),
    surfaceContainerHighest = Color(0xFF343E35),
    outline = Color(0xFF8B998B), outlineVariant = Color(0xFF3D493E)
)
private val EditorialTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
)
@Composable
fun ReaderAppTheme(appearance: AppAppearance = AppAppearance.SYSTEM, manageSystemBars: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    if (manageSystemBars) SystemBars(if (dark) Dark.background else Light.background, darkIcons = !dark)
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = EditorialTypography,
        shapes = Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(10.dp), RoundedCornerShape(16.dp), RoundedCornerShape(24.dp), RoundedCornerShape(32.dp)),
        content = content
    )
}
