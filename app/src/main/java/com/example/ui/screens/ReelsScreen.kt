package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Post
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiVideoPlayer
import com.example.ui.components.PostMediaImage
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MaroonBright
import com.example.ui.theme.SilkBlueLight
import com.example.util.MediaAssets
import kotlinx.coroutines.delay

@Composable
fun ReelsScreen(
    reels: List<Post>,
    currentUserId: String,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onBookmarkToggle: (Post) -> Unit,
    onFollowToggle: (Post) -> Unit,
    onAuthorClick: (Post) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (reels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No clips yet",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { reels.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        VerticalPager(
            state = pagerState,
            key = { reels[it].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val reel = reels[page]
            ReelPageItem(
                reel = reel,
                isActivePage = pagerState.currentPage == page,
                isOwnReel = reel.userId == currentUserId && !reel.id.startsWith("showcase_"),
                onLikeToggle = { onLikeToggle(reel) },
                onDoubleTapLike = { onDoubleTapLike(reel) },
                onCommentClick = { onCommentClick(reel) },
                onShareClick = { onShareClick(reel) },
                onBookmarkToggle = { onBookmarkToggle(reel) },
                onFollowToggle = { onFollowToggle(reel) },
                onAuthorClick = { onAuthorClick(reel) }
            )
        }

        // Top Overlay Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clips",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(SilkBlueLight.copy(alpha = 0.2f))
                    .border(1.dp, SilkBlueLight.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Momi Studio",
                    color = SilkBlueLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReelPageItem(
    reel: Post,
    isActivePage: Boolean,
    isOwnReel: Boolean,
    onLikeToggle: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onFollowToggle: () -> Unit,
    onAuthorClick: () -> Unit
) {
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(700L)
            showHeartBurst = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val zoomScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "zoom"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .let { base ->
                if (!reel.isVideoPost) {
                    base.pointerInput(reel.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showHeartBurst = true
                                onDoubleTapLike()
                            }
                        )
                    }
                } else {
                    base
                }
            }
    ) {
        if (reel.isVideoPost) {
            MomiVideoPlayer(
                mediaId = reel.id,
                localVideoPath = reel.localVideoPath,
                videoData = reel.videoData,
                videoChunksCount = reel.videoChunksCount,
                videoDurationMs = reel.videoDurationMs,
                fallbackImageKey = reel.imageKey,
                fallbackLocalImagePath = reel.localImagePath,
                fallbackImageData = reel.imageData,
                filterTag = reel.filterTag,
                isPlaying = isActivePage,
                localAudioPath = reel.localAudioPath,
                audioTitle = reel.audioTitle,
                initialMuted = false,
                showMuteButton = true,
                showDurationBadge = false,
                muteButtonAlignment = Alignment.TopEnd,
                muteButtonPadding = PaddingValues(top = 92.dp, end = 16.dp),
                contentScale = ContentScale.Crop,
                onDoubleTap = {
                    showHeartBurst = true
                    onDoubleTapLike()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PostMediaImage(
                imageKey = reel.imageKey,
                localImagePath = reel.localImagePath,
                imageData = reel.imageData,
                filterTag = reel.filterTag,
                contentDescription = "Clip by ${reel.authorUsername}",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(zoomScale)
            )
        }

        // Top & Bottom Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        // Double-Tap Coral Heart
        AnimatedVisibility(
            visible = showHeartBurst,
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 1.25f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaroonBright,
                modifier = Modifier.size(104.dp)
            )
        }

        // Bottom Content + Right Action Stack
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAuthorClick)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        LumaAvatar(
                            username = reel.authorUsername,
                            avatarKey = reel.authorAvatarKey,
                            avatarData = reel.authorAvatarData,
                            size = 38.dp,
                            hasActiveStory = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = reel.authorUsername,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (reel.authorVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = SilkBlueLight,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (!isOwnReel) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (reel.isFollowingAuthor) Color.White.copy(alpha = 0.45f) else SilkBlueLight,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(onClick = onFollowToggle)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (reel.isFollowingAuthor) "Following" else "Follow",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = reel.caption,
                    color = Color.White.copy(alpha = 0.94f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Audio Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = SilkBlueLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reel.audioTitle.ifBlank { "${reel.authorDisplayName} · Original Audio" },
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Vertical Action Stack
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onLikeToggle,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like clip",
                            tint = if (reel.isLiked) MaroonBright else Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = MediaAssets.formatCompactCount(reel.likesCount),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onCommentClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comment",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = reel.commentsCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share clip",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (reel.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save clip",
                        tint = if (reel.isBookmarked) SilkBlueLight else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, SilkBlueLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
