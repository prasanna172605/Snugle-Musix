package com.snuggle.music.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies an Apple Liquid Glass frosted-glass style to a composable.
 * On API 31+: uses hardware-accelerated RenderEffect blur for true background blur.
 * On older APIs: falls back to a semi-transparent frosted overlay with a soft glow border.
 */
fun Modifier.liquidGlass(
    blurRadius: Dp = 20.dp,
    tintAlpha: Float = 0.08f,
    borderAlpha: Float = 0.18f,
    cornerRadius: Dp = 32.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .graphicsLayer {
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(
                        blurRadius.toPx(),
                        blurRadius.toPx(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    .asComposeRenderEffect()
                clip = true
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = tintAlpha + 0.04f),
                        Color.White.copy(alpha = tintAlpha),
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.4f),
                    )
                ),
                shape = shape
            )
    } else {
        this
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = tintAlpha + 0.12f),
                        Color.White.copy(alpha = tintAlpha + 0.06f),
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            )
    }
}
