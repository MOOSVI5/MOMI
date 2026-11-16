package com.example.ui.components

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.util.MediaAssets
import com.example.util.StudioMusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val LocalRemoteVideoResolver = staticCompositionLocalOf<
    (suspend (collectionName: String, mediaId: String, expectedChunksCount: Long) -> Uri?)?
> { null }

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
    videoChunksCount: Long = 0L,
    remoteCollection: String = "posts",
    localAudioPath: String? = null,
    audioTitle: String = "",
    muteOriginalVideoWhenMusicAdded: Boolean = true,
    initialMuted: Boolean = true,
    externalMuted: Boolean? = null,
    onMuteChange: ((Boolean) -> Unit)? = null,
    showMuteButton: Boolean = true,
    showDurationBadge: Boolean = true,
    showProgressBar: Boolean = true,
    muteButtonAlignment: Alignment = Alignment.BottomEnd,
    muteButtonPadding: PaddingValues = PaddingValues(12.dp),
    contentScale: ContentScale = ContentScale.Fit,
    overrideVideoUri: Uri? = null,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val remoteVideoResolver = LocalRemoteVideoResolver.current

    var playableUri by remember(overrideVideoUri, mediaId, localVideoPath, videoData) {
        mutableStateOf(
            overrideVideoUri ?: MediaAssets.resolvePlayableVideoUri(
                context = context,
                mediaId = mediaId,
                localVideoPath = localVideoPath,
                videoData = videoData
            )
        )
    }
    var isLoadingRemoteVideo by remember(mediaId) { mutableStateOf(false) }

    LaunchedEffect(
        overrideVideoUri,
        mediaId,
        localVideoPath,
        videoData,
        videoChunksCount,
        remoteCollection,
        remoteVideoResolver
    ) {
        if (overrideVideoUri != null) {
            playableUri = overrideVideoUri
            isLoadingRemoteVideo = false
            return@LaunchedEffect
        }
        val fastUri = withContext(Dispatchers.IO) {
            MediaAssets.resolvePlayableVideoUri(
                context = context,
                mediaId = mediaId,
                localVideoPath = localVideoPath,
                videoData = videoData
            )
        }
        if (fastUri != null) {
            playableUri = fastUri
            isLoadingRemoteVideo = false
            return@LaunchedEffect
        }
        if (videoChunksCount > 0L && remoteVideoResolver != null) {
            isLoadingRemoteVideo = true
            try {
                playableUri = remoteVideoResolver(remoteCollection, mediaId, videoChunksCount)
            } finally {
                isLoadingRemoteVideo = false
            }
        }
    }

    val playableMusicUri = remember(mediaId, localAudioPath, audioTitle) {
        StudioMusicCatalog.resolvePlayableMusicUri(
            context = context,
            localAudioPath = localAudioPath,
            audioTitle = audioTitle
        )
    }

    var internalMuted by remember(mediaId) { mutableStateOf(initialMuted) }
    val isMuted = externalMuted ?: internalMuted
    var userPaused by remember(mediaId) { mutableStateOf(false) }
    var showTapPlayIndicator by remember { mutableStateOf(false) }
    var firstFrameRendered by remember(playableUri) { mutableStateOf(false) }
    var playbackFailed by remember(playableUri) { mutableStateOf(false) }
    var playbackProgress by remember(playableUri) { mutableFloatStateOf(0f) }

    // Reset manual pause when scrolled away so returning autoplays like Instagram/TikTok
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            userPaused = false
        }
    }

    LaunchedEffect(showTapPlayIndicator) {
        if (showTapPlayIndicator) {
            delay(850L)
            showTapPlayIndicator = false
        }
    }

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentUserPaused by rememberUpdatedState(userPaused)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
    ) {
        // Poster / thumbnail layer shown while buffering or if video stream has no playable URI
        PostMediaImage(
            imageKey = fallbackImageKey,
            localImagePath = fallbackLocalImagePath,
            imageData = fallbackImageData,
            filterTag = filterTag,
            contentDescription = "Video preview",
            modifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (playableUri == null || playbackFailed) {
                        base.pointerInput(mediaId) {
                            detectTapGestures(
                                onDoubleTap = { currentOnDoubleTap?.invoke() }
                            )
                        }
                    } else {
                        base
                    }
                }
        )

        if (isLoadingRemoteVideo && playableUri == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        val activeUri = playableUri
        if (activeUri != null && !playbackFailed) {
            val exoPlayer = remember(activeUri) {
                ExoPlayer.Builder(context)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false
                    )
                    .build()
                    .apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        playWhenReady = currentIsPlaying && !currentUserPaused
                        setMediaItem(MediaItem.fromUri(activeUri))
                        prepare()
                    }
            }

            val musicPlayer = remember(playableMusicUri) {
                playableMusicUri?.let { uri ->
                    ExoPlayer.Builder(context)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                            false
                        )
                        .build()
                        .apply {
                            repeatMode = Player.REPEAT_MODE_ONE
                            setMediaItem(MediaItem.fromUri(uri))
                            prepare()
                        }
                }
            }

            LaunchedEffect(exoPlayer, musicPlayer, isPlaying, userPaused) {
                val shouldPlay = isPlaying && !userPaused
                if (shouldPlay && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                exoPlayer.playWhenReady = shouldPlay
                if (shouldPlay) {
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                }
                musicPlayer?.let { mp ->
                    if (shouldPlay && mp.playbackState == Player.STATE_IDLE) {
                        mp.prepare()
                    }
                    mp.playWhenReady = shouldPlay
                    if (shouldPlay) {
                        mp.play()
                    } else {
                        mp.pause()
                    }
                }
            }

            LaunchedEffect(exoPlayer, musicPlayer, isMuted, muteOriginalVideoWhenMusicAdded) {
                val hasAddedMusic = musicPlayer != null
                exoPlayer.volume = when {
                    isMuted -> 0f
                    hasAddedMusic && muteOriginalVideoWhenMusicAdded -> 0f
                    hasAddedMusic -> 0.35f
                    else -> 1f
                }
                musicPlayer?.volume = if (isMuted) 0f else 1f
            }

            LaunchedEffect(exoPlayer, isPlaying, userPaused, showProgressBar) {
                while (showProgressBar && isPlaying && !userPaused) {
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        playbackProgress = (exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                    delay(120L)
                }
            }

            // CRITICAL: Do NOT put isPlaying/userPaused in DisposableEffect keys,
            // otherwise scrolling or tapping permanently destroys ExoPlayer!
            DisposableEffect(lifecycleOwner, exoPlayer, musicPlayer) {
                val playerListener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        firstFrameRendered = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            playbackFailed = false
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w("MomiVideoPlayer", "Playback error for $mediaId: ${error.errorCodeName}", error)
                        MediaAssets.invalidateCachedVideo(context, mediaId)
                        playbackFailed = true
                    }
                }
                exoPlayer.addListener(playerListener)

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            exoPlayer.pause()
                            musicPlayer?.pause()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            val shouldPlay = currentIsPlaying && !currentUserPaused
                            exoPlayer.playWhenReady = shouldPlay
                            if (shouldPlay) exoPlayer.play()
                            musicPlayer?.let { mp ->
                                mp.playWhenReady = shouldPlay
                                if (shouldPlay) mp.play()
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    exoPlayer.removeListener(playerListener)
                    exoPlayer.release()
                    musicPlayer?.release()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                ContentFrame(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = contentScale
                )
            }

            // Gesture overlay: single-tap toggles play/pause, double-tap triggers like
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(playableUri) {
                        detectTapGestures(
                            onTap = {
                                userPaused = !userPaused
                                showTapPlayIndicator = true
                            },
                            onDoubleTap = {
                                currentOnDoubleTap?.invoke()
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

            if (showProgressBar && firstFrameRendered) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playbackProgress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
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

        if (showMuteButton && (playableUri != null && !playbackFailed || playableMusicUri != null)) {
            Box(
                modifier = Modifier
                    .align(muteButtonAlignment)
                    .padding(muteButtonPadding)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        val nextMuted = !isMuted
                        if (onMuteChange != null) {
                            onMuteChange(nextMuted)
                        } else {
                            internalMuted = nextMuted
                        }
                    }
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
