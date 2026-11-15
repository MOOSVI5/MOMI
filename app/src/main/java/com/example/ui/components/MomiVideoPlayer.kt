package com.example.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.util.MediaAssets
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun MomiVideoPlayer(
    mediaId: String,
    localVideoPath: String?,
    videoData: String?,
    videoDurationMs: Long,
    fallbackImageKey: String,
    fallbackLocalImagePath: String?,
    fallbackImageData: String?,
    filterTag: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    initialMuted: Boolean = true,
    showMuteButton: Boolean = true,
    showDurationBadge: Boolean = true,
    muteButtonAlignment: Alignment = Alignment.BottomEnd,
    muteButtonPadding: PaddingValues = PaddingValues(12.dp),
    overrideVideoUri: Uri? = null,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playableUri = remember(overrideVideoUri, mediaId, localVideoPath, videoData) {
        overrideVideoUri ?: MediaAssets.resolvePlayableVideoUri(
            context = context,
            mediaId = mediaId,
            localVideoPath = localVideoPath,
            videoData = videoData
        )
    }

    var isMuted by remember(mediaId) { mutableStateOf(initialMuted) }
    var userPaused by remember(mediaId) { mutableStateOf(false) }
    var showTapPlayIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showTapPlayIndicator) {
        if (showTapPlayIndicator) {
            delay(850L)
            showTapPlayIndicator = false
        }
    }

    Box(modifier = modifier) {
        if (playableUri != null) {
            val exoPlayer = remember(playableUri) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(playableUri))
                    prepare()
                }
            }

            LaunchedEffect(exoPlayer, isPlaying, userPaused) {
                exoPlayer.playWhenReady = isPlaying && !userPaused
            }

            LaunchedEffect(exoPlayer, isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }

            DisposableEffect(lifecycleOwner, exoPlayer, isPlaying, userPaused) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                        Lifecycle.Event.ON_RESUME -> {
                            exoPlayer.playWhenReady = isPlaying && !userPaused
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    exoPlayer.release()
                }
            }

            ContentFrame(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Crop
            )

            // Gesture overlay: single-tap toggles play/pause, double-tap triggers like
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(playableUri, onDoubleTap) {
                        detectTapGestures(
                            onTap = {
                                userPaused = !userPaused
                                showTapPlayIndicator = true
                            },
                            onDoubleTap = {
                                onDoubleTap?.invoke()
                            }
                        )
                    }
            )

            AnimatedVisibility(
                visible = userPaused || showTapPlayIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (userPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (userPaused) "Paused" else "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        } else {
            PostMediaImage(
                imageKey = fallbackImageKey,
                localImagePath = fallbackLocalImagePath,
                imageData = fallbackImageData,
                filterTag = filterTag,
                contentDescription = "Video preview",
                modifier = Modifier
                    .fillMaxSize()
                    .let { base ->
                        if (onDoubleTap != null) {
                            base.pointerInput(mediaId) {
                                detectTapGestures(onDoubleTap = { onDoubleTap() })
                            }
                        } else {
                            base
                        }
                    }
            )
        }

        if (showDurationBadge) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                if (videoDurationMs > 0L) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = MediaAssets.formatDuration(videoDurationMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showMuteButton && playableUri != null) {
            Box(
                modifier = Modifier
                    .align(muteButtonAlignment)
                    .padding(muteButtonPadding)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { isMuted = !isMuted }
                    .semantics {
                        contentDescription = if (isMuted) "Unmute video" else "Mute video"
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
