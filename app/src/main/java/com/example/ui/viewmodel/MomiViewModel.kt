package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MomiAiAssistantRepository
import com.example.data.model.AccountPerkItem
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.ContentReport
import com.example.data.model.CreditPackageOption
import com.example.data.model.CreditTransaction
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveFrame
import com.example.data.model.LiveGiftItem
import com.example.data.model.LiveStream
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.MessageThread
import com.example.data.model.ModerationScopes
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.MomiRoles
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.MomiFirestoreRepository
import com.example.data.repository.WalletRepository
import com.example.ui.components.AiAssistantTurn
import com.example.ui.components.AiAssistantUiState
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.ThemePreference
import com.example.util.AppUpdateChecker
import com.example.util.AvailableAppUpdate
import com.example.util.MediaAssets
import com.example.util.StudioMusicCatalog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MomiViewModel(
    private val repository: MomiFirestoreRepository,
    private val marketplaceRepository: MarketplaceRepository,
    private val walletRepository: WalletRepository,
    val currentUserId: String,
    private val initialDisplayName: String?,
    private val initialEmail: String?,
    private val aiAssistantRepository: MomiAiAssistantRepository = MomiAiAssistantRepository()
) : ViewModel() {

    val isOwnerAccount: Boolean = initialEmail?.trim().equals("syali6511@gmail.com", ignoreCase = true)

    private val _adminViewEnabled = MutableStateFlow(isOwnerAccount)
    val adminViewEnabled: StateFlow<Boolean> = _adminViewEnabled.asStateFlow()

    val isAdminModeActive: StateFlow<Boolean> = _adminViewEnabled
        .map { isOwnerAccount && it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), isOwnerAccount)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _isSendingChatMedia = MutableStateFlow(false)
    val isSendingChatMedia: StateFlow<Boolean> = _isSendingChatMedia.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AvailableAppUpdate?>(null)
    val availableUpdate: StateFlow<AvailableAppUpdate?> = _availableUpdate.asStateFlow()

    private val _aiMessages = MutableStateFlow(
        listOf(
            AiAssistantTurn(
                id = "welcome_ai",
                isUser = false,
                text = "Hi! I'm Momi Assistant. Ask me to write captions, explain Marketplace verification, troubleshoot clips, or guide you through Momi features."
            )
        )
    )
    val aiMessages: StateFlow<List<AiAssistantTurn>> = _aiMessages.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiUiState: StateFlow<AiAssistantUiState> = _aiUiState.asStateFlow()

    // In-memory stream caches so quota/transient errors NEVER wipe visible posts, profiles, or orders
    private val cachedFollows = MutableStateFlow<List<FollowEdge>>(emptyList())
    private val cachedProfile = MutableStateFlow<UserProfile?>(null)
    private val cachedProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    private val cachedRawPosts = MutableStateFlow<List<Post>>(emptyList())
    private val lastKnownEnrichedPosts = MutableStateFlow<List<Post>>(emptyList())
    private val cachedStories = MutableStateFlow<List<Story>>(emptyList())
    private val cachedLiveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    private val cachedNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    private val cachedThreads = MutableStateFlow<List<MessageThread>>(emptyList())
    private val cachedMerchants = MutableStateFlow<List<MerchantAccount>>(emptyList())
    private val cachedProducts = MutableStateFlow<List<MarketProduct>>(emptyList())
    private val cachedOrders = MutableStateFlow<List<MarketOrder>>(emptyList())
    private val cachedContentReports = MutableStateFlow<List<ContentReport>>(emptyList())
    private val cachedWallet = MutableStateFlow<UserWallet?>(null)
    private val cachedCreditTransactions = MutableStateFlow<List<CreditTransaction>>(emptyList())
    private val cachedLikedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedBookmarkedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedSeenStoryIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedLikedCommentIds = MutableStateFlow<Set<String>>(emptySet())

    private val _isProcessingWalletAction = MutableStateFlow(false)
    val isProcessingWalletAction: StateFlow<Boolean> = _isProcessingWalletAction.asStateFlow()

    @Volatile private var hasNotifiedQuotaError = false
    @Volatile private var quotaCooldownUntilMs = 0L
    @Volatile private var hasAutoCheckedUpdatesInSession = false

    // Optimistic UI overlays so likes, bookmarks, follows, and comments respond in 0ms
    private val optimisticPostLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticBookmarks = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticFollows = MutableStateFlow<Map<String, Pair<Boolean, String>>>(emptyMap())
    private val optimisticCommentLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticCommentsByPost = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    private val videoDownloadMutex = Mutex()
    private val inFlightVideoDownloads = mutableMapOf<String, Deferred<Uri?>>()
    private val recordedPostViewIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repository.ensureUserSeeded(
                displayNameHint = initialDisplayName,
                emailHint = initialEmail
            ).onFailure { error ->
                if (isQuotaExceeded(error)) {
                    quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                    if (!hasNotifiedQuotaError) {
                        hasNotifiedQuotaError = true
                        _statusNotice.value = QUOTA_FRIENDLY_MESSAGE
                    }
                } else {
                    _errorMessage.value = unwrapErrorMessage(error, "Failed to initialize Momi profile")
                }
            }
            walletRepository.ensureWalletInitialized().onFailure { error ->
                Log.w(TAG, "Wallet initialization deferred", error)
            }
        }
        viewModelScope.launch {
            walletRepository.observeUnclaimedGifts(currentUserId)
                .catch { error ->
                    val root = unwrapOrRethrowCancellation(error)
                    if (isQuotaExceeded(root)) {
                        quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                    }
                    Log.w(TAG, "Error observing unclaimed gifts", root)
                }
                .collect { pendingGifts ->
                    pendingGifts.forEach { giftTx ->
                        walletRepository.claimGift(giftTx).onSuccess { claimedCredits ->
                            if (claimedCredits > 0L) {
                                _statusNotice.value =
                                    "Received +$claimedCredits credits from @${giftTx.senderUsername}!"
                            }
                        }.onFailure { err ->
                            Log.w(TAG, "Failed claiming gift ${giftTx.txId}", err)
                        }
                    }
                }
        }
    }

    private fun <T> Flow<T>.resilientWithCache(
        cache: MutableStateFlow<T>,
        label: String,
        surfaceQuotaError: Boolean = false
    ): Flow<T> = this
        .onEach { value -> cache.value = value }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing $label", root)
            if (isQuotaExceeded(root)) {
                quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                if (surfaceQuotaError && !hasNotifiedQuotaError) {
                    hasNotifiedQuotaError = true
                    _statusNotice.value = QUOTA_FRIENDLY_MESSAGE
                }
            }
            emit(cache.value)
        }

    private val rawFollows: StateFlow<List<FollowEdge>> = repository.observeFollows(currentUserId)
        .resilientWithCache(cachedFollows, "follows")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val follows: StateFlow<List<FollowEdge>> = combine(
        rawFollows,
        optimisticFollows
    ) { remoteFollows, overrides ->
        if (overrides.isEmpty()) {
            remoteFollows
        } else {
            val baseList = remoteFollows.filterNot { edge ->
                edge.followerUid == currentUserId && overrides[edge.targetUid]?.first == false
            }.toMutableList()
            val myUsername = fallbackProfile().username
            overrides.forEach { (targetUid, pair) ->
                val (desiredFollowing, targetUsername) = pair
                if (desiredFollowing && baseList.none { it.followerUid == currentUserId && it.targetUid == targetUid }) {
                    baseList.add(
                        FollowEdge(
                            id = "${currentUserId}_$targetUid",
                            followerUid = currentUserId,
                            targetUid = targetUid,
                            followerUsername = myUsername,
                            targetUsername = targetUsername,
                            createdAt = Timestamp.now()
                        )
                    )
                }
            }
            baseList
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val followingUids: StateFlow<Set<String>> = follows
        .map { edges ->
            edges.filter { it.followerUid == currentUserId }.map { it.targetUid }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val allProfiles: StateFlow<List<UserProfile>> = combine(
        repository.observeAllProfiles(currentUserId)
            .resilientWithCache(cachedProfiles, "creators"),
        follows
    ) { profiles, allFollows ->
        profiles.map { p ->
            p.copy(
                followersCount = allFollows.count { it.targetUid == p.userId }.toLong(),
                followingCount = allFollows.count { it.followerUid == p.userId }.toLong()
            )
        }
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error combining creators", root)
            emit(cachedProfiles.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val profileState: StateFlow<StreamUiState<UserProfile?>> = combine(
        repository.observeProfile(currentUserId)
            .resilientWithCache(cachedProfile, "profile"),
        follows
    ) { rawProfile, allFollows ->
        val resolved = rawProfile ?: cachedProfile.value ?: fallbackProfileBase()
        val hydrated = resolved.copy(
            followersCount = allFollows.count { it.targetUid == currentUserId }.toLong(),
            followingCount = allFollows.count { it.followerUid == currentUserId }.toLong()
        )
        StreamUiState.Success(hydrated) as StreamUiState<UserProfile?>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing profile", root)
            val recovered = cachedProfile.value ?: fallbackProfileBase()
            emit(StreamUiState.Success(recovered))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            StreamUiState.Success(fallbackProfileBase())
        )

    val profile: StateFlow<UserProfile?> = profileState
        .map { (it as? StreamUiState.Success)?.data ?: cachedProfile.value ?: fallbackProfileBase() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            fallbackProfileBase()
        )

    val currentUserRole: StateFlow<String> = combine(profile, isAdminModeActive) { prof, adminActive ->
        when {
            adminActive -> MomiRoles.ADMIN
            isOwnerAccount -> MomiRoles.USER
            else -> prof?.role?.uppercase()?.takeIf {
                it in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR, MomiRoles.USER)
            } ?: MomiRoles.USER
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        if (isOwnerAccount) MomiRoles.ADMIN else MomiRoles.USER
    )

    val currentModerationScopes: StateFlow<Set<String>> = combine(profile, isAdminModeActive) { prof, adminActive ->
        when {
            adminActive -> ModerationScopes.ALL_SCOPES.toSet()
            isOwnerAccount -> emptySet()
            prof?.role?.uppercase() in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR) ->
                prof?.moderationScopes?.toSet().orEmpty()
            else -> emptySet()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        if (isOwnerAccount) ModerationScopes.ALL_SCOPES.toSet() else emptySet()
    )

    val canAccessModerationQueue: StateFlow<Boolean> = combine(
        isAdminModeActive,
        currentUserRole,
        currentModerationScopes
    ) { adminActive, role, scopes ->
        adminActive || (role in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR) &&
            scopes.any { it in ModerationScopes.SURVEILLANCE_SCOPES })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), isOwnerAccount)

    val canCurateMarketplace: StateFlow<Boolean> = combine(
        isAdminModeActive,
        currentUserRole,
        currentModerationScopes
    ) { adminActive, role, scopes ->
        adminActive || (role == MomiRoles.EDITOR && scopes.contains(ModerationScopes.EDITORIAL_CURATION))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), isOwnerAccount)

    val contentReports: StateFlow<List<ContentReport>> = canAccessModerationQueue
        .flatMapLatest { canView ->
            if (!canView) {
                flowOf(emptyList())
            } else {
                repository.observeContentReports(currentUserId)
                    .resilientWithCache(cachedContentReports, "contentReports")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val openReportsCount: StateFlow<Int> = combine(
        contentReports,
        currentModerationScopes,
        isAdminModeActive
    ) { reports, scopes, adminActive ->
        reports.count { report ->
            report.status == "OPEN" && (adminActive || scopes.contains(report.category))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private val likedPostIds: StateFlow<Set<String>> = repository.observeLikedPostIds(currentUserId)
        .resilientWithCache(cachedLikedPostIds, "likedPosts")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val bookmarkedPostIds: StateFlow<Set<String>> = repository.observeBookmarkedPostIds(currentUserId)
        .resilientWithCache(cachedBookmarkedPostIds, "bookmarks")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val seenStoryIds: StateFlow<Set<String>> = repository.observeSeenStoryIds(currentUserId)
        .resilientWithCache(cachedSeenStoryIds, "seenStories")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val likedCommentIds: StateFlow<Set<String>> = repository.observeLikedCommentIds(currentUserId)
        .resilientWithCache(cachedLikedCommentIds, "likedComments")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val postBaseStreams = combine(
        repository.observeAllPosts(currentUserId)
            .resilientWithCache(cachedRawPosts, "posts", surfaceQuotaError = true),
        allProfiles,
        likedPostIds,
        bookmarkedPostIds,
        followingUids
    ) { posts, profiles, liked, bookmarked, following ->
        PostHydrationInputs(posts, profiles, liked, bookmarked, following)
    }

    private val optimisticPostInputs = combine(
        optimisticPostLikes,
        optimisticBookmarks,
        optimisticCommentsByPost
    ) { likeOverrides, bookmarkOverrides, pendingComments ->
        Triple(likeOverrides, bookmarkOverrides, pendingComments)
    }

    val allPostsState: StateFlow<StreamUiState<List<Post>>> = combine(
        postBaseStreams,
        optimisticPostInputs
    ) { base, (likeOverrides, bookmarkOverrides, pendingComments) ->
        val profilesByUid = base.profiles.associateBy { it.userId }
        val enriched = base.posts.map { post ->
            val authorProfile = profilesByUid[post.userId]
            val isShowcase = post.id.startsWith("showcase_")
            val serverLiked = base.liked.contains(post.id)
            val desiredLiked = likeOverrides[post.id] ?: serverLiked
            val likeDelta = when {
                desiredLiked && !serverLiked -> 1L
                !desiredLiked && serverLiked -> -1L
                else -> 0L
            }
            val serverBookmarked = base.bookmarked.contains(post.id)
            val desiredBookmarked = bookmarkOverrides[post.id] ?: serverBookmarked
            val pendingCount = pendingComments[post.id]?.size?.toLong() ?: 0L

            post.copy(
                authorAvatarData = if (isShowcase) null else authorProfile?.avatarData,
                authorVerified = if (isShowcase) post.authorVerified else (authorProfile?.isVerified ?: post.authorVerified),
                isLiked = desiredLiked,
                likesCount = (post.likesCount + likeDelta).coerceAtLeast(0L),
                commentsCount = (post.commentsCount + pendingCount).coerceAtLeast(0L),
                isBookmarked = desiredBookmarked,
                isFollowingAuthor = post.userId == currentUserId || base.following.contains(post.userId)
            )
        }
        lastKnownEnrichedPosts.value = enriched
        StreamUiState.Success(enriched) as StreamUiState<List<Post>>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing posts", root)
            val fallbackMsg = unwrapErrorMessage(root, "Failed to observe posts")
            if (isQuotaExceeded(root)) {
                quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                if (!hasNotifiedQuotaError) {
                    hasNotifiedQuotaError = true
                    _statusNotice.value = QUOTA_FRIENDLY_MESSAGE
                }
            } else {
                _errorMessage.value = fallbackMsg
            }
            val cached = lastKnownEnrichedPosts.value
            if (cached.isNotEmpty()) {
                emit(StreamUiState.Success(cached))
            } else {
                emit(StreamUiState.Error(fallbackMsg))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StreamUiState.Loading)

    val allPosts: StateFlow<List<Post>> = allPostsState
        .map { state ->
            when (state) {
                is StreamUiState.Success -> state.data
                is StreamUiState.Loading, is StreamUiState.Error -> lastKnownEnrichedPosts.value
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val reels: StateFlow<List<Post>> = allPosts
        .map { list -> list.filter { it.isReel } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val stories: StateFlow<List<Story>> = combine(
        repository.observeStories(currentUserId)
            .resilientWithCache(cachedStories, "stories"),
        allProfiles,
        seenStoryIds
    ) { rawStories, profiles, seen ->
        val profilesByUid = profiles.associateBy { it.userId }
        rawStories
            .map { story ->
                story.copy(
                    avatarData = profilesByUid[story.userId]?.avatarData,
                    isOwnStory = story.userId == currentUserId,
                    isSeen = seen.contains(story.id)
                )
            }
            .sortedWith(
                compareByDescending<Story> { it.isOwnStory }
                    .thenBy { it.isSeen }
                    .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    .thenBy { it.id }
            )
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error combining stories", root)
            emit(cachedStories.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val activeLiveStreams: StateFlow<List<LiveStream>> = combine(
        repository.observeActiveLiveStreams(currentUserId)
            .resilientWithCache(cachedLiveStreams, "liveStreams"),
        allProfiles
    ) { streams, profiles ->
        val profilesByUid = profiles.associateBy { it.userId }
        streams.map { stream ->
            val p = profilesByUid[stream.userId]
            stream.copy(
                avatarData = p?.avatarData,
                authorVerified = p?.isVerified ?: stream.authorVerified
            )
        }
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error combining live streams", root)
            emit(cachedLiveStreams.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = combine(
        repository.observeNotifications(currentUserId)
            .resilientWithCache(cachedNotifications, "notifications"),
        followingUids
    ) { items, following ->
        items.map { item ->
            if (item.isFollowAction && item.actorUid.isNotBlank()) {
                item.copy(isFollowingBack = following.contains(item.actorUid))
            } else {
                item
            }
        }
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error combining notifications", root)
            emit(cachedNotifications.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { it.isUnread } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val messageThreads: StateFlow<List<MessageThread>> = repository.observeMessageThreads(currentUserId)
        .resilientWithCache(cachedThreads, "messageThreads")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val unreadMessageCount: StateFlow<Int> = messageThreads
        .map { list ->
            list.filter { it.isUnreadFor(currentUserId) }.sumOf { it.unreadCount.toInt() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val merchants: StateFlow<List<MerchantAccount>> = marketplaceRepository.observeMerchants(currentUserId)
        .resilientWithCache(cachedMerchants, "merchants")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val marketProducts: StateFlow<List<MarketProduct>> = combine(
        marketplaceRepository.observeProducts(currentUserId)
            .resilientWithCache(cachedProducts, "marketProducts"),
        merchants
    ) { products, merchantList ->
        val merchantByUid = merchantList.associateBy { it.userId }
        products.map { product ->
            val seller = merchantByUid[product.sellerUid]
            product.copy(sellerVerified = seller?.isVerified ?: false)
        }
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error combining marketplace products", root)
            emit(cachedProducts.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val marketOrders: StateFlow<List<MarketOrder>> = marketplaceRepository.observeOrdersForUser(currentUserId)
        .resilientWithCache(cachedOrders, "marketOrders")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val wallet: StateFlow<UserWallet?> = walletRepository.observeWallet(currentUserId)
        .resilientWithCache(cachedWallet, "wallet")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val creditTransactions: StateFlow<List<CreditTransaction>> = walletRepository.observeTransactions(currentUserId)
        .resilientWithCache(cachedCreditTransactions, "creditTransactions")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _colorScheme = MutableStateFlow(AppColorScheme.MIDNIGHT_SUNSET)
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedExploreCategory = MutableStateFlow("For You")
    val selectedExploreCategory: StateFlow<String> = _selectedExploreCategory.asStateFlow()

    val explorePosts: StateFlow<List<Post>> = combine(
        allPosts,
        _searchQuery,
        _selectedExploreCategory
    ) { posts, query, category ->
        posts.filter { post ->
            val matchesCategory = when (category) {
                "For You" -> true
                "Clips" -> post.isReel || post.isVideoPost
                else -> post.category.equals(category, ignoreCase = true)
            }
            val q = query.trim()
            val cleanHash = q.removePrefix("#")
            val cleanAt = q.removePrefix("@")
            val matchesQuery = q.isEmpty() ||
                post.authorUsername.contains(cleanAt, ignoreCase = true) ||
                post.authorDisplayName.contains(q, ignoreCase = true) ||
                post.caption.contains(q, ignoreCase = true) ||
                post.location.contains(q, ignoreCase = true) ||
                post.category.contains(q, ignoreCase = true) ||
                post.allHashtags.any { it.contains(cleanHash, ignoreCase = true) } ||
                post.allTaggedUsernames.any { it.contains(cleanAt, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    val activeCommentPostId: StateFlow<String?> = _activeCommentPostId.asStateFlow()

    val activePostForComments: StateFlow<Post?> = combine(
        _activeCommentPostId,
        allPosts
    ) { postId, posts ->
        if (postId == null) null else posts.firstOrNull { it.id == postId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val activeComments: StateFlow<List<Comment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.observeComments(currentUserId, postId),
                    allProfiles,
                    likedCommentIds,
                    optimisticCommentLikes,
                    optimisticCommentsByPost
                ) { comments, profiles, liked, likeOverrides, pendingMap ->
                    val profilesByUid = profiles.associateBy { it.userId }
                    val pending = pendingMap[postId].orEmpty()
                    val filteredPending = pending.filter { opt ->
                        comments.none { remote ->
                            remote.userId == opt.userId && remote.text == opt.text
                        }
                    }
                    (comments + filteredPending).map { comment ->
                        val serverLiked = liked.contains(comment.id)
                        val desiredLiked = likeOverrides[comment.id] ?: serverLiked
                        val likeDelta = when {
                            desiredLiked && !serverLiked -> 1L
                            !desiredLiked && serverLiked -> -1L
                            else -> 0L
                        }
                        comment.copy(
                            authorAvatarData = profilesByUid[comment.userId]?.avatarData,
                            isLiked = desiredLiked,
                            likesCount = (comment.likesCount + likeDelta).coerceAtLeast(0L)
                        )
                    }
                }.catch { error ->
                    val root = unwrapOrRethrowCancellation(error)
                    Log.w(TAG, "Error observing comments", root)
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun observePost(postId: String): Flow<Post?> = combine(
        repository.observePost(currentUserId, postId),
        allProfiles,
        likedPostIds,
        bookmarkedPostIds,
        followingUids,
        optimisticPostInputs
    ) { values ->
        val rawPost = values[0] as Post?
        @Suppress("UNCHECKED_CAST")
        val profiles = values[1] as List<UserProfile>
        @Suppress("UNCHECKED_CAST")
        val liked = values[2] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val bookmarked = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val following = values[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val overrides = values[5] as Triple<Map<String, Boolean>, Map<String, Boolean>, Map<String, List<Comment>>>

        rawPost?.let { post ->
            val authorProfile = profiles.firstOrNull { it.userId == post.userId }
            val isShowcase = post.id.startsWith("showcase_")
            val serverLiked = liked.contains(post.id)
            val desiredLiked = overrides.first[post.id] ?: serverLiked
            val likeDelta = when {
                desiredLiked && !serverLiked -> 1L
                !desiredLiked && serverLiked -> -1L
                else -> 0L
            }
            val serverBookmarked = bookmarked.contains(post.id)
            val desiredBookmarked = overrides.second[post.id] ?: serverBookmarked
            val pendingCount = overrides.third[post.id]?.size?.toLong() ?: 0L

            post.copy(
                authorAvatarData = if (isShowcase) null else authorProfile?.avatarData,
                authorVerified = if (isShowcase) post.authorVerified else (authorProfile?.isVerified ?: post.authorVerified),
                isLiked = desiredLiked,
                likesCount = (post.likesCount + likeDelta).coerceAtLeast(0L),
                commentsCount = (post.commentsCount + pendingCount).coerceAtLeast(0L),
                isBookmarked = desiredBookmarked,
                isFollowingAuthor = post.userId == currentUserId || following.contains(post.userId)
            )
        }
    }.catch { error ->
        val root = unwrapOrRethrowCancellation(error)
        Log.w(TAG, "Error observing post $postId", root)
        emit(null)
    }

    fun observeChatMessages(threadId: String): Flow<List<ChatMessage>> =
        repository.observeChatMessages(currentUserId, threadId).catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing chat messages for $threadId", root)
            emit(emptyList())
        }

    fun observeLiveStream(streamId: String): Flow<LiveStream?> = combine(
        repository.observeLiveStream(currentUserId, streamId),
        allProfiles
    ) { stream, profiles ->
        stream?.let { s ->
            val p = profiles.firstOrNull { it.userId == s.userId }
            s.copy(
                avatarData = p?.avatarData,
                authorVerified = p?.isVerified ?: s.authorVerified
            )
        }
    }.catch { error ->
        val root = unwrapOrRethrowCancellation(error)
        Log.w(TAG, "Error observing live stream $streamId", root)
        emit(null)
    }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> =
        repository.observeLiveComments(currentUserId, streamId).catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing live comments for $streamId", root)
            emit(emptyList())
        }

    fun observeLiveFrame(streamId: String): Flow<LiveFrame?> =
        repository.observeLiveFrame(currentUserId, streamId).catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing live frame for $streamId", root)
            emit(null)
        }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    fun dismissAvailableUpdate(context: Context? = null) {
        val current = _availableUpdate.value
        if (context != null && current != null) {
            AppUpdateChecker.dismissVersion(context, current.latestVersionName)
        }
        _availableUpdate.value = null
    }

    fun checkForUpdates(
        context: Context? = null,
        manual: Boolean = false,
        isPlayStoreInstall: Boolean = false
    ) {
        if (!manual) {
            if (hasAutoCheckedUpdatesInSession) return
            if (context != null && !AppUpdateChecker.shouldRunAutoCheck(context)) return
            hasAutoCheckedUpdatesInSession = true
            if (context != null) {
                AppUpdateChecker.recordAutoCheck(context)
            }
        } else if (context != null) {
            AppUpdateChecker.clearDismissedVersion(context)
        }

        viewModelScope.launch {
            AppUpdateChecker.checkForUpdate(isPlayStoreInstall = isPlayStoreInstall)
                .onSuccess { result ->
                    val candidate = result.availableUpdate
                    if (candidate != null) {
                        val isDismissed = context != null &&
                            AppUpdateChecker.isVersionDismissed(context, candidate.latestVersionName)
                        if (manual || !isDismissed) {
                            _availableUpdate.value = candidate
                        }
                    } else if (manual) {
                        _statusNotice.value = if (isPlayStoreInstall) {
                            "Momi v${result.currentVersionName} is managed and updated automatically through Google Play."
                        } else {
                            "You are on the latest Momi version (v${result.currentVersionName})."
                        }
                    }
                }
                .onFailure { error ->
                    if (manual) {
                        _errorMessage.value = error.localizedMessage ?: "Could not check releases."
                    }
                }
        }
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _aiUiState.value is AiAssistantUiState.Generating) return

        val userTurnId = "user_${UUID.randomUUID()}"
        val botTurnId = "ai_${UUID.randomUUID()}"
        _aiMessages.update {
            it + AiAssistantTurn(id = userTurnId, isUser = true, text = clean) +
                AiAssistantTurn(id = botTurnId, isUser = false, text = "")
        }
        _aiUiState.value = AiAssistantUiState.Generating

        viewModelScope.launch {
            aiAssistantRepository.streamReply(clean).collect { chunkResult ->
                chunkResult.onSuccess { partialText ->
                    _aiMessages.update { turns ->
                        turns.map { turn ->
                            if (turn.id == botTurnId) turn.copy(text = partialText, isError = false) else turn
                        }
                    }
                }.onFailure { err ->
                    val msg = err.localizedMessage ?: "Assistant is temporarily unavailable."
                    _aiMessages.update { turns ->
                        turns.map { turn ->
                            if (turn.id == botTurnId && turn.text.isBlank()) {
                                turn.copy(text = msg, isError = true)
                            } else {
                                turn
                            }
                        }
                    }
                    _aiUiState.value = AiAssistantUiState.Error(msg)
                }
            }
            if (_aiUiState.value is AiAssistantUiState.Generating) {
                _aiUiState.value = AiAssistantUiState.Idle
            }
        }
    }

    fun loadAppearance(context: android.content.Context) {
        _themePreference.value = AppearancePrefs.loadThemePreference(context)
        _colorScheme.value = AppearancePrefs.loadColorScheme(context)
        if (isOwnerAccount) {
            _adminViewEnabled.value = AppearancePrefs.loadAdminViewMode(context)
        }
    }

    fun setThemePreference(context: android.content.Context, preference: ThemePreference) {
        _themePreference.value = preference
        AppearancePrefs.saveThemePreference(context, preference)
    }

    fun setColorScheme(context: android.content.Context, scheme: AppColorScheme) {
        _colorScheme.value = scheme
        AppearancePrefs.saveColorScheme(context, scheme)
    }

    fun setAdminViewEnabled(context: Context, enabled: Boolean) {
        if (!isOwnerAccount) return
        _adminViewEnabled.value = enabled
        AppearancePrefs.saveAdminViewMode(context, enabled)
        _statusNotice.value = if (enabled) {
            "Switched to Admin View"
        } else {
            "Switched to User View"
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setExploreCategory(category: String) {
        _selectedExploreCategory.value = category
    }

    fun toggleLike(post: Post) {
        val currentProfile = fallbackProfile()
        val nextLiked = !post.isLiked
        optimisticPostLikes.update { it + (post.id to nextLiked) }
        viewModelScope.launch {
            repository.toggleLike(post, currentProfile)
                .onSuccess {
                    optimisticPostLikes.update { it - post.id }
                }
                .onFailure { error ->
                    optimisticPostLikes.update { it - post.id }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not update like")
                }
        }
    }

    fun doubleTapLike(post: Post) {
        if (post.isLiked) return
        val currentProfile = fallbackProfile()
        optimisticPostLikes.update { it + (post.id to true) }
        viewModelScope.launch {
            repository.setLiked(post, currentProfile)
                .onSuccess {
                    optimisticPostLikes.update { it - post.id }
                }
                .onFailure { error ->
                    optimisticPostLikes.update { it - post.id }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not like post")
                }
        }
    }

    fun toggleBookmark(post: Post) {
        val nextBookmarked = !post.isBookmarked
        optimisticBookmarks.update { it + (post.id to nextBookmarked) }
        viewModelScope.launch {
            repository.toggleBookmark(post)
                .onSuccess {
                    optimisticBookmarks.update { it - post.id }
                }
                .onFailure { error ->
                    optimisticBookmarks.update { it - post.id }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not update bookmark")
                }
        }
    }

    fun toggleFollow(targetUid: String, targetUsername: String, currentlyFollowing: Boolean) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val currentProfile = fallbackProfile()
        val nextFollowing = !currentlyFollowing
        optimisticFollows.update { it + (targetUid to (nextFollowing to targetUsername)) }
        viewModelScope.launch {
            repository.toggleFollow(currentProfile, targetUid, targetUsername, currentlyFollowing)
                .onSuccess {
                    optimisticFollows.update { it - targetUid }
                }
                .onFailure { error ->
                    optimisticFollows.update { it - targetUid }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not update follow state")
                }
        }
    }

    fun openComments(postId: String) {
        _activeCommentPostId.value = postId
    }

    fun closeComments() {
        _activeCommentPostId.value = null
    }

    fun submitComment(text: String) {
        val postId = _activeCommentPostId.value ?: return
        val post = allPosts.value.firstOrNull { it.id == postId }
            ?: activePostForComments.value
            ?: Post(id = postId)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val currentProfile = fallbackProfile()

        val tempComment = Comment(
            id = "pending_${UUID.randomUUID()}",
            userId = currentUserId,
            authorUsername = currentProfile.username,
            authorAvatarKey = currentProfile.avatarKey,
            authorAvatarData = currentProfile.avatarData,
            text = trimmed.take(500),
            likesCount = 0L,
            isLiked = false,
            createdAt = Timestamp.now()
        )
        optimisticCommentsByPost.update { map ->
            map + (postId to (map[postId].orEmpty() + tempComment))
        }

        viewModelScope.launch {
            repository.addComment(post, currentProfile, trimmed)
                .onSuccess {
                    optimisticCommentsByPost.update { map ->
                        val remaining = map[postId].orEmpty().filterNot { it.id == tempComment.id }
                        if (remaining.isEmpty()) map - postId else map + (postId to remaining)
                    }
                }
                .onFailure { error ->
                    optimisticCommentsByPost.update { map ->
                        val remaining = map[postId].orEmpty().filterNot { it.id == tempComment.id }
                        if (remaining.isEmpty()) map - postId else map + (postId to remaining)
                    }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not post comment")
                }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val postId = _activeCommentPostId.value ?: return
        if (comment.id.startsWith("pending_")) return
        val nextLiked = !comment.isLiked
        optimisticCommentLikes.update { it + (comment.id to nextLiked) }
        viewModelScope.launch {
            repository.toggleCommentLike(postId, comment)
                .onSuccess {
                    optimisticCommentLikes.update { it - comment.id }
                }
                .onFailure { error ->
                    optimisticCommentLikes.update { it - comment.id }
                    _errorMessage.value = unwrapErrorMessage(error, "Could not update comment like")
                }
        }
    }

    suspend fun resolveRemoteVideoUri(
        context: android.content.Context,
        collectionName: String,
        mediaId: String,
        expectedChunksCount: Long
    ): Uri? {
        if (mediaId.isBlank() || mediaId.startsWith("showcase_")) return null
        val appContext = context.applicationContext
        MediaAssets.resolveCachedVideoUri(appContext, mediaId)?.let { return it }

        val key = "${collectionName}_$mediaId"
        val deferred = videoDownloadMutex.withLock {
            inFlightVideoDownloads[key] ?: viewModelScope.async(Dispatchers.IO) {
                try {
                    val chunks = repository.fetchVideoChunks(
                        collectionName = collectionName,
                        mediaId = mediaId,
                        expectedCount = expectedChunksCount
                    )
                    if (chunks.isEmpty()) {
                        null
                    } else {
                        MediaAssets.assembleChunksToCacheFile(appContext, mediaId, chunks)
                    }
                } finally {
                    videoDownloadMutex.withLock {
                        inFlightVideoDownloads.remove(key)
                    }
                }
            }.also { created ->
                inFlightVideoDownloads[key] = created
            }
        }
        return deferred.await()
    }

    fun createPost(
        context: Context,
        imageKey: String,
        localImagePath: String?,
        localExtraImagePaths: List<String> = emptyList(),
        mediaType: String,
        localVideoPath: String?,
        videoDurationMs: Long,
        audioTitle: String,
        localAudioPath: String?,
        filterTag: String,
        aspectRatio: String = "1:1",
        taggedUsernames: List<String> = emptyList(),
        hashtags: List<String> = emptyList(),
        caption: String,
        location: String,
        category: String,
        isReel: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        val extraPaths = localExtraImagePaths.filter { it.isNotBlank() }.take(4)
        val mergedHashtags = MediaAssets.extractHashtags(caption, hashtags)
        val mergedTags = MediaAssets.extractMentions(caption, taggedUsernames)
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodePrimaryPostImage(localImagePath, hasExtraSlides = extraPaths.isNotEmpty())
            }
            val encodedExtras = if (mediaType == "IMAGE" && extraPaths.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    extraPaths.mapNotNull { MediaAssets.encodeCarouselImageToBase64(it) }
                }
            } else {
                emptyList()
            }
            val encodedAudio = if (!localAudioPath.isNullOrBlank()) {
                StudioMusicCatalog.encodeAudioForFirestore(context.applicationContext, localAudioPath)
            } else {
                null
            }
            val videoPayload = if (mediaType == "VIDEO" && !localVideoPath.isNullOrBlank()) {
                MediaAssets.prepareVideoPayloadForFirestore(context.applicationContext, localVideoPath)
            } else {
                null
            }
            if (mediaType == "VIDEO" && (videoPayload == null || !videoPayload.isReady)) {
                _errorMessage.value = videoPayload?.failureReason
                    ?: "Could not prepare video for cloud sync. Please try a shorter clip."
                onComplete(false)
                return@launch
            }
            repository.createPost(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
                localExtraImagePaths = extraPaths,
                extraImageDatas = encodedExtras,
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                videoData = videoPayload?.inlineVideoData,
                videoChunks = videoPayload?.videoChunks.orEmpty(),
                videoDurationMs = videoDurationMs,
                audioTitle = audioTitle,
                localAudioPath = localAudioPath,
                audioData = encodedAudio,
                filterTag = filterTag,
                aspectRatio = aspectRatio,
                taggedUsernames = mergedTags,
                hashtags = mergedHashtags,
                caption = caption,
                location = location,
                category = category,
                isReel = isReel
            ).onSuccess {
                onComplete(true)
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not publish post")
                onComplete(false)
            }
        }
    }

    fun createStory(
        context: Context,
        imageKey: String,
        localImagePath: String?,
        mediaType: String,
        localVideoPath: String?,
        videoDurationMs: Long,
        audioTitle: String,
        localAudioPath: String?,
        caption: String,
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            val encodedAudio = if (!localAudioPath.isNullOrBlank()) {
                StudioMusicCatalog.encodeAudioForFirestore(context.applicationContext, localAudioPath)
            } else {
                null
            }
            val videoPayload = if (mediaType == "VIDEO" && !localVideoPath.isNullOrBlank()) {
                MediaAssets.prepareVideoPayloadForFirestore(context.applicationContext, localVideoPath)
            } else {
                null
            }
            if (mediaType == "VIDEO" && (videoPayload == null || !videoPayload.isReady)) {
                _errorMessage.value = videoPayload?.failureReason
                    ?: "Could not prepare story video for cloud sync. Please try a shorter clip."
                onComplete(false)
                return@launch
            }
            repository.createStory(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                videoData = videoPayload?.inlineVideoData,
                videoChunks = videoPayload?.videoChunks.orEmpty(),
                videoDurationMs = videoDurationMs,
                audioTitle = audioTitle,
                localAudioPath = localAudioPath,
                audioData = encodedAudio,
                caption = caption
            ).onSuccess {
                onComplete(true)
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not share story")
                onComplete(false)
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not delete post")
            }
        }
    }

    fun markStorySeen(storyId: String) {
        viewModelScope.launch {
            repository.markStorySeen(storyId)
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        category: String,
        bio: String,
        website: String,
        pickedAvatarLocalPath: String? = null,
        removeAvatar: Boolean = false
    ) {
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            val nextAvatarData = when {
                removeAvatar -> null
                !pickedAvatarLocalPath.isNullOrBlank() -> withContext(Dispatchers.IO) {
                    MediaAssets.encodeAvatarFileToBase64(pickedAvatarLocalPath)
                } ?: currentProfile.avatarData
                else -> currentProfile.avatarData
            }
            repository.updateProfile(
                current = currentProfile,
                displayName = displayName,
                username = username,
                category = category,
                bio = bio,
                website = website,
                avatarData = nextAvatarData
            ).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update profile")
            }
        }
    }

    fun setUserVerified(targetUid: String, verified: Boolean) {
        if (!isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to manage verified checkmarks."
            return
        }
        viewModelScope.launch {
            repository.setUserVerified(targetUid, verified).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update verified badge")
            }
        }
    }

    fun assignTeamMemberRole(
        targetUid: String,
        role: String,
        scopes: List<String>
    ) {
        if (!isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to appoint team roles."
            return
        }
        if (targetUid == currentUserId) {
            _errorMessage.value = "Your owner account always retains Admin authority."
            return
        }
        viewModelScope.launch {
            repository.setUserRoleAndScopes(
                targetUid = targetUid,
                role = role,
                scopes = scopes,
                adminEmail = initialEmail ?: "syali6511@gmail.com"
            ).onSuccess {
                _statusNotice.value = if (role == MomiRoles.USER) {
                    "Team role removed — user restored to regular member."
                } else {
                    "Updated team member to ${MomiRoles.labelOf(role)} (${scopes.size} duties)."
                }
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not update team member role")
            }
        }
    }

    fun submitContentReport(
        targetType: String,
        targetId: String,
        targetAuthorUid: String,
        targetAuthorUsername: String,
        targetPreviewText: String,
        category: String,
        reasonDetail: String
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.submitContentReport(
                reporterProfile = currentProfile,
                targetType = targetType,
                targetId = targetId,
                targetAuthorUid = targetAuthorUid,
                targetAuthorUsername = targetAuthorUsername,
                targetPreviewText = targetPreviewText,
                category = category,
                reasonDetail = reasonDetail
            ).onSuccess {
                _statusNotice.value = "Report submitted to Momi Safety Team."
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not submit report")
            }
        }
    }

    fun resolveContentReport(
        report: ContentReport,
        removeContent: Boolean,
        resolutionNote: String
    ) {
        val allowed = isAdminModeActive.value || currentModerationScopes.value.contains(report.category)
        if (!allowed) {
            _errorMessage.value = "You are not enrolled in ${ModerationScopes.labelOf(report.category)} moderation."
            return
        }
        val reviewer = fallbackProfile()
        val role = currentUserRole.value
        viewModelScope.launch {
            if (removeContent) {
                when (report.targetType.uppercase()) {
                    "POST" -> {
                        if (!report.targetId.startsWith("showcase_")) {
                            val delResult = repository.deletePost(report.targetId)
                            if (delResult.isFailure) {
                                val err = delResult.exceptionOrNull()
                                Log.w(TAG, "Post delete before resolving report failed/already gone", err)
                            }
                        }
                    }
                    "PRODUCT" -> {
                        val delResult = marketplaceRepository.deleteProduct(report.targetId)
                        if (delResult.isFailure) {
                            Log.w(TAG, "Product delete failed", delResult.exceptionOrNull())
                        }
                    }
                }
            }
            repository.resolveContentReport(
                reportId = report.id,
                status = if (removeContent) "REMOVED" else "DISMISSED",
                reviewerProfile = reviewer,
                reviewerRole = role,
                resolutionNote = resolutionNote.ifBlank {
                    if (removeContent) "Removed violating content" else "Reviewed and cleared"
                }
            ).onSuccess {
                _statusNotice.value = if (removeContent) "Content removed & report closed." else "Report dismissed."
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not update report status")
            }
        }
    }

    fun toggleNotificationFollow(notification: NotificationItem) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.toggleNotificationFollow(currentProfile, notification).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update follow state")
            }
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markNotificationsRead()
        }
    }

    fun startDirectChat(peerProfile: UserProfile, onOpened: (MessageThread) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.openOrCreateThread(currentProfile, peerProfile)
                .onSuccess(onOpened)
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not open chat")
                }
        }
    }

    fun startDirectChatWithUid(targetUid: String, onOpened: (MessageThread) -> Unit) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val existingProfile = allProfiles.value.firstOrNull { it.userId == targetUid }
            ?: merchants.value.firstOrNull { it.userId == targetUid }?.let { m ->
                UserProfile(
                    id = m.userId,
                    userId = m.userId,
                    username = m.ownerUsername.ifBlank { "merchant" },
                    displayName = m.storeName.ifBlank { m.ownerUsername },
                    avatarKey = m.avatarKey,
                    avatarData = m.avatarData,
                    isVerified = m.isVerified
                )
            }
            ?: UserProfile(
                id = targetUid,
                userId = targetUid,
                username = "seller",
                displayName = "Momi Seller",
                avatarKey = MediaAssets.avatarKeyForUid(targetUid)
            )
        startDirectChat(existingProfile, onOpened)
    }

    fun sendMessage(thread: MessageThread, text: String) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.sendMessage(thread, currentProfile, text).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not send message")
            }
        }
    }

    fun sendPhotoMessage(thread: MessageThread, localImagePath: String) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            if (encoded.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = "Could not compress photo for chat."
                return@launch
            }
            repository.sendMediaMessage(
                thread = thread,
                senderProfile = currentProfile,
                messageType = "IMAGE",
                caption = "Photo",
                imageData = encoded
            ).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not send photo")
            }
            _isSendingChatMedia.value = false
        }
    }

    fun sendVideoMessage(context: Context, thread: MessageThread, localVideoPath: String, durationMs: Long) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val videoResult = MediaAssets.prepareCompactVideoForChat(context.applicationContext, localVideoPath)
            val encoded = videoResult.getOrNull()
            if (encoded.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = videoResult.exceptionOrNull()?.localizedMessage
                    ?: "Video clip is too large for chat."
                return@launch
            }
            repository.sendMediaMessage(
                thread = thread,
                senderProfile = currentProfile,
                messageType = "VIDEO",
                caption = "Video clip",
                videoData = encoded,
                videoDurationMs = durationMs
            ).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not send video")
            }
            _isSendingChatMedia.value = false
        }
    }

    fun sendVoiceMessage(context: Context, thread: MessageThread, localAudioPath: String, durationMs: Long) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val encoded = StudioMusicCatalog.encodeAudioForFirestore(context.applicationContext, localAudioPath)
            runCatching { File(localAudioPath).delete() }
            if (encoded.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = "Voice clip could not be encoded."
                return@launch
            }
            repository.sendMediaMessage(
                thread = thread,
                senderProfile = currentProfile,
                messageType = "AUDIO",
                caption = "Voice message",
                audioData = encoded,
                audioDurationMs = durationMs
            ).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not send voice note")
            }
            _isSendingChatMedia.value = false
        }
    }

    fun createOrUpdateMerchant(
        storeName: String,
        tagline: String,
        category: String,
        location: String,
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            marketplaceRepository.createOrUpdateMerchant(
                profile = currentProfile,
                storeName = storeName,
                tagline = tagline,
                category = category,
                location = location
            ).onSuccess {
                _statusNotice.value = "Merchant storefront saved!"
                onComplete(true)
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not save merchant account")
                onComplete(false)
            }
        }
    }

    fun setMerchantVerified(merchantUid: String, verified: Boolean) {
        if (!isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to verify merchant storefronts."
            return
        }
        viewModelScope.launch {
            marketplaceRepository.setMerchantVerified(
                targetMerchantUid = merchantUid,
                verified = verified,
                adminEmail = initialEmail ?: "syali6511@gmail.com"
            ).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update merchant verification")
            }
        }
    }

    fun setProductFeatured(productId: String, featured: Boolean) {
        if (!canCurateMarketplace.value) {
            _errorMessage.value = "Only Admin or enrolled Editors can feature deals."
            return
        }
        viewModelScope.launch {
            marketplaceRepository.setProductFeatured(productId, featured)
                .onSuccess {
                    _statusNotice.value = if (featured) "Added to Editor's Picks!" else "Removed from Editor's Picks."
                }
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not update featured status")
                }
        }
    }

    fun publishMarketProduct(
        context: Context,
        merchant: MerchantAccount,
        title: String,
        description: String,
        category: String,
        priceUsd: Double,
        originalPriceUsd: Double,
        freeShipping: Boolean,
        imagePaths: List<String>,
        localVideoPath: String?,
        videoDurationMs: Long,
        stockCount: Long,
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        val cleanPaths = imagePaths.filter { it.isNotBlank() }.take(5)
        viewModelScope.launch {
            val primaryImageData = withContext(Dispatchers.IO) {
                MediaAssets.encodePrimaryPostImage(cleanPaths.firstOrNull(), hasExtraSlides = cleanPaths.size > 1)
            }
            val extraImageDatas = withContext(Dispatchers.IO) {
                cleanPaths.drop(1).mapNotNull { MediaAssets.encodeCarouselImageToBase64(it) }
            }
            val videoPayload = if (!localVideoPath.isNullOrBlank()) {
                MediaAssets.prepareVideoPayloadForFirestore(context.applicationContext, localVideoPath)
            } else {
                null
            }
            if (!localVideoPath.isNullOrBlank() && (videoPayload == null || !videoPayload.isReady)) {
                _errorMessage.value = videoPayload?.failureReason ?: "Could not prepare product video."
                onComplete(false)
                return@launch
            }
            marketplaceRepository.publishProduct(
                profile = currentProfile,
                merchant = merchant,
                title = title,
                description = description,
                category = category,
                priceUsd = priceUsd,
                originalPriceUsd = originalPriceUsd,
                freeShipping = freeShipping,
                imageData = primaryImageData,
                extraImageDatas = extraImageDatas,
                videoData = videoPayload?.inlineVideoData,
                videoChunks = videoPayload?.videoChunks.orEmpty(),
                videoDurationMs = videoDurationMs,
                stockCount = stockCount
            ).onSuccess {
                _statusNotice.value = "Listing published to Momi Marketplace!"
                onComplete(true)
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not publish product listing")
                onComplete(false)
            }
        }
    }

    fun deleteMarketProduct(productId: String) {
        viewModelScope.launch {
            marketplaceRepository.deleteProduct(productId).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not delete listing")
            }
        }
    }

    fun placeMarketOrder(product: MarketProduct, shippingAddress: String, onComplete: (Boolean) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            marketplaceRepository.placeOrder(
                buyerProfile = currentProfile,
                product = product,
                shippingAddress = shippingAddress
            ).onSuccess {
                _statusNotice.value = "Order placed with ${product.storeName}!"
                onComplete(true)
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not place order")
                onComplete(false)
            }
        }
    }

    fun updateMarketOrderStatus(orderId: String, nextStatus: String) {
        viewModelScope.launch {
            marketplaceRepository.updateOrderStatus(orderId, nextStatus).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update order status")
            }
        }
    }

    fun clearThreadUnread(thread: MessageThread) {
        viewModelScope.launch {
            repository.clearThreadUnread(thread)
        }
    }

    fun recordPostView(postId: String) {
        if (postId.isBlank() || postId.startsWith("showcase_")) return
        if (System.currentTimeMillis() < quotaCooldownUntilMs) return
        val post = allPosts.value.firstOrNull { it.id == postId }
        if (post != null && post.userId == currentUserId) return
        val shouldRecord = synchronized(recordedPostViewIds) {
            recordedPostViewIds.add(postId)
        }
        if (!shouldRecord) return
        viewModelScope.launch {
            repository.incrementPostView(postId).onFailure { error ->
                if (isQuotaExceeded(error)) {
                    quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                }
                Log.w(TAG, "Failed to record post view for $postId", error)
            }
        }
    }

    fun startLiveStream(title: String, category: String = "Live", onStarted: (Result<String>) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val result = repository.startLiveStream(
                profile = currentProfile,
                title = title,
                category = category
            )
            result.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not start live broadcast")
            }
            onStarted(result)
        }
    }

    fun endLiveStream(streamId: String = currentUserId, onEnded: () -> Unit = {}) {
        viewModelScope.launch {
            repository.endLiveStream(streamId).onFailure { error ->
                Log.w(TAG, "Error ending live stream $streamId", error)
            }
            onEnded()
        }
    }

    fun publishLiveFrame(
        streamId: String,
        seq: Long,
        frameData: String,
        cameraEnabled: Boolean,
        micMuted: Boolean
    ) {
        if (System.currentTimeMillis() < quotaCooldownUntilMs) return
        viewModelScope.launch {
            repository.publishLiveFrame(
                streamId = streamId,
                seq = seq,
                frameData = frameData,
                cameraEnabled = cameraEnabled,
                micMuted = micMuted
            ).onFailure { error ->
                if (isQuotaExceeded(error)) {
                    quotaCooldownUntilMs = System.currentTimeMillis() + QUOTA_RETRY_COOLDOWN_MS
                }
                Log.w(TAG, "Live frame publish skipped", error)
            }
        }
    }

    fun joinLiveStream(streamId: String, onJoined: (Boolean) -> Unit = {}) {
        if (streamId.isBlank() || streamId == currentUserId) {
            onJoined(false)
            return
        }
        viewModelScope.launch {
            val joined = repository.joinLiveStream(streamId).getOrDefault(false)
            onJoined(joined)
        }
    }

    fun leaveLiveStream(streamId: String) {
        if (streamId.isBlank() || streamId == currentUserId) return
        viewModelScope.launch {
            repository.leaveLiveStream(streamId)
        }
    }

    fun sendLiveHeart(streamId: String) {
        viewModelScope.launch {
            repository.sendLiveHeart(streamId)
        }
    }

    fun sendLiveComment(streamId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.sendLiveComment(streamId, currentProfile, trimmed).onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not send live comment")
            }
        }
    }

    fun completeCreditPurchase(
        pkg: CreditPackageOption,
        paymentMethod: String,
        paymentRef: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            walletRepository.recordCreditTopUp(
                profile = currentProfile,
                pkg = pkg,
                paymentMethod = paymentMethod,
                paymentRef = paymentRef
            ).onSuccess {
                _statusNotice.value = "Added +${pkg.credits} Momi credits to your account!"
                onComplete(true)
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not add credits to wallet")
                onComplete(false)
            }
            _isProcessingWalletAction.value = false
        }
    }

    fun handlePlayPurchaseCredited(
        productId: String,
        orderId: String,
        purchaseToken: String
    ) {
        val pkg = MomiCreditCatalog.packageByProductId(productId) ?: return
        completeCreditPurchase(
            pkg = pkg,
            paymentMethod = CreditTransaction.METHOD_GOOGLE_PLAY,
            paymentRef = orderId.ifBlank { purchaseToken }
        )
    }

    fun sendGift(
        recipientUid: String,
        recipientUsername: String,
        gift: LiveGiftItem,
        streamId: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            walletRepository.sendGift(
                senderProfile = currentProfile,
                recipientUid = recipientUid,
                recipientUsername = recipientUsername,
                gift = gift,
                streamId = streamId
            ).onSuccess {
                _statusNotice.value =
                    "Sent ${gift.emojiLabel} ${gift.name} (-${gift.creditsCost} cr) to @$recipientUsername!"
                onComplete(true)
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not send gift")
                onComplete(false)
            }
            _isProcessingWalletAction.value = false
        }
    }

    fun unlockAccountPerk(
        perk: AccountPerkItem,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            walletRepository.unlockPerk(currentProfile, perk)
                .onSuccess {
                    _statusNotice.value = "Unlocked ${perk.title}!"
                    onComplete(true)
                }
                .onFailure { error ->
                    _errorMessage.value = unwrapErrorMessage(error, "Could not unlock perk")
                    onComplete(false)
                }
            _isProcessingWalletAction.value = false
        }
    }

    fun showInfoNotice(message: String) {
        _statusNotice.value = message
    }

    private fun fallbackProfile(): UserProfile {
        profile.value?.let { return it }
        cachedProfile.value?.let { return it }
        return fallbackProfileBase()
    }

    private fun fallbackProfileBase(): UserProfile {
        val fallbackName = initialDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: "Momi Creator"
        val fallbackUsername = initialEmail?.substringBefore("@")
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: "momi.creator"
        return UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = fallbackUsername.take(50),
            displayName = fallbackName.take(80),
            avatarKey = MediaAssets.avatarKeyForUid(currentUserId)
        )
    }

    private fun isQuotaExceeded(error: Throwable?): Boolean {
        if (error == null) return false
        return generateSequence(error) { it.cause }.any { cause ->
            (cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) ||
                cause.message?.contains("quota", ignoreCase = true) == true ||
                cause.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true
        }
    }

    private fun unwrapOrRethrowCancellation(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    private fun unwrapErrorMessage(error: Throwable, fallback: String): String {
        if (isQuotaExceeded(error)) {
            return QUOTA_FRIENDLY_MESSAGE
        }
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return firestoreError?.localizedMessage ?: error.localizedMessage ?: fallback
    }

    private data class PostHydrationInputs(
        val posts: List<Post>,
        val profiles: List<UserProfile>,
        val liked: Set<String>,
        val bookmarked: Set<String>,
        val following: Set<String>
    )

    private companion object {
        const val TAG = "MomiVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val QUOTA_RETRY_COOLDOWN_MS = 120_000L
        const val QUOTA_FRIENDLY_MESSAGE =
            "Daily Firebase Spark quota temporarily reached — cached posts remain visible. Quota resets daily or after upgrading to Blaze."
    }
}
