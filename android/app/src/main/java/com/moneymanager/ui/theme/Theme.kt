package com.moneymanager.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green   = Color(0xFF2E7D32)
private val Amber   = Color(0xFFF57F17)
private val Red     = Color(0xFFC62828)
private val Surface = Color(0xFFF5F5F5)

val BudgetGreen = Green
val BudgetAmber = Amber
val BudgetRed   = Red

private val LightColors = lightColorScheme(
    primary         = Color(0xFF1565C0),
    onPrimary       = Color.White,
    secondary       = Color(0xFF00695C),
    onSecondary     = Color.White,
    error           = Red,
    background      = Surface,
    surface         = Color.White,
)

@Composable
fun MoneyManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography  = Typography(),
        content     = content,
    )
}
