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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Post
import com.example.util.MediaAssets
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    post: Post,
    isOwnPost: Boolean,
    isAdminViewActive: Boolean = false,
    isPlaybackActive: Boolean = false,
    isDetailMode: Boolean = false,
    externalMuted: Boolean? = null,
    onMuteChange: ((Boolean) -> Unit)? = null,
    onPostClick: () -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onLikeToggle: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onFollowToggle: () -> Unit,
    onBoostPost: (() -> Unit)? = null,
    onToggleRecommended: ((Boolean) -> Unit)? = null,
    onDeletePost: (() -> Unit)? = null,
    onReportPost: (() -> Unit)? = null,
    onAuthorClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var captionExpanded by remember(post.id, isDetailMode) { mutableStateOf(isDetailMode) }

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
                    avatarUrl = post.authorAvatarUrl,
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
                    if (post.isBoosted || post.recommendedLabel.isNotBlank()) {
                        val badgeLabel = post.recommendedLabel.ifBlank {
                            if (post.boostTier == "OWNER_RECOMMENDED") "Recommended by Momi" else "Boosted"
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("post_boost_badge_${post.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = badgeLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
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
                        if (onReportPost != null) {
                            DropdownMenuItem(
                                text = { Text("Report post", color = MaterialTheme.colorScheme.secondary) },
                                onClick = {
                                    menuExpanded = false
                                    onReportPost()
                                }
                            )
                        }
                    }
                    if (isOwnPost && onBoostPost != null && !post.id.startsWith("showcase_")) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (post.isBoosted) "Upgrade post boost" else "Boost post with credits",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onBoostPost()
                            }
                        )
                    }
                    if (isAdminViewActive && onToggleRecommended != null && !post.id.startsWith("showcase_")) {
                        val isRecommended = post.boostTier == "OWNER_RECOMMENDED"
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isRecommended) "Remove Momi recommendation" else "Recommend post (Owner)",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleRecommended(!isRecommended)
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

        // Edge-to-Edge Media with Single-Tap Open Post, Double-Tap Coral Heart, Carousel Pager, ExoPlayer, Audio for Photo+Music
        val mediaAspectRatio = post.numericAspectRatio
        val imageFrames = remember(
            post.id,
            post.imageKey,
            post.localImagePath,
            post.imageData,
            post.imageUrl,
            post.localExtraImagePaths,
            post.extraImageDatas,
            post.extraImageUrls
        ) {
            post.allImageFrames
        }
        val hasPhotoMusic = !post.isVideoPost && (
            post.audioTitle.isNotBlank() ||
                !post.localAudioPath.isNullOrBlank() ||
                !post.audioData.isNullOrBlank() ||
                !post.audioUrl.isNullOrBlank()
            )
        var localPhotoMuted by remember(post.id) { mutableStateOf(true) }
        val isPhotoMuted = externalMuted ?: localPhotoMuted

        if (hasPhotoMusic) {
            MomiMusicAudioPlayer(
                mediaId = post.id,
                localAudioPath = post.localAudioPath,
                audioData = post.audioData,
                audioUrl = post.audioUrl,
                audioTitle = post.audioTitle,
                isPlaying = isPlaybackActive,
                isMuted = isPhotoMuted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(mediaAspectRatio)
                .clipToBounds()
                .background(if (post.isVideoPost) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
                .testTag("post_media_${post.id}")
                .let { base ->
                    if (!post.isVideoPost) {
                        base.pointerInput(post.id, isDetailMode, hasPhotoMusic, isPhotoMuted) {
                            detectTapGestures(
                                onTap = {
                                    if (!isDetailMode) {
                                        onPostClick()
                                    } else if (hasPhotoMusic) {
                                        val nextMuted = !isPhotoMuted
                                        if (onMuteChange != null) onMuteChange(nextMuted) else localPhotoMuted = nextMuted
                                    }
                                },
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
                    videoUrl = post.videoUrl,
                    videoChunksCount = post.videoChunksCount,
                    videoDurationMs = post.videoDurationMs,
                    fallbackImageKey = post.imageKey,
                    fallbackLocalImagePath = post.localImagePath,
                    fallbackImageData = post.imageData,
                    fallbackImageUrl = post.imageUrl,
                    filterTag = post.filterTag,
                    isPlaying = isPlaybackActive,
                    localAudioPath = post.localAudioPath,
                    audioData = post.audioData,
                    audioUrl = post.audioUrl,
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
            } else if (imageFrames.size > 1) {
                val pagerState = rememberPagerState(pageCount = { imageFrames.size })
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val frame = imageFrames[page]
                        PostMediaImage(
                            imageKey = frame.imageKey,
                            localImagePath = frame.localImagePath,
                            imageData = frame.imageData,
                            imageUrl = frame.imageUrl,
                            filterTag = post.filterTag,
                            contentDescription = "Post ${page + 1} by ${post.authorUsername}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${imageFrames.size}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(imageFrames.size) { dotIdx ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == dotIdx) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == dotIdx) Color.White
                                        else Color.White.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }
                }
            } else {
                PostMediaImage(
                    imageKey = post.imageKey,
                    localImagePath = post.localImagePath,
                    imageData = post.imageData,
                    imageUrl = post.imageUrl,
                    filterTag = post.filterTag,
                    contentDescription = "Post by ${post.authorUsername}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(mediaAspectRatio)
                )
            }

            if (hasPhotoMusic) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            val nextMuted = !isPhotoMuted
                            if (onMuteChange != null) onMuteChange(nextMuted) else localPhotoMuted = nextMuted
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
                            imageVector = if (isPhotoMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isPhotoMuted) "Unmute music" else "Mute music",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isDetailMode && post.isVideoPost) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable(onClick = onPostClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("open_post_pill_${post.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Open post",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Open",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (post.filterTag != "NONE") {
                val filterLabel = MediaAssets.postFilterOptions
                    .firstOrNull { it.tag == post.filterTag }?.label
                    ?: post.filterTag.lowercase().replaceFirstChar { it.uppercase() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = filterLabel,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${MediaAssets.formatCompactCount(post.likesCount)} likes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("post_views_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Views",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${MediaAssets.formatCompactCount(post.viewsCount)} views",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val primaryColor = MaterialTheme.colorScheme.primary
            val onBgColor = MaterialTheme.colorScheme.onBackground
            val annotatedCaption = remember(post.id, post.authorUsername, post.caption, primaryColor, onBgColor) {
                buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "author_${post.authorUsername}",
                            styles = TextLinkStyles(
                                style = SpanStyle(fontWeight = FontWeight.Bold, color = onBgColor)
                            )
                        ) {
                            onAuthorClick()
                        }
                    ) {
                        append(post.authorUsername)
                    }
                    append("  ")

                    val tokenRegex = Regex("(#\\w+|@[a-zA-Z0-9._]+)")
                    var cursor = 0
                    for (match in tokenRegex.findAll(post.caption)) {
                        if (match.range.first > cursor) {
                            withStyle(SpanStyle(color = onBgColor)) {
                                append(post.caption.substring(cursor, match.range.first))
                            }
                        }
                        val token = match.value
                        if (token.startsWith("#")) {
                            val tagValue = token.removePrefix("#")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "hash_$tagValue",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
                                    )
                                ) {
                                    onHashtagClick(tagValue)
                                }
                            ) {
                                append(token)
                            }
                        } else {
                            val mentionValue = token.removePrefix("@")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "mention_$mentionValue",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
                                    )
                                ) {
                                    onMentionClick(mentionValue)
                                }
                            ) {
                                append(token)
                            }
                        }
                        cursor = match.range.last + 1
                    }
                    if (cursor < post.caption.length) {
                        withStyle(SpanStyle(color = onBgColor)) {
                            append(post.caption.substring(cursor))
                        }
                    }
                }
            }

            Text(
                text = annotatedCaption,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isDetailMode && !captionExpanded) {
                            captionExpanded = true
                        } else if (!isDetailMode) {
                            onPostClick()
                        }
                    }
            )

            val extraTaggedUsers = remember(post.taggedUsernames, post.caption) {
                post.taggedUsernames
                    .map { it.trim().removePrefix("@") }
                    .filter { it.isNotBlank() && !post.caption.contains("@$it", ignoreCase = true) }
            }
            val extraHashtags = remember(post.hashtags, post.caption) {
                post.hashtags
                    .map { it.trim().removePrefix("#") }
                    .filter { it.isNotBlank() && !post.caption.contains("#$it", ignoreCase = true) }
            }
            if (extraTaggedUsers.isNotEmpty() || extraHashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    extraTaggedUsers.forEach { handle ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                .clickable { onMentionClick(handle) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "@$handle",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    extraHashtags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                                .clickable { onHashtagClick(tag) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

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
