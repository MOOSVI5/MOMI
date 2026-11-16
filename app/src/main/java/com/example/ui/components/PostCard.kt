package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Post
import com.example.util.MediaAssets
import kotlinx.coroutines.delay

@Composable
fun PostCard(
    post: Post,
    isOwnPost: Boolean,
    isPlaybackActive: Boolean = false,
    externalMuted: Boolean? = null,
    onMuteChange: ((Boolean) -> Unit)? = null,
    onLikeToggle: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onFollowToggle: () -> Unit,
    onDeletePost: (() -> Unit)? = null,
    onAuthorClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var captionExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(720L)
            showDoubleTapHeart = false
        }
    }

    val likeIconScale by animateFloatAsState(
        targetValue = if (post.isLiked) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "likeScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAuthorClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LumaAvatar(
                    username = post.authorUsername,
                    avatarKey = post.authorAvatarKey,
                    avatarData = post.authorAvatarData,
                    size = 40.dp,
                    hasActiveStory = true,
                    isSeenStory = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorUsername,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (post.authorVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified creator",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    val subtitleText = when {
                        post.audioTitle.isNotBlank() -> post.audioTitle
                        post.location.isNotBlank() -> post.location
                        else -> ""
                    }
                    if (subtitleText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (post.audioTitle.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (!isOwnPost && !post.isFollowingAuthor) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onFollowToggle)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Follow",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (post.isBookmarked) "Remove from Saved" else "Save post") },
                        onClick = {
                            menuExpanded = false
                            onBookmarkToggle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share post") },
                        onClick = {
                            menuExpanded = false
                            onShareClick()
                        }
                    )
                    if (!isOwnPost) {
                        DropdownMenuItem(
                            text = { Text(if (post.isFollowingAuthor) "Unfollow @${post.authorUsername}" else "Follow @${post.authorUsername}") },
                            onClick = {
                                menuExpanded = false
                                onFollowToggle()
                            }
                        )
                    }
                    if (isOwnPost && onDeletePost != null) {
                        DropdownMenuItem(
                            text = { Text("Delete post", color = MaterialTheme.colorScheme.tertiary) },
                            onClick = {
                                menuExpanded = false
                                onDeletePost()
                            }
                        )
                    }
                }
            }
        }

        // Edge-to-Edge Media with Double-Tap Coral Heart (or Live ExoPlayer for Video Posts)
        val mediaAspectRatio = if (post.isVideoPost) 0.85f else 1f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(mediaAspectRatio)
                .clipToBounds()
                .background(if (post.isVideoPost) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
                .let { base ->
                    if (!post.isVideoPost) {
                        base.pointerInput(post.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    showDoubleTapHeart = true
                                    onDoubleTapLike()
                                }
                            )
                        }
                    } else {
                        base
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (post.isVideoPost) {
                MomiVideoPlayer(
                    mediaId = post.id,
                    localVideoPath = post.localVideoPath,
                    videoData = post.videoData,
                    videoChunksCount = post.videoChunksCount,
                    videoDurationMs = post.videoDurationMs,
                    fallbackImageKey = post.imageKey,
                    fallbackLocalImagePath = post.localImagePath,
                    fallbackImageData = post.imageData,
                    filterTag = post.filterTag,
                    isPlaying = isPlaybackActive,
                    localAudioPath = post.localAudioPath,
                    audioTitle = post.audioTitle,
                    initialMuted = true,
                    externalMuted = externalMuted,
                    onMuteChange = onMuteChange,
                    showMuteButton = true,
                    showDurationBadge = true,
                    contentScale = ContentScale.Fit,
                    onDoubleTap = {
                        showDoubleTapHeart = true
                        onDoubleTapLike()
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(mediaAspectRatio)
                )
            } else {
                PostMediaImage(
                    imageKey = post.imageKey,
                    localImagePath = post.localImagePath,
                    imageData = post.imageData,
                    filterTag = post.filterTag,
                    contentDescription = "Post by ${post.authorUsername}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(mediaAspectRatio)
                )
            }

            if (post.filterTag != "NONE") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = post.filterTag.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showDoubleTapHeart,
                enter = scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(),
                exit = scaleOut(targetScale = 1.25f) + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(96.dp)
                )
            }
        }

        // Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(likeIconScale)
                    )
                }
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bookmark_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (post.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save post",
                    tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // Likes, Caption, Comments Link, Timestamp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = "${MediaAssets.formatCompactCount(post.likesCount)} likes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            val primaryColor = MaterialTheme.colorScheme.primary
            val onBgColor = MaterialTheme.colorScheme.onBackground
            val annotatedCaption = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onBgColor)) {
                    append(post.authorUsername)
                    append("  ")
                }
                post.caption.split(" ").forEachIndexed { index, word ->
                    if (index > 0) append(" ")
                    if (word.startsWith("#") || word.startsWith("@")) {
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = onBgColor)) {
                            append(word)
                        }
                    }
                }
            }

            Text(
                text = annotatedCaption,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { captionExpanded = !captionExpanded }
            )

            if (post.commentsCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View all ${post.commentsCount} comments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCommentClick)
                        .padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Add a comment...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCommentClick)
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = MediaAssets.formatRelativeTime(post.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}
