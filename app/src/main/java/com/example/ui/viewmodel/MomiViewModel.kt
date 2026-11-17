package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MomiAiAssistantRepository
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.MessageThread
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.MomiFirestoreRepository
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
    val currentUserId: String,
    private val initialDisplayName: String?,
    private val initialEmail: String?,
    private val aiAssistantRepository: MomiAiAssistantRepository = MomiAiAssistantRepository()
) : ViewModel() {

    val isAdminUser: Boolean = initialEmail?.trim().equals("syali6511@gmail.com", ignoreCase = true)

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
                _errorMessage.value = unwrapErrorMessage(error, "Failed to initialize Momi profile")
            }
        }
        checkForUpdates(manual = false)
    }

    private val rawFollows: StateFlow<List<FollowEdge>> = repository.observeFollows(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing follows", root)
            emit(emptyList())
        }
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
        repository.observeAllProfiles(currentUserId),
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
            Log.w(TAG, "Error observing creators", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val profileState: StateFlow<StreamUiState<UserProfile?>> = combine(
        repository.observeProfile(currentUserId),
        follows
    ) { rawProfile, allFollows ->
        val hydrated = rawProfile?.let { p ->
            p.copy(
                followersCount = allFollows.count { it.targetUid == currentUserId }.toLong(),
                followingCount = allFollows.count { it.followerUid == currentUserId }.toLong()
            )
        }
        StreamUiState.Success(hydrated) as StreamUiState<UserProfile?>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing profile", root)
            emit(StreamUiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StreamUiState.Loading)

    val profile: StateFlow<UserProfile?> = profileState
        .map { (it as? StreamUiState.Success)?.data }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val likedPostIds: StateFlow<Set<String>> = repository.observeLikedPostIds(currentUserId)
        .catch { error ->
            unwrapOrRethrowCancellation(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val bookmarkedPostIds: StateFlow<Set<String>> = repository.observeBookmarkedPostIds(currentUserId)
        .catch { error ->
            unwrapOrRethrowCancellation(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val seenStoryIds: StateFlow<Set<String>> = repository.observeSeenStoryIds(currentUserId)
        .catch { error ->
            unwrapOrRethrowCancellation(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val likedCommentIds: StateFlow<Set<String>> = repository.observeLikedCommentIds(currentUserId)
        .catch { error ->
            unwrapOrRethrowCancellation(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val postBaseStreams = combine(
        repository.observeAllPosts(currentUserId),
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
        StreamUiState.Success(enriched) as StreamUiState<List<Post>>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing posts", root)
            _errorMessage.value = root.message ?: "Failed to observe posts"
            emit(StreamUiState.Error(root.message ?: "Failed to observe posts"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StreamUiState.Loading)

    val allPosts: StateFlow<List<Post>> = allPostsState
        .map { (it as? StreamUiState.Success)?.data.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val reels: StateFlow<List<Post>> = allPosts
        .map { list -> list.filter { it.isReel } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val stories: StateFlow<List<Story>> = combine(
        repository.observeStories(currentUserId),
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
            Log.w(TAG, "Error observing stories", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val activeLiveStreams: StateFlow<List<LiveStream>> = combine(
        repository.observeActiveLiveStreams(currentUserId),
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
            Log.w(TAG, "Error observing live streams", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = combine(
        repository.observeNotifications(currentUserId),
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
            Log.w(TAG, "Error observing notifications", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { it.isUnread } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val messageThreads: StateFlow<List<MessageThread>> = repository.observeMessageThreads(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing message threads", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val unreadMessageCount: StateFlow<Int> = messageThreads
        .map { list ->
            list.filter { it.isUnreadFor(currentUserId) }.sumOf { it.unreadCount.toInt() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val merchants: StateFlow<List<MerchantAccount>> = marketplaceRepository.observeMerchants(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing merchants", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val marketProducts: StateFlow<List<MarketProduct>> = combine(
        marketplaceRepository.observeProducts(currentUserId),
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
            Log.w(TAG, "Error observing marketplace products", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val marketOrders: StateFlow<List<MarketOrder>> = marketplaceRepository.observeOrdersForUser(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing marketplace orders", root)
            emit(emptyList())
        }
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
            val matchesQuery = q.isEmpty() ||
                post.authorUsername.contains(q, ignoreCase = true) ||
                post.authorDisplayName.contains(q, ignoreCase = true) ||
                post.caption.contains(q, ignoreCase = true) ||
                post.location.contains(q, ignoreCase = true) ||
                post.category.contains(q, ignoreCase = true)
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

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    fun dismissAvailableUpdate() {
        _availableUpdate.value = null
    }

    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            AppUpdateChecker.checkForUpdate()
                .onSuccess { result ->
                    if (result.availableUpdate != null) {
                        _availableUpdate.value = result.availableUpdate
                    } else if (manual) {
                        _statusNotice.value = "You are on the latest Momi version (v${result.currentVersionName})."
                    }
                }
                .onFailure { error ->
                    if (manual) {
                        _errorMessage.value = error.localizedMessage ?: "Could not check GitHub releases."
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
    }

    fun setThemePreference(context: android.content.Context, preference: ThemePreference) {
        _themePreference.value = preference
        AppearancePrefs.saveThemePreference(context, preference)
    }

    fun setColorScheme(context: android.content.Context, scheme: AppColorScheme) {
        _colorScheme.value = scheme
        AppearancePrefs.saveColorScheme(context, scheme)
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
        caption: String,
        location: String,
        category: String,
        isReel: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        val extraPaths = localExtraImagePaths.filter { it.isNotBlank() }.take(4)
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
        if (!isAdminUser) {
            _errorMessage.value = "Only the Momi owner can manage verified checkmarks."
            return
        }
        viewModelScope.launch {
            repository.setUserVerified(targetUid, verified).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update verified badge")
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
        if (!isAdminUser) {
            _errorMessage.value = "Only the Momi owner can verify merchant storefronts."
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
        if (postId.isBlank()) return
        val shouldRecord = synchronized(recordedPostViewIds) {
            recordedPostViewIds.add(postId)
        }
        if (!shouldRecord) return
        viewModelScope.launch {
            repository.incrementPostView(postId).onFailure { error ->
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

    fun joinLiveStream(streamId: String) {
        if (streamId.isBlank() || streamId == currentUserId) return
        viewModelScope.launch {
            repository.joinLiveStream(streamId)
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

    private fun fallbackProfile(): UserProfile {
        profile.value?.let { return it }
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

    private fun unwrapOrRethrowCancellation(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    private fun unwrapErrorMessage(error: Throwable, fallback: String): String {
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
    }
}
