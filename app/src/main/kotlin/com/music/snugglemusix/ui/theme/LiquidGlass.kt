package com.snuggle.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.dp
import com.snuggle.music.ui.component.LocalAppBackdrop
import com.snuggle.music.ui.component.LocalGlassEffectConfig
import com.snuggle.music.ui.component.liquidGlass as opticalLiquidGlass

/**
 * Compatibility bridge for liquidGlass modifier.
 * Uses real optical backdrop-based refraction when a backdrop is present in CompositionLocal.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    borderAlpha: Float = 0.22f,
    scrimAlpha: Float = 0.4f
): Modifier {
    val backdrop = LocalAppBackdrop.current
    return if (backdrop != null) {
        val config = LocalGlassEffectConfig.current
        this.opticalLiquidGlass(
            config = config,
            shape = shape,
            applyEdgeEffects = false
        )
    } else {
        this
            .background(
                color = Color.Black.copy(alpha = scrimAlpha),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            )
    }
}
