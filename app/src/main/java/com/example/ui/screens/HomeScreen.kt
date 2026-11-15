package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiInlineFeedAdCard
import com.example.ui.components.PostCard
import com.example.ui.theme.LumaScriptFamily

@Composable
fun HomeScreen(
    profile: UserProfile?,
    posts: List<Post>,
    stories: List<Story>,
    unreadNotificationCount: Int,
    unreadMessageCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenStory: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onBookmarkToggle: (Post) -> Unit,
    onFollowToggle: (Post) -> Unit,
    onDeletePost: (Post) -> Unit,
    onAuthorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var feedFilter by remember { mutableStateOf("For You") }
    var filterMenuOpen by remember { mutableStateOf(false) }

    val displayedPosts = remember(posts, feedFilter, profile?.username) {
        when (feedFilter) {
            "Following" -> posts.filter { it.isFollowingAuthor || it.authorUsername == profile?.username }
            "Saved" -> posts.filter { it.isBookmarked }
            else -> posts
        }
    }

    val ownStory = remember(stories) { stories.firstOrNull { it.isOwnStory } }
    val friendStories = remember(stories) { stories.filter { !it.isOwnStory } }

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

                    // Friend Stories
                    items(friendStories, key = { it.id }) { story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(74.dp)
                        ) {
                            LumaAvatar(
                                username = story.username,
                                avatarKey = story.avatarKey,
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
                        onLikeToggle = { onLikeToggle(post) },
                        onDoubleTapLike = { onDoubleTapLike(post) },
                        onCommentClick = { onCommentClick(post) },
                        onShareClick = { onShareClick(post) },
                        onBookmarkToggle = { onBookmarkToggle(post) },
                        onFollowToggle = { onFollowToggle(post) },
                        onDeletePost = { onDeletePost(post) },
                        onAuthorClick = { onAuthorClick(post.authorUsername) }
                    )
                    if (index == 1 || (index > 1 && (index - 1) % 3 == 0)) {
                        MomiInlineFeedAdCard()
                    }
                }
            }
        }
    }
}
