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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.FollowEdge
import com.example.data.model.LiveGiftItem
import com.example.data.model.Post
import com.example.data.model.ShowcaseAccount
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.ui.components.FollowListBottomSheet
import com.example.ui.components.LumaAvatar
import com.example.ui.components.PostMediaImage
import com.example.ui.components.SendGiftBottomSheet
import com.example.util.MediaAssets

@Composable
fun UserProfileScreen(
    targetUserId: String,
    targetUsernameHint: String,
    isShowcaseCreator: Boolean,
    allProfiles: List<UserProfile>,
    allPosts: List<Post>,
    follows: List<FollowEdge>,
    showcaseAccounts: List<ShowcaseAccount> = emptyList(),
    currentUserId: String,
    followingUids: Set<String>,
    wallet: UserWallet? = null,
    isSendingGift: Boolean = false,
    onBack: () -> Unit,
    onPostClick: (Post) -> Unit,
    onFollowToggle: (UserProfile, Boolean) -> Unit,
    onMessageClick: (UserProfile) -> Unit,
    onSendGift: (UserProfile, LiveGiftItem) -> Unit = { _, _ -> },
    onOpenWallet: () -> Unit = {},
    onShareProfile: (UserProfile) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    var followSheetTab by remember { mutableStateOf<Int?>(null) }
    var showGiftSheet by remember { mutableStateOf(false) }

    val userPosts = remember(allPosts, targetUserId, targetUsernameHint, isShowcaseCreator) {
        if (isShowcaseCreator) {
            allPosts.filter {
                (it.id.startsWith("showcase_") || it.userId.startsWith("showcase_")) &&
                    it.authorUsername.equals(targetUsernameHint, ignoreCase = true)
            }
        } else {
            allPosts.filter { it.userId == targetUserId && !it.id.startsWith("showcase_") }
        }
    }
    val userClips = remember(userPosts) { userPosts.filter { it.isReel || it.isVideoPost } }

    val resolvedProfile = remember(allProfiles, showcaseAccounts, userPosts, follows, targetUserId, targetUsernameHint, isShowcaseCreator) {
        if (!isShowcaseCreator) {
            allProfiles.firstOrNull { it.userId == targetUserId }
                ?: run {
                    val samplePost = userPosts.firstOrNull()
                    val fallbackUsername = samplePost?.authorUsername
                        ?: targetUsernameHint.ifBlank { "creator" }
                    UserProfile(
                        id = targetUserId,
                        userId = targetUserId,
                        username = fallbackUsername,
                        displayName = samplePost?.authorDisplayName ?: fallbackUsername,
                        avatarKey = samplePost?.authorAvatarKey ?: MediaAssets.avatarKeyForUid(targetUserId),
                        avatarUrl = samplePost?.authorAvatarUrl,
                        avatarData = samplePost?.authorAvatarData,
                        followersCount = follows.count { it.targetUid == targetUserId }.toLong(),
                        followingCount = follows.count { it.followerUid == targetUserId }.toLong(),
                        isVerified = samplePost?.authorVerified ?: false
                    )
                }
        } else {
            val starter = showcaseAccounts.firstOrNull { it.username.equals(targetUsernameHint, ignoreCase = true) }
            val samplePost = userPosts.firstOrNull()
            val uname = starter?.username ?: targetUsernameHint.ifBlank { "creator" }
            UserProfile(
                id = "showcase_$uname",
                userId = "showcase_$uname",
                username = uname,
                displayName = starter?.displayName ?: samplePost?.authorDisplayName ?: uname,
                category = starter?.category ?: samplePost?.category ?: "Featured Creator",
                bio = starter?.bio?.takeIf { it.isNotBlank() } ?: "Featured Momi creator",
                avatarKey = starter?.avatarKey ?: samplePost?.authorAvatarKey ?: "avatar_elena",
                avatarUrl = starter?.avatarUrl ?: samplePost?.authorAvatarUrl,
                followersCount = 0L,
                followingCount = 0L,
                isVerified = starter?.isVerified ?: samplePost?.authorVerified ?: true
            )
        }
    }

    val isFollowing = !isShowcaseCreator && followingUids.contains(resolvedProfile.userId)
    val isOwnProfile = !isShowcaseCreator && resolvedProfile.userId == currentUserId

    followSheetTab?.let { initialTab ->
        FollowListBottomSheet(
            targetProfile = resolvedProfile,
            initialTab = initialTab,
            follows = follows,
            allProfiles = allProfiles,
            currentUserId = currentUserId,
            followingUids = followingUids,
            onFollowToggle = onFollowToggle,
            onUserClick = onOpenUserProfile,
            onDismiss = { followSheetTab = null }
        )
    }

    if (showGiftSheet && !isOwnProfile && !isShowcaseCreator) {
        SendGiftBottomSheet(
            recipientUsername = resolvedProfile.username,
            isLiveContext = false,
            wallet = wallet,
            isProcessing = isSendingGift,
            onSendGift = { gift ->
                onSendGift(resolvedProfile, gift)
                showGiftSheet = false
            },
            onOpenWallet = {
                showGiftSheet = false
                onOpenWallet()
            },
            onDismiss = { showGiftSheet = false }
        )
    }

    val gridPosts = if (selectedTab == 0) userPosts else userClips

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top App Bar
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
                    .testTag("user_profile_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = resolvedProfile.username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (resolvedProfile.isVerified) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Verified",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onShareProfile(resolvedProfile) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share profile",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "user_profile_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LumaAvatar(
                            username = resolvedProfile.username,
                            avatarKey = resolvedProfile.avatarKey,
                            avatarData = resolvedProfile.avatarData,
                            avatarUrl = resolvedProfile.avatarUrl,
                            size = 86.dp,
                            hasActiveStory = userPosts.isNotEmpty()
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            UserStatColumn(
                                count = userPosts.size.toString(),
                                label = "posts",
                                onClick = { selectedTab = 0 }
                            )
                            UserStatColumn(
                                count = MediaAssets.formatCompactCount(resolvedProfile.followersCount),
                                label = "followers",
                                onClick = if (!isShowcaseCreator) {
                                    { followSheetTab = 0 }
                                } else {
                                    null
                                }
                            )
                            UserStatColumn(
                                count = MediaAssets.formatCompactCount(resolvedProfile.followingCount),
                                label = "following",
                                onClick = if (!isShowcaseCreator) {
                                    { followSheetTab = 1 }
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = resolvedProfile.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (resolvedProfile.category.isNotBlank()) {
                        Text(
                            text = resolvedProfile.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (resolvedProfile.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = resolvedProfile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (resolvedProfile.website.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resolvedProfile.website,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!isOwnProfile && !isShowcaseCreator) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .clickable { onFollowToggle(resolvedProfile, isFollowing) }
                                    .testTag("user_profile_follow_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFollowing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onMessageClick(resolvedProfile) }
                                    .testTag("user_profile_message_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                                    .clickable { showGiftSheet = true }
                                    .padding(horizontal = 14.dp)
                                    .testTag("user_profile_gift_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CardGiftcard,
                                    contentDescription = "Send Gift",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Gift",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
            }

            // Tab Strip (Posts / Clips)
            item(key = "user_profile_tabs") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple(0, Icons.Filled.GridOn, "Posts"),
                            Triple(1, Icons.Filled.SmartDisplay, "Clips")
                        ).forEach { (index, icon, desc) ->
                            val selected = selectedTab == index
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = desc,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(vertical = 11.dp)
                                        .size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (gridPosts.isEmpty()) {
                item(key = "user_profile_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedTab == 1) "No clips yet" else "No posts yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "@${resolvedProfile.username} hasn't shared any ${if (selectedTab == 1) "clips" else "posts"} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val rows = gridPosts.chunked(3)
                items(rows, key = { row -> "user_grid_${row.first().id}" }) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rowItems.forEach { post ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onPostClick(post) }
                            ) {
                                PostMediaImage(
                                    imageKey = post.imageKey,
                                    localImagePath = post.localImagePath,
                                    imageData = post.imageData,
                                    imageUrl = post.imageUrl,
                                    filterTag = post.filterTag,
                                    contentDescription = "Post by ${post.authorUsername}",
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
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserStatColumn(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
