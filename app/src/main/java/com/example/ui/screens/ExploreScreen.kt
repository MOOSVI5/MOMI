package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiAnchoredExploreBanner
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets

private val ExploreCategories = listOf("For You", "Clips", "Travel", "Architecture", "Coffee", "Night", "Minimal")
private val DefaultTrendingHashtags = listOf("momi", "travel", "streetstyle", "night", "coffee", "minimal", "architecture")

@Composable
fun ExploreScreen(
    posts: List<Post>,
    creators: List<UserProfile> = emptyList(),
    currentUserId: String = "",
    followingUids: Set<String> = emptySet(),
    searchQuery: String,
    selectedCategory: String,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onPostClick: (Post) -> Unit,
    onCreatorClick: (UserProfile) -> Unit = {},
    onFollowCreator: (UserProfile, Boolean) -> Unit = { _, _ -> },
    onMessageCreator: (UserProfile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var useNotesGrid by remember { mutableStateOf(true) }

    val discoverCreators = remember(creators, currentUserId) {
        creators.filter { it.userId.isNotBlank() && it.userId != currentUserId }
    }
    val trendingHashtags = remember(posts) {
        val fromPosts = posts.flatMap { it.allHashtags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        (fromPosts + DefaultTrendingHashtags).distinct().take(12)
    }
    val matchingCreators = remember(discoverCreators, searchQuery) {
        val q = searchQuery.trim().removePrefix("@")
        if (q.isEmpty()) {
            emptyList()
        } else {
            discoverCreators.filter { creator ->
                creator.username.contains(q, ignoreCase = true) ||
                    creator.displayName.contains(q, ignoreCase = true) ||
                    creator.category.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search Bar — uses heightIn(min = 48.dp) so text never clips on large system fonts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search creators, tags, or places",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input")
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ExploreCategories) { category ->
                    val selected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onCategorySelected(category) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = { useNotesGrid = !useNotesGrid },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("explore_layout_toggle")
            ) {
                Icon(
                    imageVector = if (useNotesGrid) Icons.Filled.GridView else Icons.Filled.ViewAgenda,
                    contentDescription = if (useNotesGrid) "Switch to 3-column grid" else "Switch to Discover Notes cards",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Trending Hashtags Strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(trendingHashtags, key = { "trending_hash_$it" }) { tag ->
                val isSelected = searchQuery.trim().equals("#$tag", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                        .clickable {
                            onSearchQueryChange(if (isSelected) "" else "#$tag")
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("explore_hashtag_$tag")
                ) {
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (posts.isEmpty() && matchingCreators.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No matching creators or posts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try another keyword or clear your category filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val chunks = posts.chunked(3)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (searchQuery.trim().isEmpty() && discoverCreators.isNotEmpty()) {
                    item(key = "discover_creators_row") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "Creators",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(discoverCreators.take(15), key = { "explore_creator_${it.userId}" }) { creator ->
                                    Column(
                                        modifier = Modifier
                                            .width(76.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onCreatorClick(creator) }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        LumaAvatar(
                                            username = creator.username,
                                            avatarKey = creator.avatarKey,
                                            avatarData = creator.avatarData,
                                            size = 56.dp,
                                            hasActiveStory = true
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = creator.username,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (creator.isVerified) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = "Verified",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (matchingCreators.isNotEmpty()) {
                    item(key = "matching_creators_header") {
                        Text(
                            text = "Creators",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    items(matchingCreators, key = { "search_creator_${it.userId}" }) { creator ->
                        val isFollowing = followingUids.contains(creator.userId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCreatorClick(creator) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LumaAvatar(
                                username = creator.username,
                                avatarKey = creator.avatarKey,
                                avatarData = creator.avatarData,
                                size = 46.dp,
                                hasActiveStory = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = creator.displayName,
                                        style = MaterialTheme.typography.titleSmall,
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onMessageCreator(creator) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .clickable { onFollowCreator(creator, isFollowing) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    if (posts.isNotEmpty()) {
                        item(key = "creators_posts_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }

                if (useNotesGrid) {
                    val noteRows = posts.chunked(2)
                    noteRows.forEachIndexed { rowIdx, pair ->
                        item(key = "notes_row_${rowIdx}_${pair.first().id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEachIndexed { colIdx, post ->
                                    ExploreNoteCard(
                                        post = post,
                                        tallRatio = (rowIdx + colIdx) % 2 == 0,
                                        onClick = { onPostClick(post) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    chunks.forEachIndexed { rowIndex, rowItems ->
                        item(key = "explore_row_${rowIndex}_${rowItems.first().id}") {
                            if (rowIndex % 2 == 0 && rowItems.size == 3) {
                                // Featured Asymmetric Row: 2/3 large tile + 1/3 stacked column
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(256.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    ExploreGridTile(
                                        post = rowItems[0],
                                        onClick = { onPostClick(rowItems[0]) },
                                        modifier = Modifier
                                            .weight(2f)
                                            .fillMaxHeight()
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        ExploreGridTile(
                                            post = rowItems[1],
                                            onClick = { onPostClick(rowItems[1]) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        )
                                        ExploreGridTile(
                                            post = rowItems[2],
                                            onClick = { onPostClick(rowItems[2]) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                            } else {
                                // Standard 3-Column Square Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    rowItems.forEach { post ->
                                        ExploreGridTile(
                                            post = post,
                                            onClick = { onPostClick(post) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        MomiAnchoredExploreBanner()
    }
}

@Composable
private fun ExploreGridTile(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("explore_tile_${post.id}")
    ) {
        PostMediaImage(
            imageKey = post.imageKey,
            localImagePath = post.localImagePath,
            imageData = post.imageData,
            filterTag = post.filterTag,
            contentDescription = "Explore post by ${post.authorUsername}",
            modifier = Modifier.fillMaxSize()
        )
        if (post.isReel || post.isVideoPost) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video clip",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                if (post.videoDurationMs > 0L) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = MediaAssets.formatDuration(post.videoDurationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (post.allImageFrames.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = "Multiple photos",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun ExploreNoteCard(
    post: Post,
    tallRatio: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("explore_tile_${post.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (tallRatio) 0.78f else 0.94f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PostMediaImage(
                    imageKey = post.imageKey,
                    localImagePath = post.localImagePath,
                    imageData = post.imageData,
                    filterTag = post.filterTag,
                    contentDescription = "Explore note by ${post.authorUsername}",
                    modifier = Modifier.fillMaxSize()
                )
                if (post.isReel || post.isVideoPost) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video clip",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        if (post.videoDurationMs > 0L) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = MediaAssets.formatDuration(post.videoDurationMs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (post.allImageFrames.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Collections,
                            contentDescription = "Multiple photos",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = post.caption.ifBlank { "${post.category} moment" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val firstTag = post.allHashtags.firstOrNull()
                if (!firstTag.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "#$firstTag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        LumaAvatar(
                            username = post.authorUsername,
                            avatarKey = post.authorAvatarKey,
                            avatarData = post.authorAvatarData,
                            size = 20.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = post.authorUsername,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (post.isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = MediaAssets.formatCompactCount(post.likesCount),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

