package com.personaltracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6C5CE7),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE8E5FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF636E8A),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E4),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF31111D),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFF),
    background = androidx.compose.ui.graphics.Color(0xFFF8F7FC),
    error = androidx.compose.ui.graphics.Color(0xFFE74C3C),
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA29BFE),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF1A1040),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3D3580),
    secondary = androidx.compose.ui.graphics.Color(0xFFBCC5DC),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF633B48),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E4),
    surface = androidx.compose.ui.graphics.Color(0xFF1C1B20),
    background = androidx.compose.ui.graphics.Color(0xFF131218),
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
)

@Composable
fun PersonalTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        content = content
    )
}
