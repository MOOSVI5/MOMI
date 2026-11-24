package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.data.model.MomiGroup
import com.example.data.model.MomiPage
import com.example.data.model.Post
import com.example.ui.components.PostCard
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets

private val CommunityCategories = listOf("Creator", "Photography", "Tech", "Lifestyle", "Travel", "Art", "Music")
private val CoverOptions = listOf(
    "img_post_coastal" to "Coast",
    "img_post_architecture" to "Atrium",
    "img_post_coffee" to "Espresso",
    "img_post_neon_street" to "Neon",
    "img_post_minimal" to "Minimal"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    pages: List<MomiPage>,
    groups: List<MomiGroup>,
    allPosts: List<Post>,
    currentUserId: String,
    isAdminViewActive: Boolean = false,
    onBack: () -> Unit,
    onCreatePage: (name: String, handle: String, category: String, bio: String, coverKey: String, onDone: (Boolean) -> Unit) -> Unit,
    onToggleFollowPage: (MomiPage) -> Unit,
    onCreateGroup: (name: String, category: String, description: String, privacy: String, coverKey: String, onDone: (Boolean) -> Unit) -> Unit,
    onToggleJoinGroup: (MomiGroup) -> Unit,
    onPostToCommunity: (pageId: String?, groupId: String?, communityName: String) -> Unit,
    onPostClick: (Post) -> Unit,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onBookmarkToggle: (Post) -> Unit,
    onFollowAuthorToggle: (Post) -> Unit,
    onAuthorClick: (userId: String, username: String, isShowcase: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var activePage by remember { mutableStateOf<MomiPage?>(null) }
    var activeGroup by remember { mutableStateOf<MomiGroup?>(null) }

    val filteredPages = remember(pages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) pages else pages.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.handle.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true)
        }
    }

    val filteredGroups = remember(groups, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) groups else groups.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    if (showCreateSheet) {
        CreateCommunitySheet(
            initialIsGroup = selectedTab == 1,
            onCreatePage = { n, h, c, b, k, done ->
                onCreatePage(n, h, c, b, k) { ok ->
                    done(ok)
                    if (ok) showCreateSheet = false
                }
            },
            onCreateGroup = { n, c, d, p, k, done ->
                onCreateGroup(n, c, d, p, k) { ok ->
                    done(ok)
                    if (ok) showCreateSheet = false
                }
            },
            onDismiss = { showCreateSheet = false }
        )
    }

    val openPage = activePage?.let { cur -> pages.firstOrNull { it.id == cur.id } ?: cur }
    val openGroup = activeGroup?.let { cur -> groups.firstOrNull { it.id == cur.id } ?: cur }

    if (openPage != null || openGroup != null) {
        val isPage = openPage != null
        val communityTitle = openPage?.name ?: openGroup?.name.orEmpty()
        val communitySubtitle = if (isPage) {
            "@${openPage!!.handle} · ${openPage.category} · ${MediaAssets.formatCompactCount(openPage.followersCount)} followers"
        } else {
            "${openGroup!!.category} · ${openGroup.privacy.lowercase().replaceFirstChar { it.uppercase() }} Group · ${MediaAssets.formatCompactCount(openGroup.membersCount)} members"
        }
        val communityBio = openPage?.bio ?: openGroup?.description.orEmpty()
        val communityCoverKey = openPage?.coverKey ?: openGroup?.coverKey ?: "img_post_coastal"
        val feedPosts = remember(allPosts, openPage?.id, openGroup?.id) {
            allPosts.filter { post ->
                (openPage != null && post.pageId == openPage.id) ||
                    (openGroup != null && post.groupId == openGroup.id)
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                activePage = null
                activeGroup = null
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            PostMediaImage(
                                imageKey = communityCoverKey,
                                localImagePath = null,
                                filterTag = "NONE",
                                contentDescription = communityTitle,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.36f))
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = communityTitle,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (openPage?.isVerified == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "Verified Page",
                                            tint = Color(0xFF7DD3FC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = communitySubtitle,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (communityBio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = communityBio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (openPage != null) {
                                OutlinedButton(
                                    onClick = { onToggleFollowPage(openPage) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("detail_follow_page_button")
                                ) {
                                    Text(
                                        text = if (openPage.isFollowing) "Following Page" else "Follow Page",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (openGroup != null) {
                                OutlinedButton(
                                    onClick = { onToggleJoinGroup(openGroup) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("detail_join_group_button")
                                ) {
                                    Text(
                                        text = if (openGroup.isMember) "Joined Group" else "Join Group",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val pid = openPage?.id
                                    val gid = openGroup?.id
                                    activePage = null
                                    activeGroup = null
                                    onPostToCommunity(pid, gid, communityTitle)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("community_post_here_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Post Here", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (feedPosts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No posts in $communityTitle yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap Post Here to share the first photo, reel, or update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(feedPosts, key = { "comm_post_${it.id}" }) { post ->
                        PostCard(
                            post = post,
                            isOwnPost = post.userId == currentUserId && !post.id.startsWith("showcase_"),
                            isAdminViewActive = isAdminViewActive,
                            onPostClick = { onPostClick(post) },
                            onLikeToggle = { onLikeToggle(post) },
                            onDoubleTapLike = { onDoubleTapLike(post) },
                            onCommentClick = { onCommentClick(post) },
                            onShareClick = { onShareClick(post) },
                            onBookmarkToggle = { onBookmarkToggle(post) },
                            onFollowToggle = { onFollowAuthorToggle(post) },
                            onAuthorClick = {
                                onAuthorClick(post.userId, post.authorUsername, post.id.startsWith("showcase_"))
                            }
                        )
                    }
                }
            }
        }
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("communities_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Pages & Groups",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Creator pages & Momi community clubs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showCreateSheet = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("create_community_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedTab == 0) "New Page" else "New Group",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("tab_momi_pages"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Pages, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pages (${pages.size})", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("tab_momi_groups"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Groups (${groups.size})", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(if (selectedTab == 0) "Search Momi Pages..." else "Search Momi Groups...")
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("communities_search_input")
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedTab == 0) {
                if (filteredPages.isEmpty()) {
                    item {
                        EmptyCommunityCard(
                            title = "Create the first Momi Page",
                            subtitle = "Launch a brand, creator studio, photography journal, or local business Page.",
                            ctaLabel = "Create Page",
                            onClick = { showCreateSheet = true }
                        )
                    }
                } else {
                    items(filteredPages, key = { it.id }) { page ->
                        val pagePostsCount = allPosts.count { it.pageId == page.id }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { activePage = page }
                                .testTag("page_card_${page.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PostMediaImage(
                                        imageKey = page.coverKey,
                                        localImagePath = null,
                                        filterTag = "NONE",
                                        contentDescription = page.name,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = page.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (page.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = "Verified",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "@${page.handle} · ${page.category}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${MediaAssets.formatCompactCount(page.followersCount)} followers · $pagePostsCount posts",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onToggleFollowPage(page) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 42.dp)
                                            .testTag("follow_page_${page.id}")
                                    ) {
                                        Text(
                                            text = if (page.isFollowing) "Following" else "Follow",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (page.bio.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = page.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredGroups.isEmpty()) {
                    item {
                        EmptyCommunityCard(
                            title = "Start a Momi Group",
                            subtitle = "Build a focused community space for travel buddies, film photographers, coffee lovers, or local meetups.",
                            ctaLabel = "Create Group",
                            onClick = { showCreateSheet = true }
                        )
                    }
                } else {
                    items(filteredGroups, key = { it.id }) { group ->
                        val groupPostsCount = allPosts.count { it.groupId == group.id }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { activeGroup = group }
                                .testTag("group_card_${group.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PostMediaImage(
                                        imageKey = group.coverKey,
                                        localImagePath = null,
                                        filterTag = "NONE",
                                        contentDescription = group.name,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (group.privacy == "PRIVATE") Icons.Filled.Lock else Icons.Filled.Public,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${group.category} · ${group.privacy.lowercase().replaceFirstChar { it.uppercase() }}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "${MediaAssets.formatCompactCount(group.membersCount)} members · $groupPostsCount posts",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onToggleJoinGroup(group) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 42.dp)
                                            .testTag("join_group_${group.id}")
                                    ) {
                                        Text(
                                            text = if (group.isMember) "Joined" else "Join",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (group.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = group.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EmptyCommunityCard(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(ctaLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommunitySheet(
    initialIsGroup: Boolean,
    onCreatePage: (name: String, handle: String, category: String, bio: String, coverKey: String, onDone: (Boolean) -> Unit) -> Unit,
    onCreateGroup: (name: String, category: String, description: String, privacy: String, coverKey: String, onDone: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var isGroup by remember { mutableStateOf(initialIsGroup) }
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Creator") }
    var bioOrDesc by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("PUBLIC") }
    var coverKey by remember { mutableStateOf("img_post_coastal") }
    var isSaving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isGroup) "Create Momi Group" else "Create Momi Page",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Page", true to "Group").forEach { (grpFlag, label) ->
                    val active = isGroup == grpFlag
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { isGroup = grpFlag }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (isGroup) "Group Name" else "Page Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("community_name_input")
            )

            if (!isGroup) {
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle (e.g. momi.studio)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PUBLIC", "PRIVATE").forEach { p ->
                        val active = privacy == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (active) 1.5.dp else 0.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { privacy = p }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (p == "PUBLIC") "Public Group" else "Private Group",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CommunityCategories) { cat ->
                    val active = category == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { category = cat }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CoverOptions) { (key, label) ->
                    val active = coverKey == key
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (active) 1.5.dp else 0.dp,
                                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { coverKey = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PostMediaImage(
                            imageKey = key,
                            localImagePath = null,
                            filterTag = "NONE",
                            contentDescription = label,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            OutlinedTextField(
                value = bioOrDesc,
                onValueChange = { bioOrDesc = it },
                label = { Text(if (isGroup) "Description" else "About this Page") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = !isSaving && name.isNotBlank(),
                onClick = {
                    isSaving = true
                    if (isGroup) {
                        onCreateGroup(name, category, bioOrDesc, privacy, coverKey) { isSaving = false }
                    } else {
                        onCreatePage(name, handle, category, bioOrDesc, coverKey) { isSaving = false }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("submit_create_community_button")
            ) {
                Text(
                    text = if (isSaving) "Creating..." else if (isGroup) "Create Group" else "Create Page",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
