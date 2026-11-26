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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Lightweight ExoPlayer music accompaniment for Photo Posts, Photo Stories, Photo Clips, and Post Composer preview.
 * Only allocates an ExoPlayer instance when [isPlaying] is true and releases immediately when inactive.
 */
@OptIn(UnstableApi::class)
@Composable
fun MomiMusicAudioPlayer(
    mediaId: String,
    localAudioPath: String?,
    audioData: String?,
    audioTitle: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    audioUrl: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playableMusicUri = remember(mediaId, localAudioPath, audioData, audioTitle, audioUrl) {
        StudioMusicCatalog.resolvePlayableMusicUri(
            context = context,
            localAudioPath = localAudioPath,
            audioTitle = audioTitle,
            audioData = audioData,
            cacheKey = mediaId,
            audioUrl = audioUrl
        )
    }

    if (playableMusicUri != null && isPlaying) {
        val audioPlayer = remember(playableMusicUri) {
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
                    setMediaItem(MediaItem.fromUri(playableMusicUri))
                    prepare()
                    playWhenReady = true
                }
        }

        LaunchedEffect(audioPlayer, isMuted) {
            audioPlayer.volume = if (isMuted) 0f else 1f
        }

        DisposableEffect(lifecycleOwner, audioPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> audioPlayer.pause()
                    Lifecycle.Event.ON_RESUME -> audioPlayer.play()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                audioPlayer.release()
            }
        }
    }
}

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
    isPrebuffering: Boolean = false,
    videoUrl: String? = null,
    fallbackImageUrl: String? = null,
    videoChunksCount: Long = 0L,
    remoteCollection: String = "posts",
    localAudioPath: String? = null,
    audioUrl: String? = null,
    audioData: String? = null,
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
    onDoubleTap: (() -> Unit)? = null,
    onRequestPlay: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val remoteVideoResolver = LocalRemoteVideoResolver.current

    var retryNonce by remember(mediaId) { mutableIntStateOf(0) }
    var playableUri by remember(overrideVideoUri, mediaId, localVideoPath, videoData, videoUrl) {
        mutableStateOf(
            overrideVideoUri ?: MediaAssets.resolvePlayableVideoUri(
                context = context,
                mediaId = mediaId,
                localVideoPath = localVideoPath,
                videoData = videoData,
                videoUrl = videoUrl
            )
        )
    }
    var isLoadingRemoteVideo by remember(mediaId) { mutableStateOf(false) }
    var playbackFailed by remember(mediaId) { mutableStateOf(false) }
    var isBuffering by remember(mediaId) { mutableStateOf(false) }

    LaunchedEffect(
        overrideVideoUri,
        mediaId,
        localVideoPath,
        videoData,
        videoUrl,
        videoChunksCount,
        remoteCollection,
        remoteVideoResolver,
        retryNonce
    ) {
        playbackFailed = false
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
                videoData = videoData,
                videoUrl = videoUrl
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

    val playableMusicUri = remember(mediaId, localAudioPath, audioData, audioTitle, audioUrl) {
        StudioMusicCatalog.resolvePlayableMusicUri(
            context = context,
            localAudioPath = localAudioPath,
            audioTitle = audioTitle,
            audioData = audioData,
            cacheKey = mediaId,
            audioUrl = audioUrl
        )
    }

    var internalMuted by remember(mediaId) { mutableStateOf(initialMuted) }
    val isMuted = externalMuted ?: internalMuted
    var userPaused by remember(mediaId) { mutableStateOf(false) }
    var showTapPlayIndicator by remember { mutableStateOf(false) }
    var firstFrameRendered by remember(playableUri) { mutableStateOf(false) }
    var playbackProgress by remember(playableUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            userPaused = false
            isBuffering = false
        }
    }

    LaunchedEffect(isBuffering, isPlaying, userPaused, playableUri) {
        if (isPlaying && isBuffering && !userPaused && playableUri != null) {
            delay(15_000L)
            if (isBuffering && isPlaying && !userPaused) {
                isBuffering = false
                playbackFailed = true
            }
        }
    }

    LaunchedEffect(showTapPlayIndicator) {
        if (showTapPlayIndicator) {
            delay(850L)
            showTapPlayIndicator = false
        }
    }

    val currentUserPaused by rememberUpdatedState(userPaused)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnRequestPlay by rememberUpdatedState(onRequestPlay)

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
    ) {
        // Poster / thumbnail layer shown while scrolled off-screen, buffering, or if video failed
        PostMediaImage(
            imageKey = fallbackImageKey,
            localImagePath = fallbackLocalImagePath,
            imageData = fallbackImageData,
            imageUrl = fallbackImageUrl,
            filterTag = filterTag,
            contentDescription = "Video preview",
            modifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (playableUri == null || playbackFailed || !isPlaying) {
                        base.pointerInput(mediaId, isPlaying) {
                            detectTapGestures(
                                onTap = {
                                    if (playbackFailed) {
                                        playbackFailed = false
                                        retryNonce++
                                    } else if (!isPlaying) {
                                        currentOnRequestPlay?.invoke()
                                    }
                                },
                                onDoubleTap = { currentOnDoubleTap?.invoke() }
                            )
                        }
                    } else {
                        base
                    }
                }
        )

        val activeUri = playableUri
        val shouldHoldPlayer = activeUri != null && !playbackFailed && (isPlaying || isPrebuffering)
        if (shouldHoldPlayer) {
            val exoPlayer = remember(activeUri, retryNonce) {
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
                        playWhenReady = isPlaying && !currentUserPaused
                        setMediaItem(MediaItem.fromUri(activeUri))
                        prepare()
                    }
            }

            val musicPlayer = remember(playableMusicUri, retryNonce) {
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
                            playWhenReady = isPlaying && !currentUserPaused
                            setMediaItem(MediaItem.fromUri(uri))
                            prepare()
                        }
                }
            }

            LaunchedEffect(exoPlayer, musicPlayer, userPaused, isPlaying) {
                val shouldPlay = isPlaying && !userPaused
                if (shouldPlay && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                exoPlayer.playWhenReady = shouldPlay
                if (shouldPlay) {
                    if (!exoPlayer.isPlaying) exoPlayer.play()
                } else {
                    exoPlayer.pause()
                }

                musicPlayer?.let { mp ->
                    if (shouldPlay && mp.playbackState == Player.STATE_IDLE) {
                        mp.prepare()
                    }
                    mp.playWhenReady = shouldPlay
                    if (shouldPlay) {
                        if (!mp.isPlaying) mp.play()
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

            LaunchedEffect(exoPlayer, userPaused, showProgressBar) {
                while (showProgressBar && !userPaused) {
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        playbackProgress = (exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                    delay(120L)
                }
            }

            DisposableEffect(lifecycleOwner, exoPlayer, musicPlayer) {
                val playerListener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        firstFrameRendered = true
                        isBuffering = false
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> isBuffering = true
                            Player.STATE_READY -> {
                                playbackFailed = false
                                isBuffering = false
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                exoPlayer.seekTo(0L)
                                if (!currentUserPaused) exoPlayer.play()
                            }
                            else -> isBuffering = false
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w("MomiVideoPlayer", "Playback error for $mediaId: ${error.errorCodeName}", error)
                        isBuffering = false
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
                            val shouldPlay = !currentUserPaused
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
        } else if (!isPlaying && !isPrebuffering && activeUri != null && !playbackFailed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video ready",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if ((isLoadingRemoteVideo && playableUri == null) || (isBuffering && !userPaused && !playbackFailed)) {
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

        if (playbackFailed) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable {
                        playbackFailed = false
                        retryNonce++
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry video",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Tap to retry video",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

        if (showMuteButton && ((playableUri != null && !playbackFailed) || playableMusicUri != null)) {
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
