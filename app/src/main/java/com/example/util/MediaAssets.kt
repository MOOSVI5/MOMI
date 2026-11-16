package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
            while (quality >= 40) {
                out.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= 95_000) break
                quality -= 12
            }
            bitmap.recycle()

            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (b64.length <= 180_000) b64 else null
        }.getOrNull()
    }

    fun decodeBase64ToBytes(imageData: String?): ByteArray? {
        if (imageData.isNullOrBlank()) return null
        return runCatching {
            Base64.decode(imageData, Base64.DEFAULT)
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
     * Prepares a video file for cross-device Firestore sync (< 480 KB raw / < 680 KB Base64).
     * Short/light videos encode directly; larger videos are downscaled via Media3 Transformer.
     * If still above Firestore's 1 MB document budget, returns (null, false) so the video still
     * plays locally and syncs its JPEG thumbnail cleanly across all devices.
     */
    @OptIn(UnstableApi::class)
    suspend fun prepareVideoDataForFirestore(
        context: Context,
        localVideoPath: String?
    ): Pair<String?, Boolean> {
        if (localVideoPath.isNullOrBlank()) return null to false
        val sourceFile = File(localVideoPath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null to false

        val maxRawBytes = 480_000L
        if (sourceFile.length() <= maxRawBytes) {
            val b64 = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = Base64.encodeToString(sourceFile.readBytes(), Base64.NO_WRAP)
                    if (encoded.length <= 680_000) encoded else null
                }.getOrNull()
            }
            if (b64 != null) return b64 to true
        }

        val compressionPasses = listOf(
            360 to 260_000,
            240 to 140_000
        )

        for ((targetHeight, bitrate) in compressionPasses) {
            val compressedFile = File(context.cacheDir, "momi_tx_${UUID.randomUUID()}.mp4")
            val exported = withTimeoutOrNull(25_000L) {
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
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            .build()

                        cont.invokeOnCancellation {
                            runCatching { transformer.cancel() }
                        }
                        runCatching {
                            transformer.start(editedMediaItem, compressedFile.absolutePath)
                        }.onFailure {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }
            } ?: false

            val encodedResult = withContext(Dispatchers.IO) {
                try {
                    if (exported && compressedFile.exists() && compressedFile.length() in 1..maxRawBytes) {
                        val encoded = Base64.encodeToString(compressedFile.readBytes(), Base64.NO_WRAP)
                        if (encoded.length <= 680_000) encoded else null
                    } else {
                        null
                    }
                } finally {
                    runCatching { compressedFile.delete() }
                }
            }
            if (encodedResult != null) {
                return encodedResult to true
            }
        }
        return null to false
    }

    fun resolvePlayableVideoUri(
        context: Context,
        mediaId: String,
        localVideoPath: String?,
        videoData: String?
    ): Uri? {
        if (!localVideoPath.isNullOrBlank()) {
            val localFile = File(localVideoPath)
            if (localFile.exists() && localFile.length() > 0L) {
                return Uri.fromFile(localFile)
            }
        }
        if (!videoData.isNullOrBlank()) {
            return runCatching {
                val cacheDir = File(context.cacheDir, "momi_video_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val safeId = mediaId.ifBlank { videoData.hashCode().toString() }
                    .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val cachedFile = File(cacheDir, "$safeId.mp4")
                if (!cachedFile.exists() || cachedFile.length() == 0L) {
                    val bytes = Base64.decode(videoData, Base64.DEFAULT)
                    cachedFile.writeBytes(bytes)
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
