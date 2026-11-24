package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.FollowEdge
import com.example.data.model.UserProfile
import com.example.util.MediaAssets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListBottomSheet(
    targetProfile: UserProfile,
    initialTab: Int,
    follows: List<FollowEdge>,
    allProfiles: List<UserProfile>,
    currentUserId: String,
    followingUids: Set<String>,
    onFollowToggle: (UserProfile, Boolean) -> Unit,
    onUserClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(targetProfile.userId, initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var searchQuery by remember { mutableStateOf("") }

    val profilesByUid = remember(allProfiles) { allProfiles.associateBy { it.userId } }

    val followersList = remember(follows, profilesByUid, targetProfile.userId) {
        follows.filter { it.targetUid == targetProfile.userId }
            .distinctBy { it.followerUid }
            .map { edge ->
                profilesByUid[edge.followerUid] ?: UserProfile(
                    id = edge.followerUid,
                    userId = edge.followerUid,
                    username = edge.followerUsername.ifBlank { "creator" },
                    displayName = edge.followerUsername.ifBlank { "Momi Creator" },
                    avatarKey = MediaAssets.avatarKeyForUid(edge.followerUid)
                )
            }
    }

    val followingList = remember(follows, profilesByUid, targetProfile.userId) {
        follows.filter { it.followerUid == targetProfile.userId }
            .distinctBy { it.targetUid }
            .map { edge ->
                profilesByUid[edge.targetUid] ?: UserProfile(
                    id = edge.targetUid,
                    userId = edge.targetUid,
                    username = edge.targetUsername.ifBlank { "creator" },
                    displayName = edge.targetUsername.ifBlank { "Momi Creator" },
                    avatarKey = MediaAssets.avatarKeyForUid(edge.targetUid)
                )
            }
    }

    val activeSourceList = if (selectedTab == 0) followersList else followingList
    val filteredList = remember(activeSourceList, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            activeSourceList
        } else {
            activeSourceList.filter {
                it.username.contains(q, ignoreCase = true) ||
                    it.displayName.contains(q, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = "@${targetProfile.username}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
            )

            // Followers / Following Tabs
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    0 to "${followersList.size} Followers",
                    1 to "${followingList.size} Following"
                ).forEach { (tabIdx, title) ->
                    val selected = selectedTab == tabIdx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = tabIdx }
                            .testTag("follow_list_tab_$tabIdx"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (activeSourceList.size > 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search people...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (filteredList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (selectedTab == 0) "No followers yet" else "Not following anyone yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedTab == 0) {
                            "People who follow @${targetProfile.username} will show up here."
                        } else {
                            "Accounts @${targetProfile.username} follows will show up here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredList, key = { "${selectedTab}_${it.userId}" }) { user ->
                        val isFollowing = followingUids.contains(user.userId)
                        val isSelf = user.userId == currentUserId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onUserClick(user.userId)
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LumaAvatar(
                                username = user.username,
                                avatarKey = user.avatarKey,
                                avatarData = user.avatarData,
                                avatarUrl = user.avatarUrl,
                                size = 44.dp,
                                hasActiveStory = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (user.isVerified) {
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
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isSelf) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        .clickable { onFollowToggle(user, isFollowing) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                }
            }
        }
    }
}
