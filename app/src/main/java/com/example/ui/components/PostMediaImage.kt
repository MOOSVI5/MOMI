package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.util.MediaAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val cleanUrl = remember(imageUrl) {
        imageUrl?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }
    val fallbackPainter = painterResource(id = MediaAssets.drawableForKey(imageKey))

    val mediaModel by produceState<Any?>(
        initialValue = cleanUrl,
        localImagePath,
        cleanUrl,
        imageData
    ) {
        val resolved: Any? = withContext(Dispatchers.IO) {
            val localFile = localImagePath?.trim()?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
            when {
                localFile != null -> localFile
                cleanUrl != null -> cleanUrl
                !imageData.isNullOrBlank() -> MediaAssets.decodeBase64ToBytesAsync(imageData)
                else -> null
            }
        }
        value = resolved
    }

    if (mediaModel != null) {
        AsyncImage(
            model = mediaModel,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter
        )
    } else {
        Image(
            painter = fallbackPainter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter
        )
    }
}
