package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.util.MediaAssets
import java.io.File

@Composable
fun PostMediaImage(
    imageKey: String,
    localImagePath: String?,
    filterTag: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    imageData: String? = null,
    imageUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    customColorFilter: ColorFilter? = null
) {
    val colorFilter = customColorFilter ?: MediaAssets.colorFilterForTag(filterTag)
    val decodedBytes = remember(imageData) { MediaAssets.decodeBase64ToBytes(imageData) }
    val cleanUrl = remember(imageUrl) {
        imageUrl?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }
    val fallbackPainter = painterResource(id = MediaAssets.drawableForKey(imageKey))

    when {
        !localImagePath.isNullOrBlank() && File(localImagePath).exists() -> {
            AsyncImage(
                model = File(localImagePath),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
        }
        cleanUrl != null -> {
            AsyncImage(
                model = cleanUrl,
                placeholder = fallbackPainter,
                error = fallbackPainter,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
        }
        decodedBytes != null -> {
            AsyncImage(
                model = decodedBytes,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
        }
        else -> {
            Image(
                painter = fallbackPainter,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
        }
    }
}
