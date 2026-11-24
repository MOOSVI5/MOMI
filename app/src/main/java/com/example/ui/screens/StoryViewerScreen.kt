package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import com.example.data.model.Story
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiMusicAudioPlayer
import com.example.ui.components.MomiVideoPlayer
import com.example.ui.components.PostMediaImage
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MaroonBright
import com.example.ui.theme.SilkBlueLight
import com.example.util.MediaAssets
import kotlinx.coroutines.delay

@Composable
fun StoryViewerScreen(
    stories: List<Story>,
    initialStoryId: String,
    onMarkSeen: (String) -> Unit,
    onAuthorClick: (userId: String, username: String) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val startIndex = remember(stories, initialStoryId) {
        stories.indexOfFirst { it.id == initialStoryId }.coerceAtLeast(0)
    }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var storyMuted by remember { mutableStateOf(false) }
    var liked by remember(currentIndex) { mutableStateOf(false) }

    val currentStory = stories.getOrNull(currentIndex) ?: stories.first()

    LaunchedEffect(currentStory.id) {
        onMarkSeen(currentStory.id)
        progress = 0f
    }

    val stepFraction = remember(currentStory.id, currentStory.isVideoStory, currentStory.videoDurationMs) {
        val storyDurationMs = if (currentStory.isVideoStory && currentStory.videoDurationMs > 0L) {
            currentStory.videoDurationMs.coerceIn(5_000L, 20_000L)
        } else {
            5_000L
        }
        50f / storyDurationMs.toFloat()
    }

    LaunchedEffect(currentIndex, isPaused) {
        while (progress < 1f) {
            delay(50L)
            if (!isPaused) {
                progress += stepFraction
            }
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
            progress = 0f
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(DarkBackground)
            .pointerInput(currentIndex, stories.size) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        if (offset.x < size.width * 0.35f) {
                            if (currentIndex > 0) {
                                currentIndex--
                                progress = 0f
                            } else {
                                progress = 0f
                            }
                        } else {
                            if (currentIndex < stories.lastIndex) {
                                currentIndex++
                                progress = 0f
                            } else {
                                onClose()
                            }
                        }
                    }
                )
            }
    ) {
        if (currentStory.isVideoStory) {
            MomiVideoPlayer(
                mediaId = currentStory.id,
                localVideoPath = currentStory.localVideoPath,
                videoData = currentStory.videoData,
                videoUrl = currentStory.videoUrl,
                videoChunksCount = currentStory.videoChunksCount,
                remoteCollection = "stories",
                videoDurationMs = currentStory.videoDurationMs,
                fallbackImageKey = currentStory.imageKey,
                fallbackLocalImagePath = currentStory.localImagePath,
                fallbackImageData = currentStory.imageData,
                fallbackImageUrl = currentStory.imageUrl,
                filterTag = "NONE",
                isPlaying = !isPaused,
                localAudioPath = currentStory.localAudioPath,
                audioData = currentStory.audioData,
                audioUrl = currentStory.audioUrl,
                audioTitle = currentStory.audioTitle,
                initialMuted = false,
                externalMuted = storyMuted,
                onMuteChange = { storyMuted = it },
                showMuteButton = true,
                showDurationBadge = false,
                muteButtonAlignment = Alignment.TopEnd,
                muteButtonPadding = PaddingValues(top = 92.dp, end = 14.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PostMediaImage(
                imageKey = currentStory.imageKey,
                localImagePath = currentStory.localImagePath,
                imageData = currentStory.imageData,
                imageUrl = currentStory.imageUrl,
                filterTag = "NONE",
                contentDescription = "Story by ${currentStory.username}",
                modifier = Modifier.fillMaxSize()
            )
            val hasMusic = currentStory.audioTitle.isNotBlank() ||
                !currentStory.localAudioPath.isNullOrBlank() ||
                !currentStory.audioData.isNullOrBlank() ||
                !currentStory.audioUrl.isNullOrBlank()
            if (hasMusic) {
                MomiMusicAudioPlayer(
                    mediaId = currentStory.id,
                    localAudioPath = currentStory.localAudioPath,
                    audioData = currentStory.audioData,
                    audioUrl = currentStory.audioUrl,
                    audioTitle = currentStory.audioTitle,
                    isPlaying = !isPaused,
                    isMuted = storyMuted
                )
                IconButton(
                    onClick = { storyMuted = !storyMuted },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 92.dp, end = 14.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (storyMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (storyMuted) "Unmute story music" else "Mute story music",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Top Scrim + Progress Segments + Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    val fill = when {
                        idx < currentIndex -> 1f
                        idx == currentIndex -> progress.coerceIn(0f, 1f)
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill)
                                .fillMaxHeight()
                                .background(SilkBlueLight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAuthorClick(currentStory.userId, currentStory.username) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    LumaAvatar(
                        username = currentStory.username,
                        avatarKey = currentStory.avatarKey,
                        avatarData = currentStory.avatarData,
                        avatarUrl = currentStory.avatarUrl,
                        size = 34.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentStory.username,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = MediaAssets.formatRelativeTime(currentStory.createdAt),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close story",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Caption & Reaction Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            if (currentStory.audioTitle.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentStory.audioTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (currentStory.caption.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = currentStory.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Send a reaction to ${currentStory.username}...",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { liked = !liked },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like story",
                        tint = if (liked) MaroonBright else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = liked,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 84.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaroonBright,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
