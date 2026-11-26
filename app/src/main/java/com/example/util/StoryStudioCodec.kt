package com.example.util

import kotlin.math.roundToInt

data class StoryBackgroundOption(
    val key: String,
    val label: String,
    val startColorArgb: Long,
    val endColorArgb: Long,
    val accentGlowArgb: Long
)

object StoryBackgroundCatalog {
    val options: List<StoryBackgroundOption> = listOf(
        StoryBackgroundOption(
            key = "sunset",
            label = "Sunset",
            startColorArgb = 0xFFFF5E62L,
            endColorArgb = 0xFFFF9966L,
            accentGlowArgb = 0xFFFFD194L
        ),
        StoryBackgroundOption(
            key = "maroon",
            label = "Momi Rose",
            startColorArgb = 0xFF8B1D3BL,
            endColorArgb = 0xFF2D0A18L,
            accentGlowArgb = 0xFFD92D4EL
        ),
        StoryBackgroundOption(
            key = "midnight",
            label = "Midnight",
            startColorArgb = 0xFF0F172AL,
            endColorArgb = 0xFF1E3A8AL,
            accentGlowArgb = 0xFF38BDF8L
        ),
        StoryBackgroundOption(
            key = "ocean",
            label = "Ocean",
            startColorArgb = 0xFF0284C7L,
            endColorArgb = 0xFF0D9488L,
            accentGlowArgb = 0xFF5EEAD4L
        ),
        StoryBackgroundOption(
            key = "emerald",
            label = "Emerald",
            startColorArgb = 0xFF065F46L,
            endColorArgb = 0xFF10B981L,
            accentGlowArgb = 0xFF6EE7B7L
        ),
        StoryBackgroundOption(
            key = "violet",
            label = "Violet",
            startColorArgb = 0xFF4C1D95L,
            endColorArgb = 0xFFEC4899L,
            accentGlowArgb = 0xFFF9A8D4L
        ),
        StoryBackgroundOption(
            key = "amber",
            label = "Amber",
            startColorArgb = 0xFFB45309L,
            endColorArgb = 0xFFF59E0BL,
            accentGlowArgb = 0xFFFDE68AL
        ),
        StoryBackgroundOption(
            key = "noir",
            label = "Noir",
            startColorArgb = 0xFF111827L,
            endColorArgb = 0xFF374151L,
            accentGlowArgb = 0xFF9CA3AFL
        )
    )

    fun byKey(key: String?): StoryBackgroundOption? {
        val normalized = key?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        return options.firstOrNull { it.key == normalized }
    }

    fun isColorKey(key: String?): Boolean = byKey(key) != null
}

enum class StoryTextOverlayStyle(val code: String, val label: String) {
    PILL_DARK("D", "Dark Card"),
    PILL_LIGHT("L", "Light Card"),
    BOLD_CLASSIC("B", "Classic"),
    NEON_GLOW("N", "Neon");

    companion object {
        fun fromCode(code: String?): StoryTextOverlayStyle =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: PILL_DARK
    }
}

data class DecodedStoryOverlay(
    val textOverlay: String = "",
    val styleCode: String = StoryTextOverlayStyle.PILL_DARK.code,
    val positionY: Float = 0.5f,
    val caption: String = ""
)

object StoryOverlayCodec {
    const val BG_KEY_PREFIX = "bg:"
    const val DEFAULT_STORY_TTL_MS = 86_400_000L
    private const val OVERLAY_PREFIX = "[[ov:"
    private const val OVERLAY_SUFFIX = "]]"
    private const val MAX_IMAGE_KEY_LEN = 64
    private const val MAX_CAPTION_LEN = 300

    fun encodeImageKey(imageKey: String, backgroundColorKey: String): String {
        val bg = backgroundColorKey.trim().lowercase()
        return if (bg.isNotBlank()) {
            "$BG_KEY_PREFIX$bg".take(MAX_IMAGE_KEY_LEN)
        } else {
            imageKey.trim().ifBlank { "img_post_coastal" }.take(MAX_IMAGE_KEY_LEN)
        }
    }

    fun decodeBackgroundColorKey(rawImageKey: String?): String {
        val raw = rawImageKey?.trim().orEmpty()
        if (!raw.startsWith(BG_KEY_PREFIX)) return ""
        return raw.removePrefix(BG_KEY_PREFIX).trim().lowercase().take(40)
    }

    fun encodeCaptionWithOverlay(
        caption: String,
        textOverlay: String,
        styleCode: String,
        positionY: Float
    ): String {
        val cleanCaption = caption.trim()
        val cleanOverlay = textOverlay.trim()
        if (cleanOverlay.isBlank()) {
            return cleanCaption.take(MAX_CAPTION_LEN)
        }
        val safeStyle = StoryTextOverlayStyle.fromCode(styleCode).code
        val yPct = (positionY.coerceIn(0.18f, 0.82f) * 100f).roundToInt()
        val header = "$OVERLAY_PREFIX$safeStyle:$yPct:"
        val maxEscapedOverlayLen = (MAX_CAPTION_LEN - header.length - OVERLAY_SUFFIX.length).coerceAtLeast(0)
        val escapedOverlay = truncateEscapeSafe(escapeOverlayText(cleanOverlay), maxEscapedOverlayLen)
        val prefix = "$header$escapedOverlay$OVERLAY_SUFFIX"
        val remainingCaptionSpace = (MAX_CAPTION_LEN - prefix.length).coerceAtLeast(0)
        return if (remainingCaptionSpace > 0 && cleanCaption.isNotEmpty()) {
            prefix + cleanCaption.take(remainingCaptionSpace)
        } else {
            prefix
        }
    }

    fun decodeCaptionWithOverlay(rawCaption: String?): DecodedStoryOverlay {
        val raw = rawCaption.orEmpty()
        if (!raw.startsWith(OVERLAY_PREFIX)) {
            return DecodedStoryOverlay(caption = raw)
        }
        val endIdx = raw.indexOf(OVERLAY_SUFFIX, startIndex = OVERLAY_PREFIX.length)
        if (endIdx < 0) {
            return DecodedStoryOverlay(caption = raw)
        }
        val body = raw.substring(OVERLAY_PREFIX.length, endIdx)
        val parts = body.split(":", limit = 3)
        if (parts.size < 3) {
            return DecodedStoryOverlay(caption = raw)
        }
        val styleCode = StoryTextOverlayStyle.fromCode(parts[0]).code
        val positionY = (parts[1].toIntOrNull()?.coerceIn(18, 82) ?: 50) / 100f
        val overlayText = unescapeOverlayText(parts[2])
        val trailingCaption = raw.substring(endIdx + OVERLAY_SUFFIX.length)
        return DecodedStoryOverlay(
            textOverlay = overlayText,
            styleCode = styleCode,
            positionY = positionY,
            caption = trailingCaption
        )
    }

    fun formatRemainingTtl(
        createdAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_STORY_TTL_MS
    ): String {
        if (createdAtMs == null || createdAtMs <= 0L) return "24h story"
        val remainingMs = (createdAtMs + ttlMs - nowMs).coerceAtLeast(0L)
        if (remainingMs <= 0L) return "Expiring"
        val hours = remainingMs / 3_600_000L
        val mins = (remainingMs % 3_600_000L) / 60_000L
        return if (hours >= 1L) "${hours}h left" else "${mins.coerceAtLeast(1L)}m left"
    }

    private fun escapeOverlayText(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '%' -> append("%25")
                ']' -> append("%5D")
                '\n' -> append("%0A")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun unescapeOverlayText(input: String): String = buildString(input.length) {
        var i = 0
        while (i < input.length) {
            if (input[i] == '%' && i + 2 < input.length) {
                when (input.substring(i, i + 3)) {
                    "%25" -> {
                        append('%')
                        i += 3
                        continue
                    }
                    "%5D" -> {
                        append(']')
                        i += 3
                        continue
                    }
                    "%0A" -> {
                        append('\n')
                        i += 3
                        continue
                    }
                }
            }
            append(input[i])
            i++
        }
    }

    private fun truncateEscapeSafe(escaped: String, maxLen: Int): String {
        if (escaped.length <= maxLen) return escaped
        if (maxLen <= 0) return ""
        var cut = maxLen
        if (cut >= 1 && escaped[cut - 1] == '%') {
            cut -= 1
        } else if (cut >= 2 && escaped[cut - 2] == '%') {
            cut -= 2
        }
        return escaped.substring(0, cut.coerceAtLeast(0))
    }
}
