package com.hackathon.offlinewallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WalletColorScheme = lightColorScheme(
    primary = Color(0xFF00A3E0), // Paytm-inspired blue
    secondary = Color(0xFF4CAF50), // Green for success/confirm
    background = Color(0xFFF5F7FA), // Light gray background
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = Color(0xFFD32F2F)
)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WalletColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}