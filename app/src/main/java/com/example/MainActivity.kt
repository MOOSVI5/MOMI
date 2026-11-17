package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOut
import com.example.data.model.Post
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.MomiFirestoreRepository
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.FloatingMomiAssistantButton
import com.example.ui.components.LocalRemoteVideoResolver
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiAiAssistantBottomSheet
import com.example.ui.components.MomiInterstitialController
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.GoLiveScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveViewerScreen
import com.example.ui.screens.MarketplaceScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PostDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.ReportContentSheet
import com.example.ui.screens.ReportDraftTarget
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.LumaTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.MomiViewModel
import com.example.util.AppUpdateChecker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import kotlinx.coroutines.launch

private object LumaRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val CREATE = "create?story={story}"
    const val CLIPS = "clips"
    const val PROFILE = "profile"
    const val MARKETPLACE = "marketplace"
    const val USER_PROFILE = "user/{userId}?username={username}&showcase={showcase}"
    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages?threadId={threadId}"
    const val STORY_VIEWER = "story/{storyId}"
    const val POST_DETAIL = "post/{postId}"
    const val GO_LIVE = "go_live"
    const val LIVE_VIEWER = "live/{streamId}"

    fun createRoute(isStory: Boolean = false) = "create?story=$isStory"
    fun userProfileRoute(userId: String, username: String = "", isShowcase: Boolean = false): String {
        val safeUid = Uri.encode(userId.ifBlank { "creator" })
        val safeUsername = Uri.encode(username)
        return "user/$safeUid?username=$safeUsername&showcase=$isShowcase"
    }
    fun messagesRoute(threadId: String? = null) =
        if (threadId.isNullOrBlank()) "messages" else "messages?threadId=$threadId"
    fun storyRoute(storyId: String) = "story/$storyId"
    fun postDetailRoute(postId: String) = "post/$postId"
    fun liveViewerRoute(streamId: String) = "live/${Uri.encode(streamId)}"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val PrimaryTabs = listOf(
    BottomNavTab(LumaRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    BottomNavTab(LumaRoutes.EXPLORE, "Search", Icons.Filled.Search, Icons.Outlined.Search, "tab_explore"),
    BottomNavTab("create?story=false", "Create", Icons.Filled.AddBox, Icons.Outlined.AddBox, "tab_create"),
    BottomNavTab(LumaRoutes.CLIPS, "Clips", Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay, "tab_clips"),
    BottomNavTab(LumaRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person, "tab_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)

        runCatching {
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
            StartAppSDK.init(this, BuildConfig.STARTIO_APP_ID, false)
            StartAppAd.disableSplash()
        }

        setContent {
            var themePreference by remember { mutableStateOf(AppearancePrefs.loadThemePreference(this)) }
            var appColorScheme by remember { mutableStateOf(AppearancePrefs.loadColorScheme(this)) }

            LumaTheme(
                themePreference = themePreference,
                appColorScheme = appColorScheme
            ) {
                AppNavigation(
                    onThemePreferenceChange = { themePreference = it },
                    onColorSchemeChange = { appColorScheme = it }
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onColorSchemeChange: (AppColorScheme) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )

    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                // AuthStateListener updates currentUser reactively and switches to LumaApp.
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: MomiViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id)).also { firestore ->
                            configureFirestoreCacheOnce(firestore)
                        }
                        MomiViewModel(
                            repository = MomiFirestoreRepository(db),
                            marketplaceRepository = MarketplaceRepository(db),
                            currentUserId = uid,
                            initialDisplayName = user.displayName,
                            initialEmail = user.email
                        )
                    }
                }
            )
            LaunchedEffect(viewModel) {
                viewModel.loadAppearance(context)
                MomiInterstitialController.preload(context)
                viewModel.checkForUpdates(
                    manual = false,
                    isPlayStoreInstall = AppUpdateChecker.isInstalledFromGooglePlay(context)
                )
            }
            val vmThemePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val vmColorScheme by viewModel.colorScheme.collectAsStateWithLifecycle()
            LaunchedEffect(vmThemePreference) {
                onThemePreferenceChange(vmThemePreference)
            }
            LaunchedEffect(vmColorScheme) {
                onColorSchemeChange(vmColorScheme)
            }

            LumaApp(
                viewModel = viewModel,
                onSignOut = {
                    signOut(
                        context = context,
                        credentialManager = credentialManager,
                        scope = coroutineScope
                    )
                }
            )
        }
    }
}

@Composable
private fun LumaApp(
    viewModel: MomiViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val allCreators by viewModel.allProfiles.collectAsStateWithLifecycle()
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val followingUids by viewModel.followingUids.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val explorePosts by viewModel.explorePosts.collectAsStateWithLifecycle()
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val activeLiveStreams by viewModel.activeLiveStreams.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val messageThreads by viewModel.messageThreads.collectAsStateWithLifecycle()
    val unreadMessages by viewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val appColorScheme by viewModel.colorScheme.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedExploreCategory.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusNotice by viewModel.statusNotice.collectAsStateWithLifecycle()
    val isSendingChatMedia by viewModel.isSendingChatMedia.collectAsStateWithLifecycle()
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()
    val marketProducts by viewModel.marketProducts.collectAsStateWithLifecycle()
    val marketOrders by viewModel.marketOrders.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val isAdminViewActive by viewModel.isAdminModeActive.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val currentModerationScopes by viewModel.currentModerationScopes.collectAsStateWithLifecycle()
    val canCurateMarketplace by viewModel.canCurateMarketplace.collectAsStateWithLifecycle()
    val contentReports by viewModel.contentReports.collectAsStateWithLifecycle()
    val openReportsCount by viewModel.openReportsCount.collectAsStateWithLifecycle()
    var showAiAssistantSheet by remember { mutableStateOf(false) }
    var pendingReportTarget by remember { mutableStateOf<ReportDraftTarget?>(null) }

    val activeCommentPostId by viewModel.activeCommentPostId.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(statusNotice) {
        val message = statusNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusNotice()
    }

    val showBottomBar = currentRoute in listOf(
        LumaRoutes.HOME,
        LumaRoutes.EXPLORE,
        LumaRoutes.CLIPS,
        LumaRoutes.PROFILE
    )

    val showAssistantFab = currentRoute in listOf(
        LumaRoutes.HOME,
        LumaRoutes.EXPLORE,
        LumaRoutes.PROFILE,
        LumaRoutes.MARKETPLACE
    )

    fun sharePostExternally(post: Post) {
        shareText(
            context = context,
            subject = "Momi post by @${post.authorUsername}",
            text = "${post.authorDisplayName} (@${post.authorUsername}) on Momi:\n\"${post.caption}\""
        )
    }

    fun navigatePrimaryTab(route: String) {
        if (route.startsWith("create")) {
            navController.navigate(LumaRoutes.createRoute(false))
        } else {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun openUserProfile(userId: String, username: String = "", isShowcase: Boolean = false) {
        if (!isShowcase && userId == viewModel.currentUserId) {
            navigatePrimaryTab(LumaRoutes.PROFILE)
        } else if (userId.isNotBlank() || username.isNotBlank()) {
            navController.navigate(LumaRoutes.userProfileRoute(userId, username, isShowcase))
        }
    }

    val remoteVideoResolver: suspend (String, String, Long) -> Uri? = remember(viewModel, context) {
        { collectionName, mediaId, expectedChunksCount ->
            viewModel.resolveRemoteVideoUri(context, collectionName, mediaId, expectedChunksCount)
        }
    }

    CompositionLocalProvider(LocalRemoteVideoResolver provides remoteVideoResolver) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        PrimaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigatePrimaryTab(tab.route) },
                                icon = {
                                    if (tab.route == LumaRoutes.PROFILE) {
                                        LumaAvatar(
                                            username = profile?.username ?: "momi",
                                            avatarKey = profile?.avatarKey ?: "avatar_aria",
                                            avatarData = profile?.avatarData,
                                            size = 26.dp,
                                            hasActiveStory = selected
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                if (showBottomBar && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        PrimaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigatePrimaryTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        NavigationRailItem(
                            selected = currentRoute == LumaRoutes.MARKETPLACE,
                            onClick = { navController.navigate(LumaRoutes.MARKETPLACE) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == LumaRoutes.MARKETPLACE) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                                    contentDescription = "Market"
                                )
                            },
                            label = { Text("Market") },
                            modifier = Modifier.testTag("rail_marketplace")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = LumaRoutes.HOME
                    ) {
                        composable(LumaRoutes.HOME) {
                            HomeScreen(
                                profile = profile,
                                creators = allCreators,
                                followingUids = followingUids,
                                posts = allPosts,
                                stories = stories,
                                liveStreams = activeLiveStreams,
                                unreadNotificationCount = unreadNotifications,
                                unreadMessageCount = unreadMessages,
                                onOpenSearch = { navigatePrimaryTab(LumaRoutes.EXPLORE) },
                                onOpenMarketplace = { navController.navigate(LumaRoutes.MARKETPLACE) },
                                onOpenNotifications = { navController.navigate(LumaRoutes.NOTIFICATIONS) },
                                onOpenMessages = { navController.navigate(LumaRoutes.messagesRoute()) },
                                onOpenStory = { story -> navController.navigate(LumaRoutes.storyRoute(story.id)) },
                                onAddStoryClick = { navController.navigate(LumaRoutes.createRoute(true)) },
                                onGoLiveClick = { navController.navigate(LumaRoutes.GO_LIVE) },
                                onOpenLiveStream = { stream ->
                                    if (stream.userId == viewModel.currentUserId) {
                                        navController.navigate(LumaRoutes.GO_LIVE)
                                    } else {
                                        navController.navigate(LumaRoutes.liveViewerRoute(stream.id))
                                    }
                                },
                                onPostViewed = { viewModel.recordPostView(it.id) },
                                onLikeToggle = { viewModel.toggleLike(it) },
                                onDoubleTapLike = { viewModel.doubleTapLike(it) },
                                onCommentClick = { viewModel.openComments(it.id) },
                                onShareClick = { sharePostExternally(it) },
                                onBookmarkToggle = { post ->
                                    val willSave = !post.isBookmarked
                                    viewModel.toggleBookmark(post)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (willSave) "Saved to your profile" else "Removed from Saved"
                                        )
                                    }
                                },
                                onFollowToggle = {
                                    viewModel.toggleFollow(
                                        targetUid = it.userId,
                                        targetUsername = it.authorUsername,
                                        currentlyFollowing = it.isFollowingAuthor
                                    )
                                },
                                onFollowCreator = { creator, isFollowing ->
                                    viewModel.toggleFollow(
                                        targetUid = creator.userId,
                                        targetUsername = creator.username,
                                        currentlyFollowing = isFollowing
                                    )
                                },
                                onDeletePost = { viewModel.deletePost(it.id) },
                                onReportPost = { post ->
                                    pendingReportTarget = ReportDraftTarget(
                                        targetType = "POST",
                                        targetId = post.id,
                                        targetAuthorUid = post.userId,
                                        targetAuthorUsername = post.authorUsername,
                                        previewText = post.caption.ifBlank { "Shared post by @${post.authorUsername}" }
                                    )
                                },
                                onAuthorClick = { userId, username, isShowcase ->
                                    openUserProfile(userId, username, isShowcase)
                                }
                            )
                        }

                        composable(LumaRoutes.EXPLORE) {
                            ExploreScreen(
                                posts = explorePosts,
                                creators = allCreators,
                                currentUserId = viewModel.currentUserId,
                                followingUids = followingUids,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onCategorySelected = viewModel::setExploreCategory,
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onCreatorClick = { creator ->
                                    openUserProfile(creator.userId, creator.username, false)
                                },
                                onFollowCreator = { creator, isFollowing ->
                                    viewModel.toggleFollow(
                                        targetUid = creator.userId,
                                        targetUsername = creator.username,
                                        currentlyFollowing = isFollowing
                                    )
                                },
                                onMessageCreator = { creator ->
                                    viewModel.startDirectChat(creator) { thread ->
                                        navController.navigate(LumaRoutes.messagesRoute(thread.id))
                                    }
                                }
                            )
                        }

                        composable(
                            route = LumaRoutes.CREATE,
                            arguments = listOf(
                                navArgument("story") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val startAsStory = backStackEntry.arguments?.getBoolean("story") ?: false
                            CreatePostScreen(
                                initialModeStory = startAsStory,
                                onClose = { navController.popBackStack() },
                                onPublishPost = { imageKey, localPath, extraImagePaths, mediaType, localVideoPath, videoDurationMs, audioTitle, localAudioPath, filter, caption, location, category, isReel, onFinished ->
                                    viewModel.createPost(
                                        context = context,
                                        imageKey = imageKey,
                                        localImagePath = localPath,
                                        localExtraImagePaths = extraImagePaths,
                                        mediaType = mediaType,
                                        localVideoPath = localVideoPath,
                                        videoDurationMs = videoDurationMs,
                                        audioTitle = audioTitle,
                                        localAudioPath = localAudioPath,
                                        filterTag = filter,
                                        caption = caption,
                                        location = location,
                                        category = category,
                                        isReel = isReel
                                    ) { succeeded ->
                                        onFinished(succeeded)
                                        if (succeeded) {
                                            navController.popBackStack()
                                            navigatePrimaryTab(LumaRoutes.HOME)
                                            MomiInterstitialController.maybeShowOnMilestone(context)
                                        }
                                    }
                                },
                                onPublishStory = { imageKey, localPath, mediaType, localVideoPath, videoDurationMs, audioTitle, localAudioPath, caption, onFinished ->
                                    viewModel.createStory(
                                        context = context,
                                        imageKey = imageKey,
                                        localImagePath = localPath,
                                        mediaType = mediaType,
                                        localVideoPath = localVideoPath,
                                        videoDurationMs = videoDurationMs,
                                        audioTitle = audioTitle,
                                        localAudioPath = localAudioPath,
                                        caption = caption
                                    ) { succeeded ->
                                        onFinished(succeeded)
                                        if (succeeded) {
                                            navController.popBackStack()
                                            navigatePrimaryTab(LumaRoutes.HOME)
                                            MomiInterstitialController.maybeShowOnMilestone(context)
                                        }
                                    }
                                },
                                onGoLiveClick = {
                                    navController.popBackStack()
                                    navController.navigate(LumaRoutes.GO_LIVE)
                                }
                            )
                        }

                        composable(LumaRoutes.CLIPS) {
                            ReelsScreen(
                                reels = reels,
                                currentUserId = viewModel.currentUserId,
                                onLikeToggle = { viewModel.toggleLike(it) },
                                onDoubleTapLike = { viewModel.doubleTapLike(it) },
                                onCommentClick = { viewModel.openComments(it.id) },
                                onShareClick = { sharePostExternally(it) },
                                onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                onFollowToggle = {
                                    viewModel.toggleFollow(
                                        targetUid = it.userId,
                                        targetUsername = it.authorUsername,
                                        currentlyFollowing = it.isFollowingAuthor
                                    )
                                },
                                onReelViewed = { viewModel.recordPostView(it.id) },
                                onReportReel = { reel ->
                                    pendingReportTarget = ReportDraftTarget(
                                        targetType = "POST",
                                        targetId = reel.id,
                                        targetAuthorUid = reel.userId,
                                        targetAuthorUsername = reel.authorUsername,
                                        previewText = reel.caption.ifBlank { "Clip by @${reel.authorUsername}" }
                                    )
                                },
                                onAuthorClick = { reel ->
                                    openUserProfile(
                                        reel.userId,
                                        reel.authorUsername,
                                        reel.id.startsWith("showcase_")
                                    )
                                }
                            )
                        }

                        composable(LumaRoutes.PROFILE) {
                            ProfileScreen(
                                profile = profile,
                                allCreators = allCreators,
                                follows = follows,
                                followingUids = followingUids,
                                allPosts = allPosts,
                                themePreference = themePreference,
                                appColorScheme = appColorScheme,
                                isOwnerAccount = viewModel.isOwnerAccount,
                                isAdminViewActive = isAdminViewActive,
                                currentRole = currentUserRole,
                                enrolledScopes = currentModerationScopes,
                                contentReports = contentReports,
                                openReportsCount = openReportsCount,
                                onToggleAdminViewMode = { enabled -> viewModel.setAdminViewEnabled(context, enabled) },
                                onAssignTeamMemberRole = { targetUid, role, scopes ->
                                    viewModel.assignTeamMemberRole(targetUid, role, scopes)
                                },
                                onResolveReport = { report, removeContent, note ->
                                    viewModel.resolveContentReport(report, removeContent, note)
                                },
                                onThemeChange = { viewModel.setThemePreference(context, it) },
                                onColorSchemeChange = { viewModel.setColorScheme(context, it) },
                                onCreateClick = { navController.navigate(LumaRoutes.createRoute(false)) },
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onShareProfile = {
                                    val handle = profile?.username ?: "momi.creator"
                                    shareText(
                                        context = context,
                                        subject = "Follow @$handle on Momi",
                                        text = "Check out @$handle on Momi: https://momi.photo/$handle"
                                    )
                                },
                                onUpdateProfile = viewModel::updateProfile,
                                onToggleFollowCreator = { creator, isFollowing ->
                                    viewModel.toggleFollow(
                                        targetUid = creator.userId,
                                        targetUsername = creator.username,
                                        currentlyFollowing = isFollowing
                                    )
                                },
                                onToggleUserVerified = { creator, verified ->
                                    viewModel.setUserVerified(creator.userId, verified)
                                },
                                onMessageCreator = { creator ->
                                    viewModel.startDirectChat(creator) { thread ->
                                        navController.navigate(LumaRoutes.messagesRoute(thread.id))
                                    }
                                },
                                onOpenUserProfile = { targetUid ->
                                    openUserProfile(targetUid)
                                },
                                appVersionName = BuildConfig.VERSION_NAME,
                                onCheckForUpdates = {
                                    viewModel.checkForUpdates(
                                        manual = true,
                                        isPlayStoreInstall = AppUpdateChecker.isInstalledFromGooglePlay(context)
                                    )
                                },
                                onSignOut = onSignOut
                            )
                        }

                        composable(LumaRoutes.MARKETPLACE) {
                            MarketplaceScreen(
                                profile = profile,
                                currentUserId = viewModel.currentUserId,
                                isAdminUser = isAdminViewActive,
                                canCurateEditorial = canCurateMarketplace,
                                merchants = merchants,
                                products = marketProducts,
                                orders = marketOrders,
                                onBack = { navController.popBackStack() },
                                onCreateOrUpdateMerchant = { storeName, tagline, category, location, onDone ->
                                    viewModel.createOrUpdateMerchant(
                                        storeName = storeName,
                                        tagline = tagline,
                                        category = category,
                                        location = location,
                                        onComplete = onDone
                                    )
                                },
                                onSetMerchantVerified = { merchantUid, verified ->
                                    viewModel.setMerchantVerified(merchantUid, verified)
                                },
                                onPublishProduct = { merchant, title, description, category, priceUsd, originalPriceUsd, freeShipping, imagePaths, localVideoPath, videoDurationMs, stockCount, onDone ->
                                    viewModel.publishMarketProduct(
                                        context = context,
                                        merchant = merchant,
                                        title = title,
                                        description = description,
                                        category = category,
                                        priceUsd = priceUsd,
                                        originalPriceUsd = originalPriceUsd,
                                        freeShipping = freeShipping,
                                        imagePaths = imagePaths,
                                        localVideoPath = localVideoPath,
                                        videoDurationMs = videoDurationMs,
                                        stockCount = stockCount,
                                        onComplete = onDone
                                    )
                                },
                                onDeleteProduct = { productId -> viewModel.deleteMarketProduct(productId) },
                                onPlaceOrder = { product, address, onDone ->
                                    viewModel.placeMarketOrder(product, address, onDone)
                                },
                                onUpdateOrderStatus = { orderId, status ->
                                    viewModel.updateMarketOrderStatus(orderId, status)
                                },
                                onMessageSeller = { sellerUid ->
                                    viewModel.startDirectChatWithUid(sellerUid) { thread ->
                                        navController.navigate(LumaRoutes.messagesRoute(thread.id))
                                    }
                                },
                                onToggleProductFeatured = { productId, featured ->
                                    viewModel.setProductFeatured(productId, featured)
                                },
                                onReportProduct = { product ->
                                    pendingReportTarget = ReportDraftTarget(
                                        targetType = "PRODUCT",
                                        targetId = product.id,
                                        targetAuthorUid = product.sellerUid,
                                        targetAuthorUsername = product.sellerUsername,
                                        previewText = product.title
                                    )
                                },
                                modifier = Modifier.navigationBarsPadding()
                            )
                        }

                        composable(
                            route = LumaRoutes.USER_PROFILE,
                            arguments = listOf(
                                navArgument("userId") { type = NavType.StringType },
                                navArgument("username") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("showcase") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val targetUserId = Uri.decode(backStackEntry.arguments?.getString("userId").orEmpty())
                            val targetUsername = Uri.decode(backStackEntry.arguments?.getString("username").orEmpty())
                            val isShowcase = backStackEntry.arguments?.getBoolean("showcase") ?: false

                            UserProfileScreen(
                                targetUserId = targetUserId,
                                targetUsernameHint = targetUsername,
                                isShowcaseCreator = isShowcase,
                                allProfiles = allCreators,
                                allPosts = allPosts,
                                follows = follows,
                                currentUserId = viewModel.currentUserId,
                                followingUids = followingUids,
                                onBack = { navController.popBackStack() },
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onFollowToggle = { creator, isFollowing ->
                                    viewModel.toggleFollow(
                                        targetUid = creator.userId,
                                        targetUsername = creator.username,
                                        currentlyFollowing = isFollowing
                                    )
                                },
                                onMessageClick = { creator ->
                                    viewModel.startDirectChat(creator) { thread ->
                                        navController.navigate(LumaRoutes.messagesRoute(thread.id))
                                    }
                                },
                                onShareProfile = { targetProfile ->
                                    shareText(
                                        context = context,
                                        subject = "Follow @${targetProfile.username} on Momi",
                                        text = "Check out @${targetProfile.username} on Momi: https://momi.photo/${targetProfile.username}"
                                    )
                                },
                                onOpenUserProfile = { nextUid ->
                                    openUserProfile(nextUid)
                                }
                            )
                        }

                        composable(LumaRoutes.NOTIFICATIONS) {
                            NotificationsScreen(
                                notifications = notifications,
                                allCreators = allCreators,
                                onToggleFollowBack = viewModel::toggleNotificationFollow,
                                onMarkAllRead = viewModel::markNotificationsRead,
                                onUserClick = { userId, username ->
                                    openUserProfile(userId, username, false)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = LumaRoutes.MESSAGES,
                            arguments = listOf(
                                navArgument("threadId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val initialThreadId = backStackEntry.arguments?.getString("threadId")
                            MessagesScreen(
                                currentUserId = viewModel.currentUserId,
                                profile = profile,
                                allCreators = allCreators,
                                threads = messageThreads,
                                initialThreadId = initialThreadId,
                                isSendingMedia = isSendingChatMedia,
                                observeChatMessages = viewModel::observeChatMessages,
                                onStartChat = viewModel::startDirectChat,
                                onClearUnread = viewModel::clearThreadUnread,
                                onSendMessage = viewModel::sendMessage,
                                onSendPhoto = { thread, path -> viewModel.sendPhotoMessage(thread, path) },
                                onSendVideo = { thread, path, dur -> viewModel.sendVideoMessage(context, thread, path, dur) },
                                onSendAudio = { thread, path, dur -> viewModel.sendVoiceMessage(context, thread, path, dur) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = LumaRoutes.STORY_VIEWER,
                            arguments = listOf(navArgument("storyId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val storyId = backStackEntry.arguments?.getString("storyId").orEmpty()
                            StoryViewerScreen(
                                stories = stories,
                                initialStoryId = storyId,
                                onMarkSeen = viewModel::markStorySeen,
                                onAuthorClick = { userId, username ->
                                    openUserProfile(userId, username, false)
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = LumaRoutes.POST_DETAIL,
                            arguments = listOf(navArgument("postId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId").orEmpty()
                            val post by remember(postId) {
                                viewModel.observePost(postId)
                            }.collectAsStateWithLifecycle(initialValue = allPosts.firstOrNull { it.id == postId })

                            PostDetailScreen(
                                post = post,
                                currentUserId = viewModel.currentUserId,
                                onBack = { navController.popBackStack() },
                                onLikeToggle = { viewModel.toggleLike(it) },
                                onDoubleTapLike = { viewModel.doubleTapLike(it) },
                                onCommentClick = { viewModel.openComments(it.id) },
                                onShareClick = { sharePostExternally(it) },
                                onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                onFollowToggle = {
                                    viewModel.toggleFollow(
                                        targetUid = it.userId,
                                        targetUsername = it.authorUsername,
                                        currentlyFollowing = it.isFollowingAuthor
                                    )
                                },
                                onAuthorClick = { clickedPost ->
                                    openUserProfile(
                                        clickedPost.userId,
                                        clickedPost.authorUsername,
                                        clickedPost.id.startsWith("showcase_")
                                    )
                                },
                                onDeletePost = { viewModel.deletePost(it.id) },
                                onReportPost = { reportedPost ->
                                    pendingReportTarget = ReportDraftTarget(
                                        targetType = "POST",
                                        targetId = reportedPost.id,
                                        targetAuthorUid = reportedPost.userId,
                                        targetAuthorUsername = reportedPost.authorUsername,
                                        previewText = reportedPost.caption.ifBlank { "Shared post by @${reportedPost.authorUsername}" }
                                    )
                                },
                                onPostViewed = { viewModel.recordPostView(it.id) }
                            )
                        }

                        composable(LumaRoutes.GO_LIVE) {
                            val streamId = viewModel.currentUserId
                            val activeStream by remember(streamId) {
                                viewModel.observeLiveStream(streamId)
                            }.collectAsStateWithLifecycle(initialValue = null)
                            val liveComments by remember(streamId) {
                                viewModel.observeLiveComments(streamId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            GoLiveScreen(
                                profile = profile,
                                activeStream = activeStream,
                                comments = liveComments,
                                onStartLive = { title, onDone ->
                                    viewModel.startLiveStream(title) { result ->
                                        onDone(result.isSuccess)
                                    }
                                },
                                onEndLive = {
                                    viewModel.endLiveStream(streamId)
                                },
                                onSendComment = { text ->
                                    viewModel.sendLiveComment(streamId, text)
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = LumaRoutes.LIVE_VIEWER,
                            arguments = listOf(navArgument("streamId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val streamId = Uri.decode(backStackEntry.arguments?.getString("streamId").orEmpty())
                            val liveStream by remember(streamId) {
                                viewModel.observeLiveStream(streamId)
                            }.collectAsStateWithLifecycle(
                                initialValue = activeLiveStreams.firstOrNull { it.id == streamId }
                            )
                            val liveComments by remember(streamId) {
                                viewModel.observeLiveComments(streamId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            LiveViewerScreen(
                                stream = liveStream,
                                comments = liveComments,
                                onJoin = { viewModel.joinLiveStream(streamId) },
                                onLeave = { viewModel.leaveLiveStream(streamId) },
                                onSendComment = { text -> viewModel.sendLiveComment(streamId, text) },
                                onSendHeart = { viewModel.sendLiveHeart(streamId) },
                                onAuthorClick = { uid, username ->
                                    openUserProfile(uid, username, false)
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }
                    }

                    if (showAssistantFab) {
                        FloatingMomiAssistantButton(
                            onClick = { showAiAssistantSheet = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .then(if (currentRoute == LumaRoutes.MARKETPLACE) Modifier.navigationBarsPadding() else Modifier)
                                .padding(end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
    }

    if (showAiAssistantSheet) {
        MomiAiAssistantBottomSheet(
            messages = aiMessages,
            uiState = aiUiState,
            onSendPrompt = viewModel::sendAiPrompt,
            onDismiss = { showAiAssistantSheet = false }
        )
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAvailableUpdate,
            icon = {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Update Available (v${update.latestVersionName})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = update.releaseTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = update.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installed: v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissAvailableUpdate()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                            )
                        }
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAvailableUpdate) {
                    Text("Later")
                }
            }
        )
    }

    if (activeCommentPostId != null) {
        CommentsBottomSheet(
            comments = activeComments,
            currentUser = profile,
            onDismiss = viewModel::closeComments,
            onSubmitComment = viewModel::submitComment,
            onToggleCommentLike = viewModel::toggleCommentLike,
            onAuthorClick = { userId, username ->
                openUserProfile(userId, username, false)
            }
        )
    }

    pendingReportTarget?.let { target ->
        ReportContentSheet(
            target = target,
            onDismiss = { pendingReportTarget = null },
            onSubmitReport = { category, detail ->
                viewModel.submitContentReport(
                    targetType = target.targetType,
                    targetId = target.targetId,
                    targetAuthorUid = target.targetAuthorUid,
                    targetAuthorUsername = target.targetAuthorUsername,
                    targetPreviewText = target.previewText,
                    category = category,
                    reasonDetail = detail
                )
            }
        )
    }
}

private fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

@Volatile
private var firestorePersistenceConfigured = false

private fun configureFirestoreCacheOnce(db: FirebaseFirestore) {
    if (firestorePersistenceConfigured) return
    synchronized(MainActivity::class.java) {
        if (firestorePersistenceConfigured) return
        runCatching {
            db.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024L * 1024L)
                        .build()
                )
            }
        }
        firestorePersistenceConfigured = true
    }
}
