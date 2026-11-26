package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.StoryBackgroundCatalog
import com.example.util.StoryTextOverlayStyle

@Composable
fun StoryColorBackground(
    backgroundColorKey: String,
    modifier: Modifier = Modifier
) {
    val option = StoryBackgroundCatalog.byKey(backgroundColorKey)
        ?: StoryBackgroundCatalog.options.first()
    val startColor = Color(option.startColorArgb)
    val endColor = Color(option.endColorArgb)
    val glowColor = Color(option.accentGlowArgb)

    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(startColor, endColor)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(180f, 160f),
                        radius = 650f
                    )
                )
        )
    }
}

@Composable
fun StoryTextOverlayBubble(
    text: String,
    styleCode: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    val style = StoryTextOverlayStyle.fromCode(styleCode)
    val baseTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    when (style) {
        StoryTextOverlayStyle.PILL_DARK -> {
            Box(
                modifier = modifier
                    .widthIn(max = 300.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = baseTextStyle
                )
            }
        }

        StoryTextOverlayStyle.PILL_LIGHT -> {
            Box(
                modifier = modifier
                    .widthIn(max = 300.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = Color(0xFF111827),
                    style = baseTextStyle
                )
            }
        }

        StoryTextOverlayStyle.BOLD_CLASSIC -> {
            Box(
                modifier = modifier
                    .widthIn(max = 310.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = baseTextStyle.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 3f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }

        StoryTextOverlayStyle.NEON_GLOW -> {
            Box(
                modifier = modifier
                    .widthIn(max = 300.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B1020).copy(alpha = 0.78f))
                    .border(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF38BDF8), Color(0xFFEC4899))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = baseTextStyle.copy(
                        shadow = Shadow(
                            color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                            offset = Offset.Zero,
                            blurRadius = 12f
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun StoryOverlayPlacementBox(
    textOverlay: String,
    styleCode: String,
    positionY: Float,
    modifier: Modifier = Modifier,
    onPositionYChange: ((Float) -> Unit)? = null
) {
    if (textOverlay.isBlank()) return
    val verticalBias = ((positionY.coerceIn(0.18f, 0.82f)) * 2f) - 1f

    BoxWithConstraints(modifier = modifier) {
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val dragModifier = if (onPositionYChange != null) {
            Modifier.pointerInput(positionY, containerHeightPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaPct = dragAmount.y / containerHeightPx
                    onPositionYChange((positionY + deltaPct).coerceIn(0.18f, 0.82f))
                }
            }
        } else {
            Modifier
        }

        StoryTextOverlayBubble(
            text = textOverlay,
            styleCode = styleCode,
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = 0f, verticalBias = verticalBias))
                .padding(horizontal = 20.dp)
                .then(dragModifier)
                .testTag("story_text_overlay_bubble")
        )
    }
}
