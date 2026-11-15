package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.example.R
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object MediaAssets {
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

    fun colorFilterForTag(filterTag: String): ColorFilter? {
        return when (filterTag) {
            "MONO" -> {
                val matrix = ColorMatrix().apply { setToSaturation(0f) }
                ColorFilter.colorMatrix(matrix)
            }
            "BLUE" -> {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        0.88f, 0f, 0f, 0f, 0f,
                        0f, 0.96f, 0f, 0f, 4f,
                        0f, 0f, 1.15f, 0f, 14f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            "MAROON" -> {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        1.14f, 0f, 0f, 0f, 12f,
                        0f, 0.88f, 0f, 0f, 0f,
                        0f, 0f, 0.96f, 0f, 6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            else -> null
        }
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
    fun encodeImageFileToBase64(filePath: String?): String? {
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

            val maxEdge = 900
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
            while (quality >= 42) {
                out.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= 320_000) break
                quality -= 12
            }
            bitmap.recycle()

            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (b64.length <= 680_000) b64 else null
        }.getOrNull()
    }

    fun decodeBase64ToBytes(imageData: String?): ByteArray? {
        if (imageData.isNullOrBlank()) return null
        return runCatching {
            Base64.decode(imageData, Base64.DEFAULT)
        }.getOrNull()
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
