package com.anas.ufc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import com.anas.ufc.R

@Immutable
data class UfcExtendedColors(
    val red: Color,
    val darkRed: Color,
    val win: Color,
    val subtle: Color,
    val divider: Color,
    val avatar: Color,
)

val LocalUfcColors = staticCompositionLocalOf {
    UfcExtendedColors(Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent)
}

@Composable
fun UfcTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val extended = UfcExtendedColors(
        red = colorResource(R.color.ufc_red),
        darkRed = colorResource(R.color.ufc_dark_red),
        win = colorResource(R.color.win_green),
        subtle = colorResource(R.color.surface_subtle),
        divider = colorResource(R.color.surface_divider),
        avatar = colorResource(R.color.avatar_placeholder),
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = extended.red,
            onPrimary = colorResource(R.color.white),
            secondary = extended.win,
            background = colorResource(R.color.surface_background),
            surface = colorResource(R.color.surface_card),
            onSurface = colorResource(R.color.surface_ink),
            onSurfaceVariant = extended.subtle,
        )
    } else {
        lightColorScheme(
            primary = extended.red,
            onPrimary = colorResource(R.color.white),
            secondary = extended.win,
            background = colorResource(R.color.surface_background),
            surface = colorResource(R.color.surface_card),
            onSurface = colorResource(R.color.surface_ink),
            onSurfaceVariant = extended.subtle,
        )
    }
    CompositionLocalProvider(LocalUfcColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
