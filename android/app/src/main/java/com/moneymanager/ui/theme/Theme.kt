package com.moneymanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand palette (mirrors Tailwind dark theme) ───────────────────────────────
val Emerald500  = Color(0xFF10B981)
val Emerald900  = Color(0xFF064E3B)
val Red500      = Color(0xFFEF4444)
val Red900      = Color(0xFF7F1D1D)
val Zinc950     = Color(0xFF09090B)
val Zinc900     = Color(0xFF18181B)
val Zinc800     = Color(0xFF27272A)
val Zinc700     = Color(0xFF3F3F46)
val Zinc400     = Color(0xFFA1A1AA)
val Zinc200     = Color(0xFFE4E4E7)
val Violet500   = Color(0xFF8B5CF6)
val Amber400    = Color(0xFFFBBF24)
val Amber950    = Color(0xFF451A03)

private val DarkColors = darkColorScheme(
    primary         = Emerald500,
    onPrimary       = Color.Black,
    primaryContainer   = Emerald900,
    onPrimaryContainer = Emerald500,
    secondary       = Violet500,
    onSecondary     = Color.White,
    error           = Red500,
    onError         = Color.White,
    background      = Zinc950,
    onBackground    = Zinc200,
    surface         = Zinc900,
    onSurface       = Zinc200,
    surfaceVariant  = Zinc800,
    onSurfaceVariant = Zinc400,
    outline         = Zinc700,
)

@Composable
fun MoneyManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
