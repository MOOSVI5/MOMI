package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveStream
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiInlineFeedAdCard
import com.example.ui.components.PostCard
import com.example.ui.components.rememberStartIoNativeAds
import com.example.ui.theme.LumaScriptFamily

@Composable
fun HomeScreen(
    profile: UserProfile?,
    creators: List<UserProfile> = emptyList(),
    followingUids: Set<String> = emptySet(),
    posts: List<Post>,
    stories: List<Story>,
    liveStreams: List<LiveStream> = emptyList(),
    unreadNotificationCount: Int,
    unreadMessageCount: Int,
    onOpenSearch: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenStory: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onGoLiveClick: () -> Unit = {},
    onOpenLiveStream: (LiveStream) -> Unit = {},
    onPostViewed: (Post) -> Unit = {},
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onBookmarkToggle: (Post) -> Unit,
    onFollowToggle: (Post) -> Unit,
    onFollowCreator: (UserProfile, Boolean) -> Unit = { _, _ -> },
    onDeletePost: (Post) -> Unit,
    onReportPost: (Post) -> Unit = {},
    onAuthorClick: (userId: String, username: String, isShowcase: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var feedFilter by remember { mutableStateOf("For You") }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var feedMuted by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val nativeAdState = rememberStartIoNativeAds(count = 3)

    val displayedPosts = remember(posts, feedFilter, profile?.username) {
        when (feedFilter) {
            "Following" -> posts.filter { it.isFollowingAuthor || it.authorUsername == profile?.username }
            "Saved" -> posts.filter { it.isBookmarked }
            else -> posts
        }
    }

    val suggestedCreators = remember(creators, profile?.userId) {
        creators.filter { it.userId.isNotBlank() && it.userId != profile?.userId }
    }

    val playablePostIds = remember(displayedPosts) {
        displayedPosts.asSequence()
            .filter {
                it.isVideoPost ||
                    it.audioTitle.isNotBlank() ||
                    !it.localAudioPath.isNullOrBlank() ||
                    !it.audioData.isNullOrBlank()
            }
            .map { it.id }
            .toSet()
    }

    // Instagram/TikTok-style viewport-centered playback:
    // activates whichever video or music post sits closest to screen center.
    val activePlayablePostId by remember(playablePostIds) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            val viewportCenter = (viewportStart + viewportEnd) / 2f

            layoutInfo.visibleItemsInfo
                .asSequence()
                .mapNotNull { item ->
                    val key = item.key as? String ?: return@mapNotNull null
                    if (!playablePostIds.contains(key)) return@mapNotNull null
                    val visibleTop = maxOf(item.offset, viewportStart)
                    val visibleBottom = minOf(item.offset + item.size, viewportEnd)
                    val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0)
                    val ratio = if (item.size > 0) visibleHeight.toFloat() / item.size.toFloat() else 0f
                    if (ratio < 0.32f && visibleHeight < 220) return@mapNotNull null
                    val itemCenter = item.offset + item.size / 2f
                    val distanceFromCenter = kotlin.math.abs(itemCenter - viewportCenter)
                    Triple(key, ratio, distanceFromCenter)
                }
                .minWithOrNull(
                    compareBy<Triple<String, Float, Float>> { it.third }
                        .thenByDescending { it.second }
                )
                ?.first
        }
    }

    val ownStory = remember(stories) { stories.firstOrNull { it.isOwnStory } }
    val friendStories = remember(stories) { stories.filter { !it.isOwnStory } }

    LaunchedEffect(activePlayablePostId, displayedPosts) {
        val targetId = activePlayablePostId ?: return@LaunchedEffect
        delay(600L)
        displayedPosts.firstOrNull { it.id == targetId }?.let(onPostViewed)
    }

    LaunchedEffect(displayedPosts, listState) {
        val postById = displayedPosts.associateBy { it.id }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
        }.collectLatest { visibleKeys ->
            delay(900L)
            visibleKeys.forEach { key ->
                postById[key]?.let(onPostViewed)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Luma Top Bar (Instagram-exact layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { filterMenuOpen = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Momi",
                        fontFamily = LumaScriptFamily,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Switch feed view",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false }
                ) {
                    listOf("For You", "Following", "Saved").forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontWeight = if (feedFilter == option) FontWeight.Bold else FontWeight.Normal,
                                    color = if (feedFilter == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                feedFilter = option
                                filterMenuOpen = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenMarketplace,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_marketplace_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storefront,
                        contentDescription = "Open Momi Marketplace",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search creators and posts",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }

                // Notifications / Activity Heart
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Activity",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    if (unreadNotificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, end = 10.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }

                // Direct Messages Paper Plane
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onOpenMessages,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("messages_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Messages",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (unreadMessageCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 6.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadMessageCount.coerceAtMost(9).toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Stories Tray
            item(key = "stories_tray") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Own Story Item
                    item(key = "own_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(74.dp)
                        ) {
                            LumaAvatar(
                                username = profile?.username ?: "aria.studio",
                                avatarKey = profile?.avatarKey ?: "avatar_aria",
                                avatarData = profile?.avatarData,
                                size = 70.dp,
                                hasActiveStory = ownStory != null,
                                isSeenStory = ownStory?.isSeen ?: false,
                                showAddBadge = ownStory == null,
                                onClick = {
                                    if (ownStory != null) onOpenStory(ownStory) else onAddStoryClick()
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Your story",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Go Live Launcher Tile
                    item(key = "go_live_tile") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onGoLiveClick)
                                .testTag("home_go_live_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.5.dp,
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFFD92D4E), Color(0xFFFF6B6B), MaterialTheme.colorScheme.primary)
                                        ),
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD92D4E).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sensors,
                                    contentDescription = "Go Live",
                                    tint = Color(0xFFD92D4E),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Go Live",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD92D4E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Active Live Broadcasts
                    items(liveStreams, key = { "live_${it.id}" }) { stream ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(74.dp)
                                .clickable { onOpenLiveStream(stream) }
                                .testTag("live_stream_tile_${stream.id}")
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                LumaAvatar(
                                    username = stream.username,
                                    avatarKey = stream.avatarKey,
                                    avatarData = stream.avatarData,
                                    size = 70.dp,
                                    hasActiveStory = true,
                                    isSeenStory = false,
                                    onClick = { onOpenLiveStream(stream) }
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFD92D4E))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stream.username,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Friend Stories
                    items(friendStories, key = { it.id }) { story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(74.dp)
                        ) {
                            LumaAvatar(
                                username = story.username,
                                avatarKey = story.avatarKey,
                                avatarData = story.avatarData,
                                size = 70.dp,
                                hasActiveStory = true,
                                isSeenStory = story.isSeen,
                                onClick = { onOpenStory(story) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = story.username,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (story.isSeen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }

            if (displayedPosts.isEmpty()) {
                item(key = "empty_feed") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (feedFilter == "Saved") "No saved posts yet" else "Your feed is quiet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the bookmark icon on any post or share a new frame to see it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(displayedPosts, key = { _, post -> post.id }) { index, post ->
                    PostCard(
                        post = post,
                        isOwnPost = post.userId == profile?.userId && !post.id.startsWith("showcase_"),
                        isPlaybackActive = post.id == activePlayablePostId,
                        externalMuted = feedMuted,
                        onMuteChange = { feedMuted = it },
                        onLikeToggle = { onLikeToggle(post) },
                        onDoubleTapLike = { onDoubleTapLike(post) },
                        onCommentClick = { onCommentClick(post) },
                        onShareClick = { onShareClick(post) },
                        onBookmarkToggle = { onBookmarkToggle(post) },
                        onFollowToggle = { onFollowToggle(post) },
                        onDeletePost = { onDeletePost(post) },
                        onReportPost = { onReportPost(post) },
                        onAuthorClick = {
                            onAuthorClick(
                                post.userId,
                                post.authorUsername,
                                post.id.startsWith("showcase_")
                            )
                        }
                    )
                    if (index == 0 && suggestedCreators.isNotEmpty()) {
                        SuggestedCreatorsSection(
                            creators = suggestedCreators,
                            followingUids = followingUids,
                            onCreatorClick = { creator ->
                                onAuthorClick(creator.userId, creator.username, false)
                            },
                            onFollowClick = { creator ->
                                onFollowCreator(creator, followingUids.contains(creator.userId))
                            },
                            onSeeAllClick = onOpenSearch
                        )
                    }
                    if (index % 3 == 0) {
                        MomiInlineFeedAdCard(
                            slotIndex = index / 3,
                            nativeAdState = nativeAdState
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedCreatorsSection(
    creators: List<UserProfile>,
    followingUids: Set<String>,
    onCreatorClick: (UserProfile) -> Unit,
    onFollowClick: (UserProfile) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Suggested for you",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAllClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(creators.take(12), key = { "suggested_${it.userId}" }) { creator ->
                val isFollowing = followingUids.contains(creator.userId)
                Column(
                    modifier = Modifier
                        .width(142.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .clickable { onCreatorClick(creator) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LumaAvatar(
                        username = creator.username,
                        avatarKey = creator.avatarKey,
                        avatarData = creator.avatarData,
                        size = 64.dp,
                        hasActiveStory = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = creator.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (creator.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "@${creator.username}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isFollowing) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable { onFollowClick(creator) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFollowing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}
