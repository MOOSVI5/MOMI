package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.components.LumaAvatar
import com.example.ui.components.PostCard
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets

@Composable
fun PostDetailScreen(
    post: Post?,
    currentUserId: String,
    relatedPosts: List<Post> = emptyList(),
    onBack: () -> Unit,
    onPostClick: (Post) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onBookmarkToggle: (Post) -> Unit,
    onFollowToggle: (Post) -> Unit,
    onDeletePost: (Post) -> Unit,
    onReportPost: (Post) -> Unit = {},
    onPostViewed: (Post) -> Unit = {},
    onAuthorClick: (Post) -> Unit = {},
    isAdminViewActive: Boolean = false,
    onBoostPost: (Post) -> Unit = {},
    onToggleRecommendedPost: (Post, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(post?.id) {
        post?.let(onPostViewed)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("post_detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (post != null) "@${post.authorUsername}" else "Post",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (post != null && post.category.isNotBlank()) {
                    Text(
                        text = post.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (post != null) {
                IconButton(
                    onClick = { onShareClick(post) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        if (post != null) {
            val relatedRows = relatedPosts.filter { it.id != post.id }.take(12).chunked(2)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "detail_post_${post.id}") {
                    PostCard(
                        post = post,
                        isOwnPost = post.userId == currentUserId && !post.id.startsWith("showcase_"),
                        isAdminViewActive = isAdminViewActive,
                        isPlaybackActive = true,
                        isDetailMode = true,
                        onPostClick = {},
                        onHashtagClick = onHashtagClick,
                        onMentionClick = onMentionClick,
                        onLikeToggle = { onLikeToggle(post) },
                        onDoubleTapLike = { onDoubleTapLike(post) },
                        onCommentClick = { onCommentClick(post) },
                        onShareClick = { onShareClick(post) },
                        onBookmarkToggle = { onBookmarkToggle(post) },
                        onFollowToggle = { onFollowToggle(post) },
                        onBoostPost = { onBoostPost(post) },
                        onToggleRecommended = { recommended -> onToggleRecommendedPost(post, recommended) },
                        onAuthorClick = { onAuthorClick(post) },
                        onReportPost = { onReportPost(post) },
                        onDeletePost = {
                            onDeletePost(post)
                            onBack()
                        }
                    )
                }

                if (relatedRows.isNotEmpty()) {
                    item(key = "more_to_explore_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "More to Explore",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Discover notes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(relatedRows, key = { row -> "rel_row_${row.first().id}" }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { rel ->
                                RelatedExploreNoteCard(
                                    post = rel,
                                    onClick = { onPostClick(rel) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedExploreNoteCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("related_post_card_${post.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PostMediaImage(
                    imageKey = post.imageKey,
                    localImagePath = post.localImagePath,
                    imageData = post.imageData,
                    imageUrl = post.imageUrl,
                    filterTag = post.filterTag,
                    contentDescription = "Related post by ${post.authorUsername}",
                    modifier = Modifier.fillMaxSize()
                )
                if (post.isReel || post.isVideoPost) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
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
                            contentDescription = "Album",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
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
                            avatarUrl = post.authorAvatarUrl,
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
