package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.auth.signOut
import com.example.data.model.Post
import com.example.data.repository.MomiFirestoreRepository
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.LumaAvatar
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PostDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.LumaTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.MomiViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import kotlinx.coroutines.launch

private object LumaRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val CREATE = "create?story={story}"
    const val CLIPS = "clips"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages?threadId={threadId}"
    const val STORY_VIEWER = "story/{storyId}"
    const val POST_DETAIL = "post/{postId}"

    fun createRoute(isStory: Boolean = false) = "create?story=$isStory"
    fun messagesRoute(threadId: String? = null) =
        if (threadId.isNullOrBlank()) "messages" else "messages?threadId=$threadId"
    fun storyRoute(storyId: String) = "story/$storyId"
    fun postDetailRoute(postId: String) = "post/$postId"
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

        runCatching {
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
                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                        MomiViewModel(
                            repository = MomiFirestoreRepository(db),
                            currentUserId = uid,
                            initialDisplayName = user.displayName,
                            initialEmail = user.email
                        )
                    }
                }
            )
            LaunchedEffect(viewModel) {
                viewModel.loadAppearance(context)
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
    val followingUids by viewModel.followingUids.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val explorePosts by viewModel.explorePosts.collectAsStateWithLifecycle()
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val messageThreads by viewModel.messageThreads.collectAsStateWithLifecycle()
    val unreadMessages by viewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val appColorScheme by viewModel.colorScheme.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedExploreCategory.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val activePostForComments by viewModel.activePostForComments.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    val showBottomBar = currentRoute in listOf(
        LumaRoutes.HOME,
        LumaRoutes.EXPLORE,
        LumaRoutes.CLIPS,
        LumaRoutes.PROFILE
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
                                unreadNotificationCount = unreadNotifications,
                                unreadMessageCount = unreadMessages,
                                onOpenSearch = { navigatePrimaryTab(LumaRoutes.EXPLORE) },
                                onOpenNotifications = { navController.navigate(LumaRoutes.NOTIFICATIONS) },
                                onOpenMessages = { navController.navigate(LumaRoutes.messagesRoute()) },
                                onOpenStory = { story -> navController.navigate(LumaRoutes.storyRoute(story.id)) },
                                onAddStoryClick = { navController.navigate(LumaRoutes.createRoute(true)) },
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
                                onAuthorClick = { username ->
                                    if (username == profile?.username) {
                                        navigatePrimaryTab(LumaRoutes.PROFILE)
                                    } else {
                                        viewModel.setSearchQuery(username)
                                        navigatePrimaryTab(LumaRoutes.EXPLORE)
                                    }
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
                                onPublishPost = { imageKey, localPath, mediaType, localVideoPath, videoDurationMs, audioTitle, localAudioPath, filter, caption, location, category, isReel ->
                                    viewModel.createPost(
                                        context = context,
                                        imageKey = imageKey,
                                        localImagePath = localPath,
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
                                    ) {
                                        navController.popBackStack()
                                        navigatePrimaryTab(LumaRoutes.HOME)
                                    }
                                },
                                onPublishStory = { imageKey, localPath, mediaType, localVideoPath, videoDurationMs, audioTitle, localAudioPath, caption ->
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
                                    ) {
                                        navController.popBackStack()
                                        navigatePrimaryTab(LumaRoutes.HOME)
                                    }
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
                                }
                            )
                        }

                        composable(LumaRoutes.PROFILE) {
                            ProfileScreen(
                                profile = profile,
                                allCreators = allCreators,
                                followingUids = followingUids,
                                allPosts = allPosts,
                                themePreference = themePreference,
                                appColorScheme = appColorScheme,
                                isAdminUser = viewModel.isAdminUser,
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
                                onSignOut = onSignOut
                            )
                        }

                        composable(LumaRoutes.NOTIFICATIONS) {
                            NotificationsScreen(
                                notifications = notifications,
                                allCreators = allCreators,
                                onToggleFollowBack = viewModel::toggleNotificationFollow,
                                onMarkAllRead = viewModel::markNotificationsRead,
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
                                observeChatMessages = viewModel::observeChatMessages,
                                onStartChat = viewModel::startDirectChat,
                                onClearUnread = viewModel::clearThreadUnread,
                                onSendMessage = viewModel::sendMessage,
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
                                onDeletePost = { viewModel.deletePost(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (activePostForComments != null) {
        CommentsBottomSheet(
            comments = activeComments,
            currentUser = profile,
            onDismiss = viewModel::closeComments,
            onSubmitComment = viewModel::submitComment,
            onToggleCommentLike = viewModel::toggleCommentLike
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
