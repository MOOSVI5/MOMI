package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.R
import com.example.data.model.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class ExtractedVideoMedia(
    val localVideoPath: String,
    val thumbnailPath: String?,
    val durationMs: Long
)

data class PreparedVideoPayload(
    val inlineVideoData: String? = null,
    val videoChunks: List<String> = emptyList(),
    val failureReason: String? = null
) {
    val isReady: Boolean
        get() = !inlineVideoData.isNullOrBlank() || videoChunks.isNotEmpty()
    val chunksCount: Int
        get() = videoChunks.size
}

object MediaAssets {
    private const val TAG = "MediaAssets"
    const val MAX_POST_VIDEO_DURATION_MS = 4_800_000L // 80 minutes
    const val MAX_STORY_VIDEO_DURATION_MS = 30_000L
    private const val INLINE_MAX_RAW_BYTES = 340_000L
    private const val CHUNK_SLICE_RAW_BYTES = 340_000
    private const val MAX_VIDEO_CHUNKS = 24
    private const val MAX_CHUNKED_RAW_BYTES = CHUNK_SLICE_RAW_BYTES.toLong() * MAX_VIDEO_CHUNKS
    private const val MAX_CHUNK_BASE64_LENGTH = 680_000
    @DrawableRes
    fun drawableForKey(key: String): Int = when (key) {
        "img_post_coastal" -> R.drawable.img_post_coastal
        "img_post_coffee" -> R.drawable.img_post_coffee
        "img_post_architecture" -> R.drawable.img_post_architecture
        "img_post_neon_street" -> R.drawable.img_post_neon_street
        else -> R.drawable.img_post_coastal
    }

    val presetPhotoOptions: List<Pair<String, String>> = listOf(
        "img_post_coastal" to "Amalfi Coast",
        "img_post_coffee" to "Matcha & Croissant",
        "img_post_architecture" to "Gallery Atrium",
        "img_post_neon_street" to "Tokyo Rain"
    )

    private val avatarKeys = listOf(
        "avatar_aria",
        "avatar_elena",
        "avatar_kenji",
        "avatar_nord",
        "avatar_matcha",
        "avatar_sora"
    )

    fun avatarKeyForUid(uid: String): String {
        val index = (uid.hashCode() and 0x7FFFFFFF) % avatarKeys.size
        return avatarKeys[index]
    }

    fun avatarPalette(avatarKey: String): Pair<Color, Color> = when (avatarKey) {
        "avatar_aria" -> Color(0xFF4A89B8) to Color(0xFF6B0F2B)
        "avatar_elena" -> Color(0xFF2C5E8A) to Color(0xFF7CB9E8)
        "avatar_kenji" -> Color(0xFF6B0F2B) to Color(0xFF4A89B8)
        "avatar_nord" -> Color(0xFF1A2A3D) to Color(0xFF7CB9E8)
        "avatar_matcha" -> Color(0xFF8C1D40) to Color(0xFF2C5E8A)
        "avatar_sora" -> Color(0xFF6B0F2B) to Color(0xFFE05279)
        else -> Color(0xFF4A89B8) to Color(0xFF8C1D40)
    }

    fun initialsForUsername(username: String): String {
        val parts = username.split(".", "_", "-").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            username.length >= 2 -> username.take(2).uppercase()
            else -> "MO"
        }
    }

    data class MomiFilterOption(val tag: String, val label: String)

    val postFilterOptions: List<MomiFilterOption> = listOf(
        MomiFilterOption("NONE", "Original"),
        MomiFilterOption("BLUE", "Silk Blue"),
        MomiFilterOption("MAROON", "Maroon"),
        MomiFilterOption("WARM", "Golden"),
        MomiFilterOption("VINTAGE", "Vintage"),
        MomiFilterOption("VIVID", "Vivid"),
        MomiFilterOption("CINEMA", "Cinema"),
        MomiFilterOption("PASTEL", "Pastel"),
        MomiFilterOption("MONO", "Mono"),
        MomiFilterOption("NOIR", "Noir")
    )

    fun extractHashtags(caption: String, extraTags: List<String> = emptyList()): List<String> {
        val parsed = Regex("#([a-zA-Z0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .toList()
        return (extraTags.map { it.trim().removePrefix("#").lowercase() } + parsed)
            .map { it.replace(Regex("[^a-z0-9_]"), "") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)
    }

    fun extractMentions(caption: String, extraMentions: List<String> = emptyList()): List<String> {
        val parsed = Regex("@([a-zA-Z0-9._]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .toList()
        return (extraMentions.map { it.trim().removePrefix("@").lowercase() } + parsed)
            .map { it.replace(Regex("[^a-z0-9._]"), "") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }

    private fun baseMatrixValuesForFilter(filterTag: String): FloatArray? = when (filterTag) {
        "MONO" -> floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        "BLUE" -> floatArrayOf(
            0.88f, 0f, 0f, 0f, 0f,
            0f, 0.96f, 0f, 0f, 4f,
            0f, 0f, 1.15f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )
        "MAROON" -> floatArrayOf(
            1.14f, 0f, 0f, 0f, 12f,
            0f, 0.88f, 0f, 0f, 0f,
            0f, 0f, 0.96f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )
        "WARM" -> floatArrayOf(
            1.12f, 0f, 0f, 0f, 10f,
            0f, 1.04f, 0f, 0f, 4f,
            0f, 0f, 0.86f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )
        "VINTAGE" -> floatArrayOf(
            0.95f, 0.12f, 0.04f, 0f, 12f,
            0.05f, 0.92f, 0.05f, 0f, 6f,
            0.03f, 0.08f, 0.78f, 0f, 2f,
            0f, 0f, 0f, 1f, 0f
        )
        "VIVID" -> floatArrayOf(
            1.22f, -0.06f, -0.06f, 0f, 2f,
            -0.06f, 1.22f, -0.06f, 0f, 2f,
            -0.06f, -0.06f, 1.24f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f
        )
        "NOIR" -> floatArrayOf(
            0.42f, 0.78f, 0.15f, 0f, -38f,
            0.42f, 0.78f, 0.15f, 0f, -38f,
            0.42f, 0.78f, 0.15f, 0f, -38f,
            0f, 0f, 0f, 1f, 0f
        )
        "CINEMA" -> floatArrayOf(
            1.08f, -0.04f, 0f, 0f, -4f,
            0f, 1.02f, 0.04f, 0f, 2f,
            0f, 0.08f, 1.12f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )
        "PASTEL" -> floatArrayOf(
            0.94f, 0.08f, 0.08f, 0f, 16f,
            0.06f, 0.94f, 0.08f, 0f, 16f,
            0.06f, 0.06f, 0.98f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )
        else -> null
    }

    fun composeEditMatrixValues(
        filterTag: String = "NONE",
        brightness: Float = 0f,
        contrast: Float = 1f,
        warmth: Float = 0f
    ): FloatArray {
        val base = baseMatrixValuesForFilter(filterTag)?.copyOf() ?: floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val clampedContrast = contrast.coerceIn(0.6f, 1.5f)
        val brightnessShift = brightness.coerceIn(-0.4f, 0.4f) * 255f
        val contrastTranslate = 128f * (1f - clampedContrast)
        val warmRed = 1f + (warmth.coerceIn(-0.4f, 0.4f) * 0.28f)
        val warmBlue = 1f - (warmth.coerceIn(-0.4f, 0.4f) * 0.28f)

        base[0] *= clampedContrast * warmRed
        base[1] *= clampedContrast * warmRed
        base[2] *= clampedContrast * warmRed
        base[4] = base[4] * clampedContrast + contrastTranslate + brightnessShift

        base[5] *= clampedContrast
        base[6] *= clampedContrast
        base[7] *= clampedContrast
        base[9] = base[9] * clampedContrast + contrastTranslate + brightnessShift

        base[10] *= clampedContrast * warmBlue
        base[11] *= clampedContrast * warmBlue
        base[12] *= clampedContrast * warmBlue
        base[14] = base[14] * clampedContrast + contrastTranslate + brightnessShift
        return base
    }

    fun colorFilterForEdit(
        filterTag: String,
        brightness: Float = 0f,
        contrast: Float = 1f,
        warmth: Float = 0f
    ): ColorFilter? {
        val isIdentity = filterTag == "NONE" &&
            kotlin.math.abs(brightness) < 0.01f &&
            kotlin.math.abs(contrast - 1f) < 0.01f &&
            kotlin.math.abs(warmth) < 0.01f
        if (isIdentity) return null
        return ColorFilter.colorMatrix(
            ColorMatrix(composeEditMatrixValues(filterTag, brightness, contrast, warmth))
        )
    }

    fun colorFilterForTag(filterTag: String): ColorFilter? {
        val values = baseMatrixValuesForFilter(filterTag) ?: return null
        return ColorFilter.colorMatrix(ColorMatrix(values))
    }

    /**
     * Bakes rotation, aspect crop (1:1, 4:5, 16:9), and tone sliders (brightness, contrast, warmth)
     * into a new JPEG file under app internal storage while preserving [filterTag] as the non-destructive grade overlay.
     */
    fun renderEditedImageToFile(
        context: Context,
        sourcePath: String?,
        brightness: Float = 0f,
        contrast: Float = 1f,
        warmth: Float = 0f,
        rotationDegrees: Int = 0,
        targetAspectRatio: String = "1:1"
    ): String? {
        if (sourcePath.isNullOrBlank()) return null
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val hasToneEdit = kotlin.math.abs(brightness) >= 0.01f ||
            kotlin.math.abs(contrast - 1f) >= 0.01f ||
            kotlin.math.abs(warmth) >= 0.01f
        if (!hasToneEdit && normalizedRotation == 0 && targetAspectRatio == "1:1") {
            return sourcePath
        }
        return runCatching {
            val file = File(sourcePath)
            if (!file.exists()) return sourcePath

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourcePath, boundsOptions)
            val srcW = boundsOptions.outWidth
            val srcH = boundsOptions.outHeight
            if (srcW <= 0 || srcH <= 0) return sourcePath

            var sample = 1
            while ((srcW / sample) > 1600 || (srcH / sample) > 1600) sample *= 2
            var bitmap = BitmapFactory.decodeFile(
                sourcePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return sourcePath

            val exifOrientation = runCatching {
                ExifInterface(sourcePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val exifDeg = when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            val totalDeg = (exifDeg + normalizedRotation) % 360
            if (totalDeg != 0) {
                val m = Matrix().apply { postRotate(totalDeg.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            // Center crop to desired aspect ratio
            val desiredRatio = when (targetAspectRatio) {
                "4:5" -> 4f / 5f
                "16:9" -> 16f / 9f
                else -> 1f
            }
            val currentRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
            val (cropW, cropH) = if (currentRatio > desiredRatio) {
                val h = bitmap.height
                val w = (h * desiredRatio).toInt().coerceIn(1, bitmap.width)
                w to h
            } else {
                val w = bitmap.width
                val h = (w / desiredRatio).toInt().coerceIn(1, bitmap.height)
                w to h
            }
            val cropX = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
            val cropY = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
            if (cropW < bitmap.width || cropH < bitmap.height) {
                val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
                if (cropped !== bitmap) bitmap.recycle()
                bitmap = cropped
            }

            if (hasToneEdit) {
                val styled = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(styled)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(
                        android.graphics.ColorMatrix(
                            composeEditMatrixValues("NONE", brightness, contrast, warmth)
                        )
                    )
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                bitmap.recycle()
                bitmap = styled
            }

            val dir = File(context.filesDir, "luma_media").apply { if (!exists()) mkdirs() }
            val outFile = File(dir, "edited_${UUID.randomUUID()}.jpg")
            outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath else sourcePath
        }.getOrDefault(sourcePath)
    }

    fun copyPickedImageToInternalStorage(context: Context, uri: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, "luma_media")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "post_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        }.getOrNull()
    }

    /**
     * Scales and JPEG-compresses a local image file to a Base64 string safe for Firestore (< 450 KB)
     * so picked gallery photos sync across devices without needing external object storage.
     */
    fun encodeImageFileToBase64(filePath: String?): String? =
        encodeImageFileToBase64WithBudget(filePath, maxRawBytes = 320_000, maxEdge = 900, maxBase64Chars = 680_000)

    fun encodePrimaryPostImage(filePath: String?, hasExtraSlides: Boolean): String? =
        if (hasExtraSlides) {
            encodeImageFileToBase64WithBudget(filePath, maxRawBytes = 160_000, maxEdge = 800, maxBase64Chars = 230_000)
        } else {
            encodeImageFileToBase64(filePath)
        }

    fun encodeCarouselImageToBase64(filePath: String?): String? =
        encodeImageFileToBase64WithBudget(filePath, maxRawBytes = 100_000, maxEdge = 720, maxBase64Chars = 140_000)

    private fun encodeImageFileToBase64WithBudget(
        filePath: String?,
        maxRawBytes: Int,
        maxEdge: Int,
        maxBase64Chars: Int
    ): String? {
        if (filePath.isNullOrBlank()) return null
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOptions)
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            val maxRawDim = 1080
            while ((srcWidth / sampleSize) > maxRawDim * 2 || (srcHeight / sampleSize) > maxRawDim * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

            val orientation = runCatching {
                ExifInterface(filePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            if (bitmap.width > maxEdge || bitmap.height > maxEdge) {
                val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val out = ByteArrayOutputStream()
            var quality = 78
            while (quality >= 34) {
                out.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxRawBytes) break
                quality -= 10
            }
            bitmap.recycle()

            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (b64.length <= maxBase64Chars) b64 else null
        }.getOrNull()
    }

    fun encodeAvatarFileToBase64(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOptions)
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            while ((srcWidth / sampleSize) > 720 || (srcHeight / sampleSize) > 720) {
                sampleSize *= 2
            }

            var bitmap = BitmapFactory.decodeFile(
                filePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return null

            val orientation = runCatching {
                ExifInterface(filePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val side = minOf(bitmap.width, bitmap.height)
            val xOff = (bitmap.width - side) / 2
            val yOff = (bitmap.height - side) / 2
            val cropped = Bitmap.createBitmap(bitmap, xOff, yOff, side, side)
            if (cropped !== bitmap) bitmap.recycle()
            bitmap = cropped

            val targetSize = 320
            if (bitmap.width > targetSize) {
                val scaled = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val out = ByteArrayOutputStream()
            var quality = 78
            while (quality >= 20) {
                out.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= 95_000) break
                quality -= 10
            }
            if (out.size() > 135_000) {
                val smaller = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                out.reset()
                smaller.compress(Bitmap.CompressFormat.JPEG, 60, out)
                if (smaller !== bitmap) smaller.recycle()
            }
            bitmap.recycle()

            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (b64.length <= 200_000) b64 else null
        }.getOrNull()
    }

    fun decodeBase64ToBytes(imageData: String?): ByteArray? {
        if (imageData.isNullOrBlank()) return null
        return runCatching {
            Base64.decode(imageData, Base64.DEFAULT)
        }.getOrNull()
    }

    fun decodeBase64ToBitmap(frameData: String?): Bitmap? {
        val bytes = decodeBase64ToBytes(frameData) ?: return null
        if (bytes.isEmpty()) return null
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun encodeLiveFrameBitmapToBase64(source: Bitmap, rotationDegrees: Int): String? {
        return runCatching {
            var working = source
            var ownsWorking = false
            try {
                if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
                    if (rotated !== working) {
                        working = rotated
                        ownsWorking = true
                    }
                }
                val maxEdge = 420
                if (working.width > maxEdge || working.height > maxEdge) {
                    val scale = maxEdge.toFloat() / maxOf(working.width, working.height).toFloat()
                    val targetW = (working.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (working.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(working, targetW, targetH, true)
                    if (scaled !== working) {
                        if (ownsWorking) working.recycle()
                        working = scaled
                        ownsWorking = true
                    }
                }
                val out = ByteArrayOutputStream()
                var quality = 46
                while (quality >= 26) {
                    out.reset()
                    working.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    if (out.size() <= 125_000) break
                    quality -= 10
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty()) return null
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (b64.length <= 200_000) b64 else null
            } finally {
                if (ownsWorking) {
                    runCatching { working.recycle() }
                }
            }
        }.getOrNull()
    }

    fun copyPickedVideoToInternalStorage(context: Context, uri: Uri): ExtractedVideoMedia? {
        return runCatching {
            val dir = File(context.filesDir, "luma_media")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "clip_${UUID.randomUUID()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!outFile.exists() || outFile.length() == 0L) return null

            var durationMs = 0L
            var thumbnailPath: String? = null
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(outFile.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val thumbFile = File(dir, "thumb_${UUID.randomUUID()}.jpg")
                    thumbFile.outputStream().use { thumbOut ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 82, thumbOut)
                    }
                    frame.recycle()
                    if (thumbFile.exists() && thumbFile.length() > 0L) {
                        thumbnailPath = thumbFile.absolutePath
                    }
                }
            } finally {
                runCatching { retriever.release() }
            }

            ExtractedVideoMedia(
                localVideoPath = outFile.absolutePath,
                thumbnailPath = thumbnailPath,
                durationMs = durationMs
            )
        }.getOrNull()
    }

    /**
     * Compresses and prepares a video file for cross-device Firestore sync up to ~8.16 MB:
     * - Clips <= 340 KB store inline in `videoData` for instant 1-read playback.
     * - Larger clips compress cleanly via Media3 Transformer (480p -> 360p -> 240p) and split
     *   into ordered <= 340 KB Base64 chunk documents under `/videoChunks`.
     * - Falls back to chunking the original MP4 directly if hardware encoding throws on an unusual codec
     *   while the source file is <= 8.16 MB.
     */
    @OptIn(UnstableApi::class)
    suspend fun prepareVideoPayloadForFirestore(
        context: Context,
        localVideoPath: String?
    ): PreparedVideoPayload {
        if (localVideoPath.isNullOrBlank()) {
            return PreparedVideoPayload(failureReason = "No video file selected.")
        }
        val sourceFile = File(localVideoPath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return PreparedVideoPayload(failureReason = "Selected video file could not be read.")
        }

        if (sourceFile.length() <= INLINE_MAX_RAW_BYTES) {
            val inlinePayload = withContext(Dispatchers.IO) {
                runCatching { splitVideoBytes(sourceFile.readBytes()) }.getOrNull()
            }
            if (inlinePayload != null && inlinePayload.isReady) {
                return inlinePayload
            }
        }

        val compressionPasses = listOf(
            480 to 650_000,
            360 to 300_000,
            240 to 180_000
        )

        var sawExportErrorOrTimeout = false
        for ((targetHeight, bitrate) in compressionPasses) {
            val compressedFile = File(context.cacheDir, "momi_tx_${UUID.randomUUID()}.mp4")
            val exported = withTimeoutOrNull(45_000L) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine { cont ->
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(bitrate)
                                    .build()
                            )
                            .build()

                        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile)))
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(Presentation.createForHeight(targetHeight))
                                )
                            )
                            .build()

                        val transformer = Transformer.Builder(context)
                            .setEncoderFactory(encoderFactory)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    Log.w(TAG, "Video compression ${targetHeight}p failed: ${exportException.errorCodeName}", exportException)
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            .build()

                        cont.invokeOnCancellation {
                            runCatching { transformer.cancel() }
                        }
                        runCatching {
                            transformer.start(editedMediaItem, compressedFile.absolutePath)
                        }.onFailure { err ->
                            Log.w(TAG, "Transformer start failed at ${targetHeight}p", err)
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }
            } ?: false

            if (!exported) {
                sawExportErrorOrTimeout = true
            }

            val compressedPayload = withContext(Dispatchers.IO) {
                try {
                    if (exported && compressedFile.exists() && compressedFile.length() in 1L..MAX_CHUNKED_RAW_BYTES) {
                        splitVideoBytes(compressedFile.readBytes())
                    } else {
                        null
                    }
                } finally {
                    runCatching { compressedFile.delete() }
                }
            }
            if (compressedPayload != null && compressedPayload.isReady) {
                return compressedPayload
            }
        }

        // Hardware encoder fallback: if source MP4 already fits in our chunked budget, upload it directly
        if (sourceFile.length() <= MAX_CHUNKED_RAW_BYTES) {
            val rawPayload = withContext(Dispatchers.IO) {
                runCatching { splitVideoBytes(sourceFile.readBytes()) }.getOrNull()
            }
            if (rawPayload != null && rawPayload.isReady) {
                return rawPayload
            }
        }

        val reason = if (sawExportErrorOrTimeout && sourceFile.length() > MAX_CHUNKED_RAW_BYTES) {
            "This video file is large. Please select a shorter clip or compress it before uploading."
        } else {
            "Video file could not be processed. Please try another MP4 video."
        }
        return PreparedVideoPayload(failureReason = reason)
    }

    suspend fun prepareVideoDataForFirestore(
        context: Context,
        localVideoPath: String?
    ): Pair<String?, Boolean> {
        val payload = prepareVideoPayloadForFirestore(context, localVideoPath)
        return payload.inlineVideoData to (payload.inlineVideoData != null)
    }

    /**
     * Prepares a compact inline MP4 (<= 440 KB raw / <= 640 KB Base64) for 1:1 DM video messages.
     */
    @OptIn(UnstableApi::class)
    suspend fun prepareCompactVideoForChat(
        context: Context,
        localVideoPath: String?
    ): Result<String> {
        if (localVideoPath.isNullOrBlank()) return Result.failure(IllegalArgumentException("No video selected."))
        val sourceFile = File(localVideoPath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return Result.failure(IllegalArgumentException("Video file could not be read."))
        }
        val maxChatBytes = 440_000L
        val maxChatBase64 = 640_000
        if (sourceFile.length() <= maxChatBytes) {
            val inlineB64 = withContext(Dispatchers.IO) {
                runCatching { Base64.encodeToString(sourceFile.readBytes(), Base64.NO_WRAP) }.getOrNull()
            }
            if (!inlineB64.isNullOrBlank() && inlineB64.length <= maxChatBase64) {
                return Result.success(inlineB64)
            }
        }
        val passes = listOf(360 to 320_000, 240 to 160_000, 240 to 95_000)
        for ((targetHeight, bitrate) in passes) {
            val outFile = File(context.cacheDir, "momi_chat_vid_${UUID.randomUUID()}.mp4")
            val ok = withTimeoutOrNull(35_000L) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine { cont ->
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(bitrate).build()
                            )
                            .build()
                        val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile)))
                            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight))))
                            .build()
                        val transformer = Transformer.Builder(context)
                            .setEncoderFactory(encoderFactory)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(true)
                                }
                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            .build()
                        cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                        runCatching { transformer.start(edited, outFile.absolutePath) }
                            .onFailure { if (cont.isActive) cont.resume(false) }
                    }
                }
            } ?: false
            val b64 = withContext(Dispatchers.IO) {
                try {
                    if (ok && outFile.exists() && outFile.length() in 1L..maxChatBytes) {
                        val encoded = Base64.encodeToString(outFile.readBytes(), Base64.NO_WRAP)
                        if (encoded.length <= maxChatBase64) encoded else null
                    } else null
                } finally {
                    runCatching { outFile.delete() }
                }
            }
            if (b64 != null) return Result.success(b64)
        }
        return Result.failure(IllegalStateException("Video is too long for chat. Please send a shorter clip (under ~20 seconds)."))
    }

    internal fun splitVideoBytes(bytes: ByteArray): PreparedVideoPayload? {
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_CHUNKED_RAW_BYTES) return null
        if (bytes.size.toLong() <= INLINE_MAX_RAW_BYTES) {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded.length <= MAX_CHUNK_BASE64_LENGTH) {
                return PreparedVideoPayload(inlineVideoData = encoded)
            }
        }
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val len = minOf(CHUNK_SLICE_RAW_BYTES, bytes.size - offset)
            val sliceB64 = Base64.encodeToString(bytes, offset, len, Base64.NO_WRAP)
            if (sliceB64.isEmpty() || sliceB64.length > MAX_CHUNK_BASE64_LENGTH) return null
            chunks.add(sliceB64)
            offset += len
        }
        if (chunks.isEmpty() || chunks.size > MAX_VIDEO_CHUNKS) return null
        return PreparedVideoPayload(videoChunks = chunks)
    }

    private fun videoCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "momi_video_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeCacheFileName(mediaId: String): String {
        val cleaned = mediaId.ifBlank { "clip" }.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return "$cleaned.mp4"
    }

    fun resolveCachedVideoUri(context: Context, mediaId: String): Uri? {
        if (mediaId.isBlank()) return null
        val cachedFile = File(videoCacheDir(context), safeCacheFileName(mediaId))
        return if (cachedFile.exists() && cachedFile.length() > 0L) Uri.fromFile(cachedFile) else null
    }

    fun invalidateCachedVideo(context: Context, mediaId: String) {
        if (mediaId.isBlank()) return
        runCatching {
            val cachedFile = File(videoCacheDir(context), safeCacheFileName(mediaId))
            if (cachedFile.exists()) cachedFile.delete()
        }
    }

    suspend fun assembleChunksToCacheFile(
        context: Context,
        mediaId: String,
        orderedBase64Chunks: List<String>
    ): Uri? = withContext(Dispatchers.IO) {
        if (mediaId.isBlank() || orderedBase64Chunks.isEmpty()) return@withContext null
        runCatching {
            val cacheDir = videoCacheDir(context)
            val cachedFile = File(cacheDir, safeCacheFileName(mediaId))
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                return@withContext Uri.fromFile(cachedFile)
            }
            val tempFile = File(cacheDir, "${safeCacheFileName(mediaId)}.${UUID.randomUUID()}.tmp")
            try {
                BufferedOutputStream(tempFile.outputStream()).use { out ->
                    for (chunkB64 in orderedBase64Chunks) {
                        if (chunkB64.isBlank()) error("Empty video chunk encountered")
                        val decoded = Base64.decode(chunkB64, Base64.DEFAULT)
                        if (decoded.isEmpty()) error("Decoded empty video chunk")
                        out.write(decoded)
                    }
                    out.flush()
                }
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    error("Assembled temp video file is empty")
                }
                if (cachedFile.exists()) cachedFile.delete()
                if (!tempFile.renameTo(cachedFile)) {
                    tempFile.copyTo(cachedFile, overwrite = true)
                    tempFile.delete()
                }
                Uri.fromFile(cachedFile)
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                throw t
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed assembling video chunks for $mediaId", err)
        }.getOrNull()
    }

    suspend fun readUploadBytesUpToLimit(
        localPath: String?,
        maxBytes: Long = 22_000_000L
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (localPath.isNullOrBlank()) return@withContext null
        val file = File(localPath)
        if (!file.exists() || file.length() <= 0L || file.length() > maxBytes) {
            return@withContext null
        }
        runCatching { file.readBytes() }.getOrNull()
    }

    suspend fun readCompressedImageBytes(
        localPath: String?,
        maxDimPx: Int = 1280,
        quality: Int = 82
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (localPath.isNullOrBlank()) return@withContext null
        val file = File(localPath)
        if (!file.exists() || file.length() <= 0L) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(localPath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@runCatching file.readBytes().takeIf { it.size <= 3_500_000 }
            var sample = 1
            while ((w / sample) > maxDimPx || (h / sample) > maxDimPx) sample *= 2
            val decoded = BitmapFactory.decodeFile(
                localPath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching file.readBytes().takeIf { it.size <= 3_500_000 }
            val scale = minOf(1f, maxDimPx.toFloat() / maxOf(decoded.width, decoded.height).toFloat())
            val finalBmp = if (scale < 0.99f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            val baos = ByteArrayOutputStream()
            finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (finalBmp !== decoded) finalBmp.recycle()
            decoded.recycle()
            baos.toByteArray()
        }.getOrNull()
    }

    fun resolvePlayableVideoUri(
        context: Context,
        mediaId: String,
        localVideoPath: String?,
        videoData: String?,
        videoUrl: String? = null
    ): Uri? {
        if (!localVideoPath.isNullOrBlank()) {
            val localFile = File(localVideoPath)
            if (localFile.exists() && localFile.length() > 0L) {
                return Uri.fromFile(localFile)
            }
        }
        val cleanRemoteUrl = videoUrl?.trim().orEmpty()
        if (cleanRemoteUrl.startsWith("https://", ignoreCase = true) ||
            cleanRemoteUrl.startsWith("http://", ignoreCase = true)
        ) {
            return Uri.parse(cleanRemoteUrl)
        }
        resolveCachedVideoUri(context, mediaId)?.let { return it }
        if (!videoData.isNullOrBlank()) {
            return runCatching {
                val cacheDir = videoCacheDir(context)
                val safeName = safeCacheFileName(mediaId.ifBlank { videoData.hashCode().toString() })
                val cachedFile = File(cacheDir, safeName)
                if (!cachedFile.exists() || cachedFile.length() == 0L) {
                    val tempFile = File(cacheDir, "$safeName.${UUID.randomUUID()}.tmp")
                    try {
                        val bytes = Base64.decode(videoData, Base64.DEFAULT)
                        if (bytes.isEmpty()) return null
                        tempFile.writeBytes(bytes)
                        if (!tempFile.renameTo(cachedFile)) {
                            tempFile.copyTo(cachedFile, overwrite = true)
                            tempFile.delete()
                        }
                    } catch (t: Throwable) {
                        runCatching { tempFile.delete() }
                        throw t
                    }
                }
                Uri.fromFile(cachedFile)
            }.getOrNull()
        }
        return null
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun formatRelativeTime(timestamp: Timestamp?): String {
        val millis = timestamp?.toDate()?.time ?: return "Just now"
        return formatRelativeTime(millis)
    }

    fun formatRelativeTime(createdAtMillis: Long): String {
        val diff = (System.currentTimeMillis() - createdAtMillis).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }

    fun formatCompactCount(count: Long): String {
        return when {
            count >= 1_000_000L -> String.format("%.1fM", count / 1_000_000.0)
            count >= 10_000L -> String.format("%.1fK", count / 1_000.0)
            count >= 1_000L -> String.format("%,d", count)
            else -> count.toString()
        }
    }
}
