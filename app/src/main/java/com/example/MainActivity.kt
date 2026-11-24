package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.lifecycleScope
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
import com.example.auth.configureAppCheck
import com.example.auth.signOut
import com.example.billing.PlayBillingManager
import com.example.billing.PlayPurchaseEvent
import com.example.data.model.CreditTransaction
import com.example.data.model.Post
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.MomiFirestoreRepository
import com.example.data.repository.WalletRepository
import com.example.data.supabase.MomiSupabaseClient
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.FloatingMomiAssistantButton
import com.example.ui.components.LocalRemoteVideoResolver
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiAiAssistantBottomSheet
import com.example.ui.components.MomiInterstitialController
import com.example.ui.components.MomiStartIoSession
import com.example.ui.components.PostBoostBottomSheet
import com.example.ui.screens.CommunitiesScreen
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
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.LumaTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.MomiViewModel
import com.example.util.AppUpdateChecker
import com.example.util.MomiAlertNotifier
import com.example.util.MomiDeepLink
import com.example.util.MomiLinks
import com.google.android.gms.ads.MobileAds
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.startapp.sdk.adsbase.StartAppSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private object LumaRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val CREATE = "create?story={story}&pageId={pageId}&groupId={groupId}&communityName={communityName}"
    const val CLIPS = "clips"
    const val PROFILE = "profile"
    const val WALLET = "wallet"
    const val MARKETPLACE = "marketplace"
    const val COMMUNITIES = "communities"
    const val USER_PROFILE = "user/{userId}?username={username}&showcase={showcase}"
    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages?threadId={threadId}"
    const val STORY_VIEWER = "story/{storyId}"
    const val POST_DETAIL = "post/{postId}"
    const val GO_LIVE = "go_live"
    const val LIVE_VIEWER = "live/{streamId}"

    fun createRoute(
        isStory: Boolean = false,
        pageId: String? = null,
        groupId: String? = null,
        communityName: String = ""
    ) = "create?story=$isStory&pageId=${Uri.encode(pageId.orEmpty())}&groupId=${Uri.encode(groupId.orEmpty())}&communityName=${Uri.encode(communityName)}"

    fun userProfileRoute(userId: String, username: String = "", isShowcase: Boolean = false): String {
        val safeUid = Uri.encode(userId.ifBlank { "creator" })
        val safeUsername = Uri.encode(username)
        return "user/$safeUid?username=$safeUsername&showcase=$isShowcase"
    }
    fun messagesRoute(threadId: String? = null) =
        if (threadId.isNullOrBlank()) "messages" else "messages?threadId=$threadId"
    fun storyRoute(storyId: String) = "story/$storyId"
    fun postDetailRoute(postId: String) = "post/${Uri.encode(postId)}"
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
    private val latestIncomingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MomiAlertNotifier.ensureChannel(this)
        configureAppCheck(this, intent)
        latestIncomingIntent.value = intent

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                MobileAds.initialize(this@MainActivity) {
                    MomiStartIoSession.updateAdMob("SDK initialized")
                }
            }.onFailure { err ->
                MomiStartIoSession.updateAdMob("Init skipped: ${err.message ?: "error"}")
            }
        }

        runCatching {
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
            StartAppSDK.initParams(this, BuildConfig.STARTIO_APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    MomiStartIoSession.markInitialized("SDK initialized")
                }
                .init()
            MomiStartIoSession.scheduleFallbackReady()
        }

        setContent {
            var themePreference by remember { mutableStateOf(AppearancePrefs.loadThemePreference(this)) }
            var appColorScheme by remember { mutableStateOf(AppearancePrefs.loadColorScheme(this)) }

            LumaTheme(
                themePreference = themePreference,
                appColorScheme = appColorScheme
            ) {
                AppNavigation(
                    incomingIntent = latestIncomingIntent.value,
                    onConsumeIncomingIntent = { latestIncomingIntent.value = null },
                    onThemePreferenceChange = { themePreference = it },
                    onColorSchemeChange = { appColorScheme = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIncomingIntent.value = intent
    }
}

@Composable
private fun AppNavigation(
    incomingIntent: Intent?,
    onConsumeIncomingIntent: () -> Unit,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onColorSchemeChange: (AppColorScheme) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(context) {
        if (MomiSupabaseClient.activeSession == null) {
            AppearancePrefs.restoreSupabaseSession(context)
        }
    }
    val currentSession by MomiSupabaseClient.sessionFlow.collectAsStateWithLifecycle()

    val session = currentSession
    if (session == null) {
        SignInScreen(
            onAuthSuccess = {
                // MomiSupabaseClient.sessionFlow updates reactively and switches to LumaApp.
            }
        )
    } else {
        val uid = session.userId
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
                            walletRepository = WalletRepository(db),
                            currentUserId = uid,
                            initialDisplayName = session.displayName,
                            initialEmail = session.email
                        )
                    }
                }
            )
            val sdkReady by MomiStartIoSession.isInitialized.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.loadAppearance(context)
                viewModel.checkForUpdates(
                    context = context,
                    manual = false,
                    isPlayStoreInstall = AppUpdateChecker.isInstalledFromGooglePlay(context)
                )
            }
            val monetizationConfig by viewModel.monetizationConfig.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel, sdkReady, monetizationConfig) {
                if (sdkReady || monetizationConfig.admobEnabled) {
                    MomiInterstitialController.preload(context, monetizationConfig)
                }
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
                incomingIntent = incomingIntent,
                onConsumeIncomingIntent = onConsumeIncomingIntent,
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
    incomingIntent: Intent?,
    onConsumeIncomingIntent: () -> Unit,
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
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val creditTransactions by viewModel.creditTransactions.collectAsStateWithLifecycle()
    val monetizationConfig by viewModel.monetizationConfig.collectAsStateWithLifecycle()
    val isProcessingWalletAction by viewModel.isProcessingWalletAction.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val showcaseAccounts by viewModel.showcaseAccounts.collectAsStateWithLifecycle()
    val dismissedAnnouncementId by viewModel.dismissedAnnouncementId.collectAsStateWithLifecycle()
    var showAiAssistantSheet by remember { mutableStateOf(false) }
    var pendingReportTarget by remember { mutableStateOf<ReportDraftTarget?>(null) }
    var pendingBoostPost by remember { mutableStateOf<Post?>(null) }
    var notificationSoundsEnabled by remember(context) {
        mutableStateOf(AppearancePrefs.isNotificationSoundsEnabled(context))
    }
    var canPostSystemNotifications by remember(context) {
        mutableStateOf(MomiAlertNotifier.canPostNotifications(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPostSystemNotifications = granted || MomiAlertNotifier.canPostNotifications(context)
    }

    LaunchedEffect(context) {
        MomiAlertNotifier.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MomiAlertNotifier.canPostNotifications(context) &&
            !AppearancePrefs.hasPromptedNotificationPermission(context)
        ) {
            AppearancePrefs.markNotificationPermissionPrompted(context)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pendingDeepLinkUser by remember { mutableStateOf<String?>(null) }

    // Handle notification tap routing & shared post/profile deep links (cold start + warm onNewIntent)
    LaunchedEffect(incomingIntent) {
        val intentToHandle = incomingIntent ?: return@LaunchedEffect
        val routeExtra = intentToHandle.getStringExtra(MomiAlertNotifier.EXTRA_ALERT_ROUTE)
        if (!routeExtra.isNullOrBlank()) {
            intentToHandle.removeExtra(MomiAlertNotifier.EXTRA_ALERT_ROUTE)
            when (routeExtra) {
                LumaRoutes.NOTIFICATIONS,
                LumaRoutes.WALLET,
                "messages" -> navController.navigate(
                    if (routeExtra == "messages") LumaRoutes.messagesRoute() else routeExtra
                )
            }
            onConsumeIncomingIntent()
            return@LaunchedEffect
        }

        when (val link = MomiLinks.parseDeepLink(intentToHandle.data)) {
            is MomiDeepLink.PostTarget -> {
                intentToHandle.data = null
                onConsumeIncomingIntent()
                navController.navigate(LumaRoutes.postDetailRoute(link.postId)) {
                    launchSingleTop = true
                }
            }
            is MomiDeepLink.UserTarget -> {
                intentToHandle.data = null
                onConsumeIncomingIntent()
                pendingDeepLinkUser = link.username
            }
            null -> Unit
        }
    }

    // Real-time alert sounds & system notifications for new activity, DMs, and gifts
    val baselineNotifIds = remember(viewModel.currentUserId) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(notifications, notificationSoundsEnabled) {
        val currentIds = notifications.map { it.id }.toSet()
        val prev = baselineNotifIds.value
        if (prev == null) {
            baselineNotifIds.value = currentIds
            return@LaunchedEffect
        }
        val newlyArrived = notifications.filter {
            it.id !in prev && it.isUnread && it.actorUid != viewModel.currentUserId
        }
        baselineNotifIds.value = prev + currentIds
        val latest = newlyArrived.firstOrNull() ?: return@LaunchedEffect
        MomiAlertNotifier.postAlertNotification(
            context = context,
            notificationId = latest.id.hashCode(),
            title = "@${latest.actorUsername.ifBlank { "Momi" }}",
            body = latest.message.ifBlank { "New activity on Momi" },
            route = LumaRoutes.NOTIFICATIONS,
            soundEnabled = notificationSoundsEnabled
        )
    }

    val baselineThreadSignatures = remember(viewModel.currentUserId) { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(messageThreads, currentRoute, notificationSoundsEnabled) {
        val currentMap = messageThreads.associate { thread ->
            thread.id to "${thread.lastSenderUid}|${thread.unreadCount}|${thread.lastMessage}"
        }
        val prev = baselineThreadSignatures.value
        if (prev == null) {
            baselineThreadSignatures.value = currentMap
            return@LaunchedEffect
        }
        val newIncoming = messageThreads.firstOrNull { thread ->
            val sig = currentMap[thread.id]
            prev[thread.id] != sig &&
                thread.isUnreadFor(viewModel.currentUserId) &&
                thread.lastSenderUid.isNotBlank() &&
                thread.lastSenderUid != viewModel.currentUserId &&
                currentRoute?.startsWith("messages") != true
        }
        baselineThreadSignatures.value = currentMap
        if (newIncoming != null) {
            val senderHandle = newIncoming.peerUsernameFor(viewModel.currentUserId).ifBlank { "Creator" }
            MomiAlertNotifier.postAlertNotification(
                context = context,
                notificationId = ("dm_" + newIncoming.id).hashCode(),
                title = "Message from @$senderHandle",
                body = newIncoming.lastMessage.ifBlank { "Sent a new message" },
                route = "messages",
                soundEnabled = notificationSoundsEnabled
            )
        }
    }

    val baselineGiftTxIds = remember(viewModel.currentUserId) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(creditTransactions, notificationSoundsEnabled) {
        val incomingGiftIds = creditTransactions
            .filter { it.type == CreditTransaction.TYPE_GIFT && it.recipientUid == viewModel.currentUserId && it.senderUid != viewModel.currentUserId }
            .map { it.id }
            .toSet()
        val prev = baselineGiftTxIds.value
        if (prev == null) {
            baselineGiftTxIds.value = incomingGiftIds
            return@LaunchedEffect
        }
        val newGift = creditTransactions.firstOrNull {
            it.id in incomingGiftIds && it.id !in prev
        }
        baselineGiftTxIds.value = prev + incomingGiftIds
        if (newGift != null) {
            MomiAlertNotifier.postAlertNotification(
                context = context,
                notificationId = ("gift_" + newGift.id).hashCode(),
                title = "Gift received from @${newGift.senderUsername.ifBlank { "Supporter" }}",
                body = "${newGift.giftName ?: "Gift"} (+${newGift.creditsAmount} credits)",
                route = LumaRoutes.WALLET,
                soundEnabled = notificationSoundsEnabled
            )
        }
    }

    val billingManager = remember(context) { PlayBillingManager(context) }
    val billingState by billingManager.uiState.collectAsStateWithLifecycle()

    DisposableEffect(billingManager) {
        billingManager.start()
        onDispose {
            billingManager.release()
        }
    }

    LaunchedEffect(billingManager, viewModel) {
        billingManager.events.collect { event ->
            when (event) {
                is PlayPurchaseEvent.Credited -> {
                    viewModel.handlePlayPurchaseCredited(
                        productId = event.productId,
                        orderId = event.orderId,
                        purchaseToken = event.purchaseToken
                    )
                }
                is PlayPurchaseEvent.Pending -> {
                    viewModel.showInfoNotice("Google Play purchase pending confirmation for ${event.productId}.")
                }
                is PlayPurchaseEvent.Cancelled -> {
                    viewModel.showInfoNotice(event.message)
                }
                is PlayPurchaseEvent.Failed -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

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

    fun sharePostExternally(post: Post) {
        shareText(
            context = context,
            subject = "Momi post by @${post.authorUsername}",
            text = MomiLinks.buildPostShareText(post)
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

    fun openHashtagExplore(tag: String) {
        val clean = tag.trim().removePrefix("#")
        if (clean.isNotBlank()) {
            viewModel.setExploreCategory("For You")
            viewModel.setSearchQuery("#$clean")
            navigatePrimaryTab(LumaRoutes.EXPLORE)
        }
    }

    fun openMentionProfile(username: String) {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return
        val matched = allCreators.firstOrNull { it.username.equals(clean, ignoreCase = true) }
        if (matched != null) {
            openUserProfile(matched.userId, matched.username, false)
        } else {
            val showcasePost = allPosts.firstOrNull { it.authorUsername.equals(clean, ignoreCase = true) }
            if (showcasePost != null) {
                openUserProfile(
                    showcasePost.userId,
                    showcasePost.authorUsername,
                    showcasePost.id.startsWith("showcase_")
                )
            } else {
                viewModel.setSearchQuery("@$clean")
                navigatePrimaryTab(LumaRoutes.EXPLORE)
            }
        }
    }

    LaunchedEffect(pendingDeepLinkUser, allCreators) {
        val targetUsername = pendingDeepLinkUser ?: return@LaunchedEffect
        if (allCreators.isEmpty()) return@LaunchedEffect
        pendingDeepLinkUser = null
        openMentionProfile(targetUsername)
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
                                            avatarUrl = profile?.avatarUrl,
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
                            selected = currentRoute == LumaRoutes.COMMUNITIES,
                            onClick = { navController.navigate(LumaRoutes.COMMUNITIES) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == LumaRoutes.COMMUNITIES) Icons.Filled.Groups else Icons.Outlined.Groups,
                                    contentDescription = "Pages & Groups"
                                )
                            },
                            label = { Text("Pages") },
                            modifier = Modifier.testTag("rail_communities")
                        )
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
                                onOpenCommunities = { navController.navigate(LumaRoutes.COMMUNITIES) },
                                onOpenAiAssistant = { showAiAssistantSheet = true },
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
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onHashtagClick = { tag -> openHashtagExplore(tag) },
                                onMentionClick = { username -> openMentionProfile(username) },
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
                                isAdminViewActive = isAdminViewActive,
                                monetizationConfig = monetizationConfig,
                                dismissedAnnouncementId = dismissedAnnouncementId.orEmpty(),
                                onDismissAnnouncement = viewModel::dismissInAppAnnouncement,
                                onBoostPost = { post -> pendingBoostPost = post },
                                onToggleRecommendedPost = { post, recommended ->
                                    viewModel.adminSetPostRecommended(post, recommended)
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
                                },
                                onOpenAiAssistant = { showAiAssistantSheet = true },
                                monetizationConfig = monetizationConfig
                            )
                        }

                        composable(
                            route = LumaRoutes.CREATE,
                            arguments = listOf(
                                navArgument("story") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                },
                                navArgument("pageId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("groupId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("communityName") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val startAsStory = backStackEntry.arguments?.getBoolean("story") ?: false
                            val initPageId = Uri.decode(backStackEntry.arguments?.getString("pageId").orEmpty()).takeIf { it.isNotBlank() }
                            val initGroupId = Uri.decode(backStackEntry.arguments?.getString("groupId").orEmpty()).takeIf { it.isNotBlank() }
                            val initCommunityName = Uri.decode(backStackEntry.arguments?.getString("communityName").orEmpty())
                            CreatePostScreen(
                                initialModeStory = startAsStory,
                                creators = allCreators,
                                pages = pages,
                                groups = groups,
                                initialPageId = initPageId,
                                initialGroupId = initGroupId,
                                initialCommunityName = initCommunityName,
                                currentUserId = viewModel.currentUserId,
                                onClose = { navController.popBackStack() },
                                onPublishPost = { imageKey, localPath, extraImagePaths, mediaType, localVideoPath, videoDurationMs, audioTitle, localAudioPath, filter, aspectRatio, taggedUsernames, hashtags, caption, location, category, isReel, pageId, groupId, communityName, onFinished ->
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
                                        aspectRatio = aspectRatio,
                                        taggedUsernames = taggedUsernames,
                                        hashtags = hashtags,
                                        caption = caption,
                                        location = location,
                                        category = category,
                                        isReel = isReel,
                                        pageId = pageId,
                                        groupId = groupId,
                                        communityName = communityName
                                    ) { succeeded ->
                                        onFinished(succeeded)
                                        if (succeeded) {
                                            navController.popBackStack()
                                            navigatePrimaryTab(LumaRoutes.HOME)
                                            MomiInterstitialController.maybeShowOnMilestone(context, monetizationConfig)
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
                                            MomiInterstitialController.maybeShowOnMilestone(context, monetizationConfig)
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
                            val showcasePostsCount by viewModel.showcasePostsCount.collectAsStateWithLifecycle()
                            val showcaseStoriesCount by viewModel.showcaseStoriesCount.collectAsStateWithLifecycle()
                            val isShowcaseBusy by viewModel.isShowcaseActionBusy.collectAsStateWithLifecycle()
                            val supabaseStatus by viewModel.supabaseStatus.collectAsStateWithLifecycle()
                            val isSupabaseBusy by viewModel.isSupabaseBusy.collectAsStateWithLifecycle()
                            ProfileScreen(
                                profile = profile,
                                allCreators = allCreators,
                                follows = follows,
                                followingUids = followingUids,
                                allPosts = allPosts,
                                wallet = wallet,
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
                                onOpenWallet = { navController.navigate(LumaRoutes.WALLET) },
                                onOpenAiAssistant = { showAiAssistantSheet = true },
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onShareProfile = {
                                    val handle = profile?.username ?: "momi.creator"
                                    shareText(
                                        context = context,
                                        subject = "Follow @$handle on Momi",
                                        text = MomiLinks.buildProfileShareText(handle)
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
                                notificationSoundsEnabled = notificationSoundsEnabled,
                                canPostSystemNotifications = canPostSystemNotifications,
                                onToggleNotificationSounds = { enabled ->
                                    notificationSoundsEnabled = enabled
                                    AppearancePrefs.setNotificationSoundsEnabled(context, enabled)
                                    if (enabled) {
                                        MomiAlertNotifier.playAlertSound(context)
                                    }
                                },
                                onTestAlertSound = {
                                    MomiAlertNotifier.postAlertNotification(
                                        context = context,
                                        notificationId = 9001,
                                        title = "Momi Alert Sound Check",
                                        body = "Notification sounds & chimes are active on your device.",
                                        route = LumaRoutes.NOTIFICATIONS,
                                        soundEnabled = true
                                    )
                                },
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        canPostSystemNotifications = MomiAlertNotifier.canPostNotifications(context)
                                    }
                                },
                                onCheckForUpdates = {
                                    viewModel.checkForUpdates(
                                        context = context,
                                        manual = true,
                                        isPlayStoreInstall = AppUpdateChecker.isInstalledFromGooglePlay(context)
                                    )
                                },
                                showcasePostsCount = showcasePostsCount,
                                showcaseStoriesCount = showcaseStoriesCount,
                                showcaseAccounts = showcaseAccounts,
                                isShowcaseBusy = isShowcaseBusy,
                                onPublishShowcaseBurst = viewModel::publishShowcaseActivityBurst,
                                onEngageWithShowcase = viewModel::engageRecentPostsWithShowcase,
                                onRemoveAllShowcase = viewModel::removeAllShowcaseContent,
                                onAuthorizeShowcaseAccount = viewModel::authorizeShowcaseAccount,
                                onUpdateShowcasePersonaProfile = viewModel::updateShowcasePersonaProfile,
                                onPublishCustomShowcasePost = { account, caption, mediaType, imageKey, localImagePath, localVideoPath, isReel, onDone ->
                                    viewModel.publishCustomShowcasePost(
                                        context = context,
                                        account = account,
                                        caption = caption,
                                        mediaType = mediaType,
                                        imageKey = imageKey,
                                        localImagePath = localImagePath,
                                        localVideoPath = localVideoPath,
                                        isReel = isReel,
                                        onComplete = onDone
                                    )
                                },
                                onBoostPostWithShowcase = viewModel::boostPostMetricsWithShowcase,
                                onPublishInAppAnnouncement = viewModel::publishInAppAnnouncement,
                                monetizationConfig = monetizationConfig,
                                isSavingMonetization = isProcessingWalletAction,
                                onSaveMonetizationConfig = viewModel::saveAdMonetizationConfig,
                                onUpdatePartnerConsent = { consented -> viewModel.updatePartnerConsent(consented) },
                                onGrantConsentingUser = { creator, credits, perk, grantVerified ->
                                    viewModel.adminGrantConsentingUser(
                                        targetUser = creator,
                                        creditsAmount = credits,
                                        perk = perk,
                                        grantVerified = grantVerified
                                    )
                                },
                                onAdminSetPostRecommended = { post, recommended, label ->
                                    viewModel.adminSetPostRecommended(post, recommended, label)
                                },
                                isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                supabaseStatus = supabaseStatus,
                                isSupabaseBusy = isSupabaseBusy,
                                onTestSupabaseConnection = viewModel::testSupabaseConnection,
                                onSyncSupabaseSnapshot = viewModel::syncLiveSnapshotToSupabase,
                                onBuildSupabaseExportJson = viewModel::buildSupabaseMigrationSnapshotJson,
                                onSignOut = onSignOut
                            )
                        }

                        composable(LumaRoutes.WALLET) {
                            WalletScreen(
                                currentUserId = viewModel.currentUserId,
                                profile = profile,
                                wallet = wallet,
                                transactions = creditTransactions,
                                allCreators = allCreators,
                                billingState = billingState,
                                isProcessing = isProcessingWalletAction,
                                onRefreshBilling = { billingManager.refreshCatalogAndPendingPurchases() },
                                onBuyWithPlay = { pkg ->
                                    billingManager.launchCreditPurchase(
                                        activity = context.findActivity(),
                                        productId = pkg.productId,
                                        accountUid = viewModel.currentUserId
                                    )
                                },
                                onSandboxTopUp = { pkg ->
                                    if (BuildConfig.DEBUG) {
                                        viewModel.completeCreditPurchase(
                                            pkg = pkg,
                                            paymentMethod = CreditTransaction.METHOD_DEBUG_SANDBOX,
                                            paymentRef = "DEBUG_${System.currentTimeMillis()}"
                                        )
                                    }
                                },
                                onSendGiftToCreator = { recipientUid, recipientUsername, gift ->
                                    viewModel.sendGift(
                                        recipientUid = recipientUid,
                                        recipientUsername = recipientUsername,
                                        gift = gift
                                    )
                                },
                                onUnlockPerk = { perk -> viewModel.unlockAccountPerk(perk) },
                                ownPosts = allPosts.filter {
                                    it.userId == viewModel.currentUserId && !it.id.startsWith("showcase_")
                                },
                                onUpdatePartnerConsent = { consented -> viewModel.updatePartnerConsent(consented) },
                                onBoostPostWithCredits = { post, option ->
                                    viewModel.boostPostWithCredits(post, option)
                                },
                                onBack = { navController.popBackStack() }
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
                                onOpenAiAssistant = { showAiAssistantSheet = true },
                                monetizationConfig = monetizationConfig,
                                modifier = Modifier.navigationBarsPadding()
                            )
                        }

                        composable(LumaRoutes.COMMUNITIES) {
                            CommunitiesScreen(
                                pages = pages,
                                groups = groups,
                                allPosts = allPosts,
                                currentUserId = viewModel.currentUserId,
                                isAdminViewActive = isAdminViewActive,
                                onBack = { navController.popBackStack() },
                                onCreatePage = viewModel::createPage,
                                onToggleFollowPage = viewModel::toggleFollowPage,
                                onCreateGroup = viewModel::createGroup,
                                onToggleJoinGroup = viewModel::toggleJoinGroup,
                                onPostToCommunity = { pid, gid, commName ->
                                    navController.navigate(
                                        LumaRoutes.createRoute(
                                            isStory = false,
                                            pageId = pid,
                                            groupId = gid,
                                            communityName = commName
                                        )
                                    )
                                },
                                onPostClick = { post ->
                                    navController.navigate(LumaRoutes.postDetailRoute(post.id))
                                },
                                onLikeToggle = { viewModel.toggleLike(it) },
                                onDoubleTapLike = { viewModel.doubleTapLike(it) },
                                onCommentClick = { viewModel.openComments(it.id) },
                                onShareClick = { sharePostExternally(it) },
                                onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                onFollowAuthorToggle = {
                                    viewModel.toggleFollow(
                                        targetUid = it.userId,
                                        targetUsername = it.authorUsername,
                                        currentlyFollowing = it.isFollowingAuthor
                                    )
                                },
                                onAuthorClick = { uid, uname, isShowcase ->
                                    openUserProfile(uid, uname, isShowcase)
                                }
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
                                wallet = wallet,
                                isSendingGift = isProcessingWalletAction,
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
                                onSendGift = { creator, gift ->
                                    viewModel.sendGift(
                                        recipientUid = creator.userId,
                                        recipientUsername = creator.username,
                                        gift = gift
                                    )
                                },
                                onOpenWallet = { navController.navigate(LumaRoutes.WALLET) },
                                onShareProfile = { targetProfile ->
                                    shareText(
                                        context = context,
                                        subject = "Follow @${targetProfile.username} on Momi",
                                        text = MomiLinks.buildProfileShareText(targetProfile.username)
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
                                    if (userId != "momi_official") {
                                        openUserProfile(userId, username, false)
                                    }
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
                            val postId = Uri.decode(backStackEntry.arguments?.getString("postId").orEmpty())
                            val post by remember(postId) {
                                viewModel.observePost(postId)
                            }.collectAsStateWithLifecycle(initialValue = allPosts.firstOrNull { it.id == postId })

                            PostDetailScreen(
                                post = post,
                                currentUserId = viewModel.currentUserId,
                                relatedPosts = allPosts.filter { it.id != postId }.take(12),
                                onBack = { navController.popBackStack() },
                                onPostClick = { nextPost ->
                                    navController.navigate(LumaRoutes.postDetailRoute(nextPost.id))
                                },
                                onHashtagClick = { tag -> openHashtagExplore(tag) },
                                onMentionClick = { username -> openMentionProfile(username) },
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
                                onPostViewed = { viewModel.recordPostView(it.id) },
                                isAdminViewActive = isAdminViewActive,
                                onBoostPost = { p -> pendingBoostPost = p },
                                onToggleRecommendedPost = { p, recommended ->
                                    viewModel.adminSetPostRecommended(p, recommended)
                                }
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
                                onPublishFrame = { seq, frameData, cameraEnabled, micMuted ->
                                    viewModel.publishLiveFrame(
                                        streamId = streamId,
                                        seq = seq,
                                        frameData = frameData,
                                        cameraEnabled = cameraEnabled,
                                        micMuted = micMuted
                                    )
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
                            val liveFrame by remember(streamId) {
                                viewModel.observeLiveFrame(streamId)
                            }.collectAsStateWithLifecycle(initialValue = null)
                            val liveComments by remember(streamId) {
                                viewModel.observeLiveComments(streamId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            LiveViewerScreen(
                                stream = liveStream,
                                liveFrame = liveFrame,
                                comments = liveComments,
                                wallet = wallet,
                                isSendingGift = isProcessingWalletAction,
                                onJoin = { onJoined -> viewModel.joinLiveStream(streamId, onJoined) },
                                onLeave = { viewModel.leaveLiveStream(streamId) },
                                onSendComment = { text -> viewModel.sendLiveComment(streamId, text) },
                                onSendHeart = { viewModel.sendLiveHeart(streamId) },
                                onSendGift = { gift ->
                                    liveStream?.let { s ->
                                        viewModel.sendGift(
                                            recipientUid = s.userId,
                                            recipientUsername = s.username,
                                            gift = gift,
                                            streamId = s.id
                                        )
                                    }
                                },
                                onOpenWallet = { navController.navigate(LumaRoutes.WALLET) },
                                onAuthorClick = { uid, username ->
                                    openUserProfile(uid, username, false)
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }
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
            onDismissRequest = { viewModel.dismissAvailableUpdate(context) },
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
                        viewModel.dismissAvailableUpdate(context)
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
                TextButton(onClick = { viewModel.dismissAvailableUpdate(context) }) {
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

    pendingBoostPost?.let { postToBoost ->
        PostBoostBottomSheet(
            post = postToBoost,
            wallet = wallet,
            isProcessing = isProcessingWalletAction,
            onSelectBoost = { option ->
                viewModel.boostPostWithCredits(postToBoost, option) { ok ->
                    if (ok) pendingBoostPost = null
                }
            },
            onOpenWallet = {
                pendingBoostPost = null
                navController.navigate(LumaRoutes.WALLET)
            },
            onDismiss = { pendingBoostPost = null }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
