package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.ContentReport
import com.example.data.model.FollowEdge
import com.example.data.model.ModerationScopes
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.MomiRoles
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.FollowListBottomSheet
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiStartIoSession
import com.example.ui.components.PostMediaImage
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.ThemePreference
import com.example.util.MediaAssets

private data class ProfileHighlight(
    val title: String,
    val imageKey: String
)

private val DefaultHighlights = listOf(
    ProfileHighlight("Amalfi", "img_post_coastal"),
    ProfileHighlight("Espresso", "img_post_coffee"),
    ProfileHighlight("Atrium", "img_post_architecture"),
    ProfileHighlight("Tokyo", "img_post_neon_street")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    allCreators: List<UserProfile>,
    follows: List<FollowEdge> = emptyList(),
    followingUids: Set<String>,
    allPosts: List<Post>,
    wallet: UserWallet? = null,
    themePreference: ThemePreference,
    appColorScheme: AppColorScheme,
    isOwnerAccount: Boolean = false,
    isAdminViewActive: Boolean = false,
    currentRole: String = MomiRoles.USER,
    enrolledScopes: Set<String> = emptySet(),
    contentReports: List<ContentReport> = emptyList(),
    openReportsCount: Int = 0,
    onToggleAdminViewMode: (Boolean) -> Unit = {},
    onAssignTeamMemberRole: (targetUid: String, role: String, scopes: List<String>) -> Unit = { _, _, _ -> },
    onResolveReport: (report: ContentReport, removeContent: Boolean, resolutionNote: String) -> Unit = { _, _, _ -> },
    onThemeChange: (ThemePreference) -> Unit,
    onColorSchemeChange: (AppColorScheme) -> Unit,
    onCreateClick: () -> Unit,
    onOpenWallet: () -> Unit = {},
    onPostClick: (Post) -> Unit,
    onShareProfile: () -> Unit,
    onUpdateProfile: (
        displayName: String,
        username: String,
        category: String,
        bio: String,
        website: String,
        pickedAvatarLocalPath: String?,
        removeAvatar: Boolean
    ) -> Unit,
    onToggleFollowCreator: (UserProfile, Boolean) -> Unit,
    onToggleUserVerified: (UserProfile, Boolean) -> Unit = { _, _ -> },
    onMessageCreator: (UserProfile) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    appVersionName: String = "",
    notificationSoundsEnabled: Boolean = true,
    canPostSystemNotifications: Boolean = true,
    onToggleNotificationSounds: (Boolean) -> Unit = {},
    onTestAlertSound: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCreatorsSheet by remember { mutableStateOf(false) }
    var showAdminVerifySheet by remember { mutableStateOf(false) }
    var showTeamRolesSheet by remember { mutableStateOf(false) }
    var showModerationQueueSheet by remember { mutableStateOf(false) }
    var showPlayReadinessSheet by remember { mutableStateOf(false) }
    var followSheetTab by remember { mutableStateOf<Int?>(null) }

    val canAccessSafetyQueue = isAdminViewActive || (
        currentRole in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR) &&
            enrolledScopes.any { it in ModerationScopes.SURVEILLANCE_SCOPES }
        )

    val ownPosts = remember(allPosts, profile?.userId) {
        val uid = profile?.userId.orEmpty()
        allPosts.filter { it.userId == uid && !it.id.startsWith("showcase_") }
    }
    val ownClips = remember(ownPosts) { ownPosts.filter { it.isReel } }
    val savedPosts = remember(allPosts) { allPosts.filter { it.isBookmarked } }

    val gridPosts = when (selectedTab) {
        0 -> ownPosts
        1 -> ownClips
        else -> savedPosts
    }

    if (showEditDialog && profile != null) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = onUpdateProfile
        )
    }

    if (followSheetTab != null && profile != null) {
        FollowListBottomSheet(
            targetProfile = profile,
            initialTab = followSheetTab ?: 0,
            follows = follows,
            allProfiles = allCreators,
            currentUserId = profile.userId,
            followingUids = followingUids,
            onFollowToggle = onToggleFollowCreator,
            onUserClick = onOpenUserProfile,
            onDismiss = { followSheetTab = null }
        )
    }

    if (showCreatorsSheet) {
        val otherCreators = remember(allCreators, profile?.userId) {
            allCreators.filter { it.userId != profile?.userId }
        }
        ModalBottomSheet(
            onDismissRequest = { showCreatorsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Creators on Momi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Follow or message other signed-in Momi creators in real time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (otherCreators.isEmpty()) {
                    Text(
                        text = "No other signed-in creators yet. Sign in with another Google account on a second phone to see them appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(otherCreators, key = { it.userId }) { creator ->
                            val isFollowing = followingUids.contains(creator.userId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showCreatorsSheet = false
                                        onOpenUserProfile(creator.userId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LumaAvatar(
                                    username = creator.username,
                                    avatarKey = creator.avatarKey,
                                    avatarData = creator.avatarData,
                                    size = 44.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = creator.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            showCreatorsSheet = false
                                            onMessageCreator(creator)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Message",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        .clickable { onToggleFollowCreator(creator, isFollowing) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Appearance & Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose light or dark mode and your Momi color scheme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        Triple(ThemePreference.SYSTEM, "System", Icons.Outlined.PhoneAndroid),
                        Triple(ThemePreference.LIGHT, "Light", Icons.Outlined.LightMode),
                        Triple(ThemePreference.DARK, "Dark", Icons.Outlined.DarkMode)
                    ).forEach { (pref, label, icon) ->
                        val active = themePreference == pref
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (active) 1.5.dp else 0.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onThemeChange(pref) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Color scheme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppColorScheme.entries.chunked(2).forEach { rowSchemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSchemes.forEach { scheme ->
                                val active = appColorScheme == scheme
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = if (active) 1.5.dp else 0.dp,
                                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onColorSchemeChange(scheme) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("color_scheme_${scheme.name}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(scheme.gradientColors))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = scheme.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = scheme.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Alerts & Notification Sounds
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alert Sounds & Chimes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Play sound & vibration for new messages, activity & gifts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = notificationSoundsEnabled,
                                onCheckedChange = onToggleNotificationSounds,
                                modifier = Modifier.testTag("notification_sounds_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTestAlertSound,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("test_alert_sound_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Sound", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            if (!canPostSystemNotifications) {
                                OutlinedButton(
                                    onClick = onRequestNotificationPermission,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("enable_system_alerts_button")
                                ) {
                                    Text("Enable Alerts", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (isOwnerAccount) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAdminViewActive) Icons.Filled.AdminPanelSettings else Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAdminViewActive) "Admin View Active" else "User View Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAdminViewActive) {
                                        "Team roles, verification & full safety tools enabled"
                                    } else {
                                        "Browsing Momi cleanly as a normal community member"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAdminViewActive,
                                onCheckedChange = { onToggleAdminViewMode(it) },
                                modifier = Modifier.testTag("owner_admin_mode_switch")
                            )
                        }
                    }
                }

                if (isAdminViewActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSettingsSheet = false
                                showTeamRolesSheet = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_team_roles_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Team & Roles", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                showSettingsSheet = false
                                showAdminVerifySheet = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_verify_users_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify Users", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showSettingsSheet = false
                            showPlayReadinessSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_play_monetization_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Store & Monetization Status", fontWeight = FontWeight.Bold)
                    }
                }

                if (canAccessSafetyQueue) {
                    OutlinedButton(
                        onClick = {
                            showSettingsSheet = false
                            showModerationQueueSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("safety_queue_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (openReportsCount > 0) {
                                "Safety Surveillance Queue ($openReportsCount open)"
                            } else {
                                "Safety Surveillance Queue"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        showSettingsSheet = false
                        onOpenWallet()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_wallet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Momi Pay & Credits (${wallet?.balance ?: 0L} cr)",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        showSettingsSheet = false
                        onCheckForUpdates()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_updates_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appVersionName.isNotBlank()) {
                            "Check for updates (v$appVersionName)"
                        } else {
                            "Check for updates"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 6.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                OutlinedButton(
                    onClick = {
                        showSettingsSheet = false
                        onSignOut()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign out of @${profile?.username ?: "account"}",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTeamRolesSheet && isAdminViewActive) {
        TeamRolesManagementSheet(
            currentUserId = profile?.userId.orEmpty(),
            allUsers = allCreators,
            onAssignRoleAndScopes = onAssignTeamMemberRole,
            onDismiss = { showTeamRolesSheet = false }
        )
    }

    if (showModerationQueueSheet && canAccessSafetyQueue) {
        ModerationQueueSheet(
            isAdminModeActive = isAdminViewActive,
            currentRole = currentRole,
            enrolledScopes = enrolledScopes,
            reports = contentReports,
            onResolveReport = onResolveReport,
            onDismiss = { showModerationQueueSheet = false }
        )
    }

    if (showPlayReadinessSheet && isOwnerAccount && isAdminViewActive) {
        OwnerMonetizationAndPlaySheet(
            onDismiss = { showPlayReadinessSheet = false }
        )
    }

    if (showAdminVerifySheet && isAdminViewActive) {
        var userFilter by remember { mutableStateOf("") }
        val filteredUsers = remember(allCreators, userFilter) {
            val q = userFilter.trim()
            if (q.isEmpty()) {
                allCreators
            } else {
                allCreators.filter {
                    it.username.contains(q, ignoreCase = true) ||
                        it.displayName.contains(q, ignoreCase = true)
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showAdminVerifySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verified Checkmarks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Only you can grant or remove the verified badge for any user.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = userFilter,
                    onValueChange = { userFilter = it },
                    placeholder = { Text("Search users by name or @username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredUsers, key = { "verify_${it.userId}" }) { creator ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LumaAvatar(
                                username = creator.username,
                                avatarKey = creator.avatarKey,
                                avatarData = creator.avatarData,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = creator.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (creator.isVerified) {
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
                                    text = "@${creator.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = creator.isVerified,
                                onCheckedChange = { next ->
                                    onToggleUserVerified(creator, next)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Profile Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile?.username ?: "momi.creator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (profile?.isVerified == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "Color scheme and theme",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onCreateClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddBox,
                        contentDescription = "Create post",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Settings and theme",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
        ) {
            item(key = "profile_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Avatar + Stats (Tap avatar to edit profile picture)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            LumaAvatar(
                                username = profile?.username ?: "momi",
                                avatarKey = profile?.avatarKey ?: "avatar_aria",
                                avatarData = profile?.avatarData,
                                size = 76.dp,
                                hasActiveStory = true,
                                onClick = { showEditDialog = true }
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showEditDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Change profile photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileStatColumn(
                                count = ownPosts.size.toString(),
                                label = "posts",
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatColumn(
                                count = MediaAssets.formatCompactCount(profile?.followersCount ?: 0L),
                                label = "followers",
                                testTag = "profile_followers_stat",
                                onClick = { followSheetTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatColumn(
                                count = MediaAssets.formatCompactCount(profile?.followingCount ?: 0L),
                                label = "following",
                                testTag = "profile_following_stat",
                                onClick = { followSheetTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Name, Category, Bio, Link
                    Text(
                        text = profile?.displayName ?: "Momi Creator",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!profile?.category.isNullOrBlank()) {
                        Text(
                            text = profile?.category ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile?.bio ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!profile?.website.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile?.website ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showEditDialog = true }
                                .testTag("edit_profile_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Edit profile",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onShareProfile),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Share profile",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showCreatorsSheet = true }
                                .testTag("discover_creators_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = "Discover creators",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Momi Pay, Credits, Gifts & Account Perks Card
                    val creditBalance = wallet?.balance ?: 0L
                    val tierName = MomiCreditCatalog.tierLabel(
                        wallet?.accountTier ?: MomiCreditCatalog.TIER_STANDARD
                    )
                    val verifLabel = if (profile?.isVerified == true) {
                        "Verified"
                    } else {
                        MomiCreditCatalog.verificationLabel(
                            wallet?.verificationTier ?: MomiCreditCatalog.VERIFICATION_NONE
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenWallet)
                            .testTag("profile_wallet_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Toll,
                                    contentDescription = "Momi Credits",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${MediaAssets.formatCompactCount(creditBalance)} Credits",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$tierName · $verifLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Top up, send gifts & unlock perks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Wallet",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    if (isOwnerAccount && isAdminViewActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Owner Admin Console",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { onToggleAdminViewMode(false) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .testTag("switch_to_user_view_button")
                                    ) {
                                        Text(
                                            text = "Switch to User",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { showTeamRolesSheet = true }
                                            .testTag("profile_team_roles_chip"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Team Roles",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { showModerationQueueSheet = true }
                                            .testTag("profile_safety_queue_chip"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (openReportsCount > 0) "Safety ($openReportsCount)" else "Safety Queue",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (openReportsCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { showAdminVerifySheet = true }
                                            .testTag("profile_verify_chip"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Verify",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1.0f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { showPlayReadinessSheet = true }
                                            .testTag("profile_play_ads_chip"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Play & Ads",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isOwnerAccount && !isAdminViewActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .clickable { onToggleAdminViewMode(true) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("switch_to_admin_view_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Using Member View · Switch to Admin",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!isOwnerAccount && currentRole in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR) && enrolledScopes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Momi ${MomiRoles.labelOf(currentRole)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Enrolled: ${enrolledScopes.joinToString(", ") { ModerationScopes.labelOf(it) }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (canAccessSafetyQueue) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { showModerationQueueSheet = true }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .testTag("staff_open_safety_queue_button")
                                    ) {
                                        Text(
                                            text = if (openReportsCount > 0) "Queue ($openReportsCount)" else "Queue",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Story Highlights Row
            item(key = "highlights") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(DefaultHighlights) { highlight ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                            ) {
                                PostMediaImage(
                                    imageKey = highlight.imageKey,
                                    localImagePath = null,
                                    filterTag = "NONE",
                                    contentDescription = highlight.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = highlight.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Tab Strip (Posts / Clips / Saved)
            item(key = "profile_tabs") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple(0, Icons.Filled.GridOn, "Posts"),
                            Triple(1, Icons.Filled.SmartDisplay, "Clips"),
                            Triple(
                                2,
                                if (selectedTab == 2) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                "Saved"
                            )
                        ).forEach { (index, icon, desc) ->
                            val selected = selectedTab == index
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index }
                                    .testTag("profile_tab_$index"),
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
                item(key = "empty_profile_grid") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (selectedTab) {
                                1 -> "No clips shared yet"
                                2 -> "Nothing saved yet"
                                else -> "Share your first photo"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (selectedTab) {
                                2 -> "Tap the bookmark icon on any feed post to save it here."
                                else -> "Tap + to capture or share a visual moment."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val rows = gridPosts.chunked(3)
                items(rows, key = { row -> "grid_row_${row.first().id}" }) { rowItems ->
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
                                    filterTag = post.filterTag,
                                    contentDescription = "Post thumbnail",
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
private fun ProfileStatColumn(
    count: String,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .let { base -> if (testTag != null) base.testTag(testTag) else base }
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerMonetizationAndPlaySheet(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isDebugAds = BuildConfig.DEBUG
    val adDiagnostics by MomiStartIoSession.diagnostics.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monetization & Play Readiness",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Real build & SDK status for your Google Play Store release and Firebase quota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Start.io Live Configuration & Diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        PlayInfoLine("Start.io App ID", BuildConfig.STARTIO_APP_ID)
                        PlayInfoLine(
                            "Ad Serving Mode",
                            if (isDebugAds) "Debug Test Ads ($0 — switch to signed Release build for payouts)" else "Release Paid Live Ads"
                        )
                        PlayInfoLine("SDK Status", adDiagnostics.sdkStatus)
                        PlayInfoLine("Home Native Ad", adDiagnostics.nativeStatus)
                        PlayInfoLine("Home MREC / Fallback", adDiagnostics.mrecStatus)
                        PlayInfoLine("Explore / Market Banner", adDiagnostics.bannerStatus)
                        PlayInfoLine("Publish Interstitial", adDiagnostics.interstitialStatus)
                        PlayInfoLine(
                            "App Identity",
                            "${BuildConfig.APPLICATION_ID} (v${BuildConfig.VERSION_NAME} / code ${BuildConfig.VERSION_CODE})"
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Google Play Policy & Quota Hardening",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        PlayChecklistRow("Zero broad storage permissions (Photo Picker only)")
                        PlayChecklistRow("Play-installed builds never prompt external .apk sideloads")
                        PlayChecklistRow("ProGuard keep rules configured for Release .aab")
                        PlayChecklistRow("100 MB offline Firestore cache + sticky post feed during Spark quota limits")
                    }
                }
            }

            item {
                Text(
                    text = "Open Management Portals",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalLinkButton(
                        title = "Start.io Publisher Portal",
                        subtitle = "Link Play Store listing & view impressions / eCPM",
                        onClick = {
                            runCatching { uriHandler.openUri("https://portal.start.io") }
                        }
                    )
                    PortalLinkButton(
                        title = "Firebase Quota & Blaze Upgrade",
                        subtitle = "Project gen-lang-client-0132255277 daily reads/writes",
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://console.firebase.google.com/project/gen-lang-client-0132255277/usage/details")
                            }
                        }
                    )
                    PortalLinkButton(
                        title = "Google Play Console",
                        subtitle = "Upload signed .aab, complete Data Safety & publish",
                        onClick = {
                            runCatching { uriHandler.openUri("https://play.google.com/console") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayInfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlayChecklistRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PortalLinkButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open external portal",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
