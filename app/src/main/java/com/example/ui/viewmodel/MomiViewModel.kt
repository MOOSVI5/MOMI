package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.GoogleAiHealthState
import com.example.ai.GoogleAiStatus
import com.example.ai.MomiAiAssistantRepository
import com.example.data.model.AccountPerkItem
import com.example.data.model.AdMonetizationConfig
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
import com.example.data.model.MomiGroup
import com.example.data.model.MomiPage
import com.example.data.model.MomiRoles
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PostBoostOption
import com.example.data.model.ShowcaseAccount
import com.example.data.model.ShowcaseCatalog
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.data.model.sortFeedWithBoostPriority
import com.example.data.supabase.MomiSupabaseClient
import com.example.data.supabase.MomiSupabaseSchema
import com.example.data.supabase.SupabaseAuthSession
import com.example.data.supabase.SupabasePrimaryBackend
import com.example.data.supabase.SupabaseRealtimeClient
import com.example.data.supabase.SupabaseRealtimeConnectionState
import com.example.data.supabase.SupabaseRealtimeEvent
import com.example.ui.components.AiAssistantTurn
import com.example.ui.components.AiAssistantUiState
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.ThemePreference
import com.example.util.AppUpdateChecker
import com.example.util.AvailableAppUpdate
import com.example.util.MediaAssets
import com.example.util.StudioMusicCatalog
import com.example.data.model.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MomiViewModel(
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

    private val _isShowcaseActionBusy = MutableStateFlow(false)
    val isShowcaseActionBusy: StateFlow<Boolean> = _isShowcaseActionBusy.asStateFlow()

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

    private val _userGeminiApiKey = MutableStateFlow("")
    val userGeminiApiKey: StateFlow<String> = _userGeminiApiKey.asStateFlow()

    private val _aiStatus = MutableStateFlow(aiAssistantRepository.currentConfigStatus())
    val aiStatus: StateFlow<GoogleAiStatus> = _aiStatus.asStateFlow()

    private val _isCheckingAiStatus = MutableStateFlow(false)
    val isCheckingAiStatus: StateFlow<Boolean> = _isCheckingAiStatus.asStateFlow()

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
    private val cachedMonetizationConfig = MutableStateFlow(AdMonetizationConfig())
    private val cachedLikedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedBookmarkedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedSeenStoryIds = MutableStateFlow<Set<String>>(emptySet())
    private val cachedLikedCommentIds = MutableStateFlow<Set<String>>(emptySet())

    private val _isProcessingWalletAction = MutableStateFlow(false)
    val isProcessingWalletAction: StateFlow<Boolean> = _isProcessingWalletAction.asStateFlow()

    private val supabaseClient = MomiSupabaseClient()
    private val supabaseBackend = SupabasePrimaryBackend(supabaseClient)
    private val realtimeClient = SupabaseRealtimeClient()

    private val supaHasLoadedFeed = MutableStateFlow(false)
    private val supaFollows = MutableStateFlow<List<FollowEdge>>(emptyList())
    private val supaProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    private val supaPosts = MutableStateFlow<List<Post>>(emptyList())
    private val supaStories = MutableStateFlow<List<Story>>(emptyList())
    private val supaLiveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    private val supaNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    private val supaThreads = MutableStateFlow<List<MessageThread>>(emptyList())
    private val supaMerchants = MutableStateFlow<List<MerchantAccount>>(emptyList())
    private val supaProducts = MutableStateFlow<List<MarketProduct>>(emptyList())
    private val supaOrders = MutableStateFlow<List<MarketOrder>>(emptyList())
    private val supaContentReports = MutableStateFlow<List<ContentReport>>(emptyList())
    private val supaWallet = MutableStateFlow<UserWallet?>(null)
    private val supaCreditTransactions = MutableStateFlow<List<CreditTransaction>>(emptyList())
    private val supaMonetizationConfig = MutableStateFlow<AdMonetizationConfig?>(null)
    private val supaLikedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaBookmarkedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaSeenStoryIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaLikedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaPostLikersByPostId = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val postLikersByPostId: StateFlow<Map<String, List<String>>> = supaPostLikersByPostId.asStateFlow()
    private val supaPostViewersByPostId = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val postViewersByPostId: StateFlow<Map<String, List<String>>> = supaPostViewersByPostId.asStateFlow()
    private val supaCommentsByPost = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    private val supaChatMessagesByThread = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val supaLiveFrameByStream = MutableStateFlow<Map<String, LiveFrame>>(emptyMap())
    private val supaLiveCommentsByStream = MutableStateFlow<Map<String, List<LiveComment>>>(emptyMap())
    private val supaPages = MutableStateFlow<List<MomiPage>>(emptyList())
    private val supaPageFollowedIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaGroups = MutableStateFlow<List<MomiGroup>>(emptyList())
    private val supaGroupJoinedIds = MutableStateFlow<Set<String>>(emptySet())
    private val supaShowcaseAccounts = MutableStateFlow<List<ShowcaseAccount>>(emptyList())
    private val _dismissedAnnouncementId = MutableStateFlow<String?>(null)
    val dismissedAnnouncementId: StateFlow<String?> = _dismissedAnnouncementId.asStateFlow()

    private val _supabaseStatus = MutableStateFlow<String?>(
        if (supabaseClient.isConfigured()) {
            "Connecting Supabase Realtime WebSocket..."
        } else {
            "Waiting for SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets."
        }
    )
    val supabaseStatus: StateFlow<String?> = _supabaseStatus.asStateFlow()

    private val _isSupabaseBusy = MutableStateFlow(false)
    val isSupabaseBusy: StateFlow<Boolean> = _isSupabaseBusy.asStateFlow()

    val isSupabaseConfigured: Boolean
        get() = supabaseClient.isConfigured()

    @Volatile private var hasAutoCheckedUpdatesInSession = false
    @Volatile private var hasProbedAiStatusInSession = false
    @Volatile private var knownPlayStoreInstall = false
    @Volatile private var cachedAppContext: Context? = null
    @Volatile private var activeChatThreadId: String? = null
    @Volatile private var activeLiveRoomId: String? = null
    private var realtimeRefreshJob: Job? = null
    private var realtimeListenersJob: Job? = null
    @Volatile private var registeredRealtimeUid: String? = null

    // Optimistic UI overlays + 12s pending mutation protection so background sync NEVER reverts recent edits
    private val optimisticPostLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticBookmarks = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticFollows = MutableStateFlow<Map<String, Pair<Boolean, String>>>(emptyMap())
    private val optimisticCommentLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticCommentsByPost = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    private val pendingLikes = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val pendingBookmarks = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val pendingFollowStates = ConcurrentHashMap<String, Pair<FollowEdge?, Long>>()
    private val pendingMinViews = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val pendingMinLikes = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val pendingCreatedPosts = ConcurrentHashMap<String, Pair<Post, Long>>()
    private val pendingDeletedPostIds = ConcurrentHashMap<String, Long>()
    @Volatile private var pendingProfileOverride: Pair<UserProfile, Long>? = null
    private val recordedPostViewIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            if (supabaseClient.isConfigured()) {
                // Register Realtime DB listeners immediately on login without waiting for HTTP seed/fetch
                registerDatabaseListenersOnLogin(MomiSupabaseClient.activeSession)

                launch {
                    refreshFromSupabase(forceEnsureProfile = true)
                }

                launch {
                    realtimeClient.connectionState.collect { rtStatus ->
                        _supabaseStatus.value = when (rtStatus.state) {
                            SupabaseRealtimeConnectionState.CONNECTED ->
                                "Supabase Realtime Live (WebSocket) • Posts, Comments & Likes synced."
                            SupabaseRealtimeConnectionState.CONNECTING ->
                                "Connecting Supabase Realtime WebSocket..."
                            SupabaseRealtimeConnectionState.RECONNECTING ->
                                rtStatus.detail
                            SupabaseRealtimeConnectionState.DISCONNECTED ->
                                "Supabase Cloud Sync active — Postgres & Storage primary."
                        }
                    }
                }

                launch {
                    MomiSupabaseClient.sessionFlow.collect { updatedSession ->
                        if (updatedSession == null) {
                            registeredRealtimeUid = null
                            realtimeListenersJob?.cancel()
                            realtimeListenersJob = null
                            realtimeClient.disconnect()
                        } else {
                            registerDatabaseListenersOnLogin(updatedSession)
                        }
                    }
                }

                launch {
                    while (true) {
                        val intervalMs = if (
                            realtimeClient.connectionState.value.state == SupabaseRealtimeConnectionState.CONNECTED
                        ) {
                            15_000L
                        } else {
                            5_000L
                        }
                        delay(intervalMs)
                        refreshFromSupabase(forceEnsureProfile = false)
                    }
                }
            }
        }
    }

    /**
     * Registers Supabase Realtime WebSocket listeners for 'posts', 'comments', 'user_interactions'
     * ('likes'), and workspace tables as soon as the user logs in.
     */
    private fun registerDatabaseListenersOnLogin(session: SupabaseAuthSession?) {
        if (!supabaseClient.isConfigured()) return
        val uid = session?.userId ?: currentUserId
        val freshToken = session?.accessToken

        val needsListenerRegistration =
            realtimeListenersJob?.isActive != true || registeredRealtimeUid != uid

        if (needsListenerRegistration) {
            registeredRealtimeUid = uid
            realtimeListenersJob?.cancel()
            val watchedTables = (
                SupabaseRealtimeClient.SOCIAL_ENGAGEMENT_TABLES + MomiSupabaseSchema.TABLES
                ).distinct()
            realtimeListenersJob = realtimeClient.registerTableListeners(
                scope = viewModelScope,
                tables = watchedTables
            ) { event ->
                handleRealtimeEvent(event)
            }
            realtimeClient.connect(
                scope = viewModelScope,
                forceReconnect = true
            ) { MomiSupabaseClient.activeSession?.accessToken }
        } else {
            realtimeClient.connect(viewModelScope) { MomiSupabaseClient.activeSession?.accessToken }
            if (!freshToken.isNullOrBlank()) {
                realtimeClient.updateAccessToken(freshToken)
            }
        }
    }

    private fun handleRealtimeEvent(event: SupabaseRealtimeEvent) {
        val table = event.table.trim().lowercase()
        when (table) {
            "posts" -> applyRealtimePostEvent(event)
            "comments" -> applyRealtimeCommentEvent(event)
            "user_interactions", "likes" -> applyRealtimeInteractionOrLikeEvent(event)
            "chat_messages" -> {
                val threadId = activeChatThreadId
                if (!threadId.isNullOrBlank()) {
                    viewModelScope.launch {
                        supabaseClient.fetchChatMessages(threadId).onSuccess { list ->
                            supaChatMessagesByThread.update { it + (threadId to list) }
                        }
                    }
                }
            }
            "live_frames", "live_comments", "live_streams" -> {
                val streamId = activeLiveRoomId
                if (!streamId.isNullOrBlank()) {
                    viewModelScope.launch {
                        refreshLiveRoomSnapshot(streamId)
                    }
                }
            }
        }
        if (table != "live_frames") {
            triggerDebouncedSupabaseRefresh()
        }
    }

    private fun applyRealtimePostEvent(event: SupabaseRealtimeEvent) {
        val type = event.eventType.uppercase()
        if (type == "DELETE") {
            val deletedId = event.oldRecordJson?.optString("id")?.takeIf { it.isNotBlank() }
                ?: event.recordJson?.optString("id")?.takeIf { it.isNotBlank() }
                ?: event.oldRecordFields["id"]?.takeIf { it.isNotBlank() }
                ?: event.recordFields["id"]?.takeIf { it.isNotBlank() }
                ?: return
            supaPosts.update { list -> list.filterNot { it.id == deletedId } }
            cachedRawPosts.value = supaPosts.value
            return
        }
        val parsed = event.recordJson?.let { supabaseClient.parsePostRecord(it) } ?: return
        if (pendingDeletedPostIds.containsKey(parsed.id)) return
        val minV = pendingMinViews[parsed.id]?.first ?: 0L
        val minL = pendingMinLikes[parsed.id]?.first ?: 0L
        val mergedPost = parsed.copy(
            viewsCount = maxOf(parsed.viewsCount, minV),
            likesCount = maxOf(parsed.likesCount, minL)
        )
        supaPosts.update { current ->
            val idx = current.indexOfFirst { it.id == mergedPost.id }
            if (idx >= 0) {
                current.toMutableList().apply { set(idx, mergedPost) }
            } else {
                listOf(mergedPost) + current
            }
        }
        cachedRawPosts.value = supaPosts.value
    }

    private fun applyRealtimeCommentEvent(event: SupabaseRealtimeEvent) {
        val type = event.eventType.uppercase()
        val postId = event.recordJson?.optString("post_id")?.takeIf { it.isNotBlank() }
            ?: event.oldRecordJson?.optString("post_id")?.takeIf { it.isNotBlank() }
            ?: event.recordFields["post_id"]?.takeIf { it.isNotBlank() }
            ?: event.oldRecordFields["post_id"]?.takeIf { it.isNotBlank() }
            ?: ""

        if (postId.isNotBlank()) {
            when (type) {
                "INSERT" -> {
                    val newComment = event.recordJson?.let { supabaseClient.parseCommentRecord(it) }
                    if (newComment != null) {
                        var insertedNew = false
                        supaCommentsByPost.update { map ->
                            val existing = map[postId].orEmpty()
                            if (existing.any { it.id == newComment.id }) {
                                map
                            } else {
                                insertedNew = true
                                map + (postId to (existing + newComment))
                            }
                        }
                        if (insertedNew) {
                            supaPosts.update { posts ->
                                posts.map { p ->
                                    if (p.id == postId) {
                                        p.copy(commentsCount = (p.commentsCount + 1L).coerceAtLeast(1L))
                                    } else {
                                        p
                                    }
                                }
                            }
                        }
                    }
                }
                "UPDATE" -> {
                    val updated = event.recordJson?.let { supabaseClient.parseCommentRecord(it) }
                    if (updated != null) {
                        supaCommentsByPost.update { map ->
                            val existing = map[postId].orEmpty()
                            val idx = existing.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                map + (postId to existing.toMutableList().apply { set(idx, updated) })
                            } else {
                                map
                            }
                        }
                    }
                }
                "DELETE" -> {
                    val deletedCommentId = event.oldRecordJson?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: event.recordJson?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: event.oldRecordFields["id"]?.takeIf { it.isNotBlank() }
                        ?: event.recordFields["id"]?.takeIf { it.isNotBlank() }
                    if (!deletedCommentId.isNullOrBlank()) {
                        var removed = false
                        supaCommentsByPost.update { map ->
                            val existing = map[postId].orEmpty()
                            if (existing.any { it.id == deletedCommentId }) {
                                removed = true
                                map + (postId to existing.filterNot { it.id == deletedCommentId })
                            } else {
                                map
                            }
                        }
                        if (removed) {
                            supaPosts.update { posts ->
                                posts.map { p ->
                                    if (p.id == postId) {
                                        p.copy(commentsCount = (p.commentsCount - 1L).coerceAtLeast(0L))
                                    } else {
                                        p
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val activePostId = _activeCommentPostId.value
        if (!activePostId.isNullOrBlank() && (postId.isBlank() || postId == activePostId)) {
            viewModelScope.launch {
                supabaseClient.fetchComments(activePostId).onSuccess { remote ->
                    supaCommentsByPost.update { it + (activePostId to remote) }
                }
            }
        }
    }

    private fun applyRealtimeInteractionOrLikeEvent(event: SupabaseRealtimeEvent) {
        val type = event.eventType.uppercase()
        val isDelete = type == "DELETE"
        val sourceFields = if (isDelete && event.oldRecordFields.isNotEmpty()) {
            event.oldRecordFields
        } else {
            event.recordFields.ifEmpty { event.oldRecordFields }
        }
        val sourceJson = if (isDelete) event.oldRecordJson ?: event.recordJson else event.recordJson

        val actorUid = sourceJson?.optString("user_id")?.takeIf { it.isNotBlank() }
            ?: sourceFields["user_id"].orEmpty().trim()
        val targetId = sourceJson?.optString("target_id")?.takeIf { it.isNotBlank() }
            ?: sourceJson?.optString("post_id")?.takeIf { it.isNotBlank() }
            ?: sourceFields["target_id"]?.takeIf { it.isNotBlank() }
            ?: sourceFields["post_id"].orEmpty().trim()
        val targetType = when (event.table.trim().lowercase()) {
            "likes" -> "LIKED_POST"
            else -> sourceJson?.optString("target_type")?.takeIf { it.isNotBlank() }
                ?: sourceFields["target_type"].orEmpty().trim().uppercase()
        }

        if (targetId.isBlank()) return

        when (targetType) {
            "LIKED_POST" -> {
                if (actorUid.isNotBlank()) {
                    var changed = false
                    supaPostLikersByPostId.update { map ->
                        val currentList = map[targetId].orEmpty()
                        val nextList = if (isDelete) {
                            if (actorUid in currentList) {
                                changed = true
                                currentList.filterNot { it == actorUid }
                            } else {
                                currentList
                            }
                        } else {
                            if (actorUid !in currentList) {
                                changed = true
                                currentList + actorUid
                            } else {
                                currentList
                            }
                        }
                        if (changed) map + (targetId to nextList) else map
                    }
                    if (actorUid == currentUserId && !pendingLikes.containsKey(targetId)) {
                        supaLikedPostIds.update { set ->
                            if (isDelete) set - targetId else set + targetId
                        }
                    }
                    if (changed) {
                        val delta = if (isDelete) -1L else 1L
                        supaPosts.update { posts ->
                            posts.map { p ->
                                if (p.id == targetId && !pendingMinLikes.containsKey(targetId)) {
                                    p.copy(likesCount = (p.likesCount + delta).coerceAtLeast(0L))
                                } else {
                                    p
                                }
                            }
                        }
                    }
                }
            }
            "VIEWED_POST" -> {
                if (actorUid.isNotBlank() && !isDelete) {
                    supaPostViewersByPostId.update { map ->
                        val currentList = map[targetId].orEmpty()
                        if (actorUid in currentList) map else map + (targetId to (currentList + actorUid))
                    }
                }
            }
            "LIKED_COMMENT" -> {
                if (actorUid == currentUserId) {
                    supaLikedCommentIds.update { set ->
                        if (isDelete) set - targetId else set + targetId
                    }
                }
            }
            "BOOKMARK" -> {
                if (actorUid == currentUserId && !pendingBookmarks.containsKey(targetId)) {
                    supaBookmarkedPostIds.update { set ->
                        if (isDelete) set - targetId else set + targetId
                    }
                }
            }
            "SEEN_STORY" -> {
                if (actorUid == currentUserId && !isDelete) {
                    supaSeenStoryIds.update { it + targetId }
                }
            }
        }
    }

    private fun triggerDebouncedSupabaseRefresh() {
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = viewModelScope.launch {
            delay(250L)
            refreshFromSupabase(forceEnsureProfile = false)
        }
    }

    private suspend fun refreshLiveRoomSnapshot(streamId: String) {
        supabaseClient.fetchLiveRoomState(streamId).onSuccess { state ->
            state.second?.let { f -> supaLiveFrameByStream.update { it + (streamId to f) } }
            supaLiveCommentsByStream.update { it + (streamId to state.third) }
            state.first?.let { s ->
                supaLiveStreams.update { current ->
                    if (current.any { it.id == s.id }) {
                        current.map { if (it.id == s.id) s else it }
                    } else {
                        current + s
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeRefreshJob?.cancel()
        realtimeListenersJob?.cancel()
        realtimeClient.disconnect()
    }

    val follows: StateFlow<List<FollowEdge>> = combine(
        supaFollows,
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
        supaProfiles,
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
        supaProfiles,
        follows,
        cachedProfile
    ) { sProfiles, allFollows, localCached ->
        val cleanEmail = initialEmail?.trim()?.takeIf { it.isNotBlank() }
        val matchedRemote = sProfiles.firstOrNull { it.userId == currentUserId }
            ?: if (cleanEmail != null) sProfiles.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) } else null
        val resolved = matchedRemote
            ?: localCached
            ?: fallbackProfileBase()
        val hydrated = resolved.copy(
            followersCount = allFollows.count { it.targetUid == resolved.userId }.toLong(),
            followingCount = allFollows.count { it.followerUid == resolved.userId }.toLong()
        )
        StreamUiState.Success(hydrated) as StreamUiState<UserProfile?>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing profile", root)
            val recovered = supaProfiles.value.firstOrNull { it.userId == currentUserId }
                ?: cachedProfile.value
                ?: fallbackProfileBase()
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
            if (!canView) flowOf(emptyList()) else supaContentReports
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

    private val likedPostIds: StateFlow<Set<String>> = supaLikedPostIds
    private val bookmarkedPostIds: StateFlow<Set<String>> = supaBookmarkedPostIds
    private val seenStoryIds: StateFlow<Set<String>> = supaSeenStoryIds
    private val likedCommentIds: StateFlow<Set<String>> = supaLikedCommentIds

    private val postBaseStreams = combine(
        supaPosts,
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
        optimisticPostInputs,
        supaHasLoadedFeed
    ) { base, (likeOverrides, bookmarkOverrides, pendingComments), hasLoaded ->
        val sourcePosts = base.posts.ifEmpty { cachedRawPosts.value }
        if (!hasLoaded && sourcePosts.isEmpty()) {
            return@combine StreamUiState.Loading
        }
        val profilesByUid = base.profiles.associateBy { it.userId }
        val enriched = sourcePosts.map { post ->
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
                authorAvatarUrl = if (isShowcase) null else (authorProfile?.avatarUrl ?: post.authorAvatarUrl),
                authorAvatarData = if (isShowcase) null else authorProfile?.avatarData,
                authorVerified = if (isShowcase) post.authorVerified else (authorProfile?.isVerified ?: post.authorVerified),
                isLiked = desiredLiked,
                likesCount = (post.likesCount + likeDelta).coerceAtLeast(0L),
                commentsCount = (post.commentsCount + pendingCount).coerceAtLeast(0L),
                isBookmarked = desiredBookmarked,
                isFollowingAuthor = post.userId == currentUserId || base.following.contains(post.userId)
            )
        }
        val prioritized = sortFeedWithBoostPriority(enriched)
        lastKnownEnrichedPosts.value = prioritized
        StreamUiState.Success(prioritized) as StreamUiState<List<Post>>
    }
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing posts", root)
            val cached = lastKnownEnrichedPosts.value
            if (cached.isNotEmpty()) {
                emit(StreamUiState.Success(cached))
            } else {
                emit(StreamUiState.Error(unwrapErrorMessage(root, "Failed to sync Supabase feed")))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StreamUiState.Loading)

    val isFeedLoading: StateFlow<Boolean> = allPostsState
        .map { it is StreamUiState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

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
        supaStories,
        allProfiles,
        seenStoryIds
    ) { rawStories, profiles, seen ->
        val profilesByUid = profiles.associateBy { it.userId }
        val cutoffMs = System.currentTimeMillis() - MomiSupabaseSchema.STORY_TTL_MS
        rawStories
            .filter { story ->
                val createdMs = story.createdAt?.toDate()?.time
                createdMs == null || createdMs >= cutoffMs
            }
            .map { story ->
                val p = profilesByUid[story.userId]
                story.copy(
                    avatarUrl = p?.avatarUrl ?: story.avatarUrl,
                    avatarData = p?.avatarData,
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

    val showcasePostsCount: StateFlow<Int> = allPosts
        .map { list -> list.count { it.id.startsWith("showcase_") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val showcaseStoriesCount: StateFlow<Int> = stories
        .map { list -> list.count { it.id.startsWith("showcase_") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val activeLiveStreams: StateFlow<List<LiveStream>> = combine(
        supaLiveStreams,
        allProfiles
    ) { streams, profiles ->
        val profilesByUid = profiles.associateBy { it.userId }
        streams.map { stream ->
            val p = profilesByUid[stream.userId]
            stream.copy(
                avatarUrl = p?.avatarUrl ?: stream.avatarUrl,
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
        supaNotifications,
        followingUids,
        supaMonetizationConfig,
        cachedMonetizationConfig
    ) { items, following, supaCfg, cachedCfg ->
        val baseItems = items.map { item ->
            if (item.isFollowAction && item.actorUid.isNotBlank()) {
                item.copy(isFollowingBack = following.contains(item.actorUid))
            } else {
                item
            }
        }
        val cfg = supaCfg ?: cachedCfg
        val annTitle = cfg.announcementTitle.trim()
        val annBody = cfg.announcementBody.trim()
        val annId = cfg.announcementId.ifBlank { "ann_${annTitle.hashCode()}" }
        if ((annTitle.isNotBlank() || annBody.isNotBlank()) && baseItems.none { it.id == annId }) {
            val annItem = NotificationItem(
                id = annId,
                userId = currentUserId,
                actorUid = "momi_official",
                actorUsername = "momi",
                actorAvatarKey = "avatar_aria",
                message = buildString {
                    if (annTitle.isNotBlank()) append(annTitle)
                    if (annTitle.isNotBlank() && annBody.isNotBlank()) append(" — ")
                    if (annBody.isNotBlank()) append(annBody)
                    if (cfg.latestVersionName.isNotBlank()) append(" (v${cfg.latestVersionName})")
                },
                timeLabel = "Pinned",
                section = "Updates & Announcements",
                isFollowAction = false,
                isUnread = _dismissedAnnouncementId.value != annId,
                createdAt = Timestamp.now()
            )
            listOf(annItem) + baseItems
        } else {
            baseItems
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

    val pages: StateFlow<List<MomiPage>> = combine(
        supaPages,
        supaPageFollowedIds
    ) { pageList, followed ->
        pageList.map { it.copy(isFollowing = it.id in followed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val groups: StateFlow<List<MomiGroup>> = combine(
        supaGroups,
        supaGroupJoinedIds
    ) { groupList, joined ->
        groupList.map { it.copy(isMember = it.id in joined) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val showcaseAccounts: StateFlow<List<ShowcaseAccount>> = supaShowcaseAccounts
        .map { remote ->
            val remoteByUsername = remote.associateBy { it.username.lowercase() }
            ShowcaseCatalog.PERSONAS.map { persona ->
                val saved = remoteByUsername[persona.username.lowercase()]
                ShowcaseAccount(
                    username = persona.username,
                    displayName = saved?.displayName?.takeIf { it.isNotBlank() } ?: persona.displayName,
                    avatarKey = saved?.avatarKey?.takeIf { it.isNotBlank() } ?: persona.avatarKey,
                    bio = saved?.bio?.takeIf { it.isNotBlank() } ?: "Featured Momi creator in ${persona.location}",
                    category = saved?.category?.takeIf { it.isNotBlank() } ?: persona.category,
                    location = saved?.location?.takeIf { it.isNotBlank() } ?: persona.location,
                    isAuthorized = saved?.isAuthorized ?: false,
                    updatedByEmail = saved?.updatedByEmail.orEmpty()
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ShowcaseCatalog.PERSONAS.map {
                ShowcaseAccount(
                    username = it.username,
                    displayName = it.displayName,
                    avatarKey = it.avatarKey,
                    bio = "Featured Momi creator in ${it.location}",
                    category = it.category,
                    location = it.location,
                    isAuthorized = false
                )
            }
        )

    val messageThreads: StateFlow<List<MessageThread>> = supaThreads

    val unreadMessageCount: StateFlow<Int> = messageThreads
        .map { list ->
            list.filter { it.isUnreadFor(currentUserId) }.sumOf { it.unreadCount.toInt() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val merchants: StateFlow<List<MerchantAccount>> = supaMerchants

    val marketProducts: StateFlow<List<MarketProduct>> = combine(
        supaProducts,
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

    val marketOrders: StateFlow<List<MarketOrder>> = supaOrders

    val wallet: StateFlow<UserWallet?> = supaWallet

    val creditTransactions: StateFlow<List<CreditTransaction>> = supaCreditTransactions

    val monetizationConfig: StateFlow<AdMonetizationConfig> = supaMonetizationConfig
        .map { it ?: cachedMonetizationConfig.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AdMonetizationConfig())

    private val _themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _colorScheme = MutableStateFlow(AppColorScheme.EMERALD_MAROON)
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

    private fun observeSupabaseCommentsFlow(postId: String): Flow<List<Comment>> = kotlinx.coroutines.flow.flow {
        if (!supabaseClient.isConfigured()) {
            emit(emptyList())
            return@flow
        }
        while (true) {
            supabaseClient.fetchComments(postId).onSuccess { remote ->
                supaCommentsByPost.update { it + (postId to remote) }
            }
            emit(supaCommentsByPost.value[postId].orEmpty())
            val waitMs = if (realtimeClient.connectionState.value.state == SupabaseRealtimeConnectionState.CONNECTED) {
                8_000L
            } else {
                2_000L
            }
            delay(waitMs)
        }
    }

    val activeComments: StateFlow<List<Comment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    observeSupabaseCommentsFlow(postId),
                    supaCommentsByPost.map { it[postId].orEmpty() },
                    allProfiles,
                    likedCommentIds,
                    optimisticCommentLikes,
                    optimisticCommentsByPost
                ) { values ->
                    @Suppress("UNCHECKED_CAST")
                    val comments = (values[1] as List<Comment>).ifEmpty { values[0] as List<Comment> }
                    @Suppress("UNCHECKED_CAST")
                    val profiles = values[2] as List<UserProfile>
                    @Suppress("UNCHECKED_CAST")
                    val liked = values[3] as Set<String>
                    @Suppress("UNCHECKED_CAST")
                    val likeOverrides = values[4] as Map<String, Boolean>
                    @Suppress("UNCHECKED_CAST")
                    val pendingMap = values[5] as Map<String, List<Comment>>
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
                        val prof = profilesByUid[comment.userId]
                        comment.copy(
                            authorAvatarUrl = prof?.avatarUrl ?: comment.authorAvatarUrl,
                            authorAvatarData = prof?.avatarData,
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

    fun observePost(postId: String): Flow<Post?> = allPosts.map { list ->
        list.firstOrNull { it.id == postId }
    }

    fun observeChatMessages(threadId: String): Flow<List<ChatMessage>> {
        activeChatThreadId = threadId
        val supaPoller = kotlinx.coroutines.flow.flow {
            if (!supabaseClient.isConfigured()) {
                emit(emptyList())
                return@flow
            }
            while (true) {
                supabaseClient.fetchChatMessages(threadId).onSuccess { list ->
                    supaChatMessagesByThread.update { it + (threadId to list) }
                }
                emit(supaChatMessagesByThread.value[threadId].orEmpty())
                val waitMs = if (realtimeClient.connectionState.value.state == SupabaseRealtimeConnectionState.CONNECTED) {
                    8_000L
                } else {
                    2_000L
                }
                delay(waitMs)
            }
        }
        return combine(
            supaPoller,
            supaChatMessagesByThread.map { it[threadId].orEmpty() }
        ) { _, supaMessages ->
            supaMessages
        }.catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing chat messages for $threadId", root)
            emit(supaChatMessagesByThread.value[threadId].orEmpty())
        }
    }

    private val activeLivePollStreams = mutableMapOf<String, StateFlow<Triple<LiveStream?, LiveFrame?, List<LiveComment>>>>()

    private fun liveRoomStateFlow(streamId: String): StateFlow<Triple<LiveStream?, LiveFrame?, List<LiveComment>>> {
        activeLiveRoomId = streamId
        return activeLivePollStreams.getOrPut(streamId) {
            kotlinx.coroutines.flow.flow {
                if (!supabaseClient.isConfigured()) {
                    emit(Triple(null, null, emptyList()))
                    return@flow
                }
                while (true) {
                    refreshLiveRoomSnapshot(streamId)
                    val curStream = supaLiveStreams.value.firstOrNull { it.id == streamId }
                    val curFrame = supaLiveFrameByStream.value[streamId]
                    val curComments = supaLiveCommentsByStream.value[streamId].orEmpty()
                    emit(Triple(curStream, curFrame, curComments))
                    val waitMs = if (realtimeClient.connectionState.value.state == SupabaseRealtimeConnectionState.CONNECTED) {
                        1_800L
                    } else {
                        1_200L
                    }
                    delay(waitMs)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Triple(null, null, emptyList()))
        }
    }

    fun observeLiveStream(streamId: String): Flow<LiveStream?> = combine(
        liveRoomStateFlow(streamId),
        supaLiveStreams,
        allProfiles
    ) { roomTriple, sStreams, profiles ->
        val raw = roomTriple.first ?: sStreams.firstOrNull { it.id == streamId }
        raw?.let { s ->
            val p = profiles.firstOrNull { it.userId == s.userId }
            s.copy(
                avatarUrl = p?.avatarUrl ?: s.avatarUrl,
                avatarData = p?.avatarData,
                authorVerified = p?.isVerified ?: s.authorVerified
            )
        }
    }.catch { error ->
        val root = unwrapOrRethrowCancellation(error)
        Log.w(TAG, "Error observing live stream $streamId", root)
        emit(supaLiveStreams.value.firstOrNull { it.id == streamId })
    }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> = combine(
        liveRoomStateFlow(streamId),
        supaLiveCommentsByStream.map { it[streamId].orEmpty() }
    ) { roomTriple, cachedSupa ->
        (roomTriple.third + cachedSupa).distinctBy { it.id }
    }.catch { error ->
        val root = unwrapOrRethrowCancellation(error)
        Log.w(TAG, "Error observing live comments for $streamId", root)
        emit(supaLiveCommentsByStream.value[streamId].orEmpty())
    }

    fun observeLiveFrame(streamId: String): Flow<LiveFrame?> = combine(
        liveRoomStateFlow(streamId),
        supaLiveFrameByStream.map { it[streamId] }
    ) { roomTriple, cachedFrame ->
        val roomFrame = roomTriple.second
        if (roomFrame != null && (cachedFrame == null || roomFrame.seq >= cachedFrame.seq)) {
            roomFrame
        } else {
            cachedFrame ?: roomFrame
        }
    }.catch { error ->
        val root = unwrapOrRethrowCancellation(error)
        Log.w(TAG, "Error observing live frame for $streamId", root)
        emit(supaLiveFrameByStream.value[streamId])
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    fun dismissAvailableUpdate(context: Context? = null) {
        val current = _availableUpdate.value
        val ctx = context ?: cachedAppContext
        if (ctx != null && current != null) {
            AppUpdateChecker.dismissVersion(ctx, current.latestVersionName, current.remoteBuildNumber)
        }
        _availableUpdate.value = null
    }

    fun checkForUpdates(
        context: Context? = null,
        manual: Boolean = false,
        isPlayStoreInstall: Boolean = false
    ) {
        if (context != null) {
            cachedAppContext = context.applicationContext
        }
        knownPlayStoreInstall = isPlayStoreInstall
        val effectiveContext = context ?: cachedAppContext

        if (!manual) {
            if (hasAutoCheckedUpdatesInSession) return
            if (effectiveContext != null && !AppUpdateChecker.shouldRunAutoCheck(effectiveContext)) return
            hasAutoCheckedUpdatesInSession = true
            if (effectiveContext != null) {
                AppUpdateChecker.recordAutoCheck(effectiveContext)
            }
        } else if (effectiveContext != null) {
            AppUpdateChecker.clearDismissedVersion(effectiveContext)
        }

        viewModelScope.launch {
            AppUpdateChecker.checkForUpdate(
                isPlayStoreInstall = isPlayStoreInstall,
                supabaseConfig = monetizationConfig.value
            )
                .onSuccess { result ->
                    val candidate = result.availableUpdate
                    if (candidate != null) {
                        val isDismissed = effectiveContext != null &&
                            AppUpdateChecker.isVersionDismissed(
                                effectiveContext,
                                candidate.latestVersionName,
                                candidate.remoteBuildNumber
                            )
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

    fun checkGoogleAiStatus(force: Boolean = false) {
        if (_isCheckingAiStatus.value) return
        if (!force && hasProbedAiStatusInSession && _aiStatus.value.state == GoogleAiHealthState.READY) {
            return
        }
        hasProbedAiStatusInSession = true
        viewModelScope.launch {
            _isCheckingAiStatus.value = true
            _aiStatus.value = _aiStatus.value.copy(
                state = GoogleAiHealthState.CHECKING,
                label = "Checking Momi AI..."
            )
            val result = aiAssistantRepository.probeGoogleAiStatus(_userGeminiApiKey.value)
            _aiStatus.value = result
            _isCheckingAiStatus.value = false
        }
    }

    fun saveUserGeminiApiKey(context: Context, apiKey: String) {
        val clean = apiKey.trim()
        _userGeminiApiKey.value = clean
        AppearancePrefs.saveUserGeminiApiKey(context, clean)
        _aiStatus.value = aiAssistantRepository.currentConfigStatus(clean)
        checkGoogleAiStatus(force = true)
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _aiUiState.value is AiAssistantUiState.Generating) return

        val isImageGen = listOf(
            "generate image", "generate a image", "draw", "create image", "imagen",
            "picture of", "photo of", "ai art", "🎨"
        ).any { clean.lowercase().contains(it) }

        val userTurnId = "user_${UUID.randomUUID()}"
        val botTurnId = "ai_${UUID.randomUUID()}"
        _aiMessages.update {
            it + AiAssistantTurn(id = userTurnId, isUser = true, text = clean)
        }
        _aiUiState.value = AiAssistantUiState.Generating

        viewModelScope.launch {
            if (isImageGen) {
                try {
                    val (replyText, bitmap) = withTimeout(60_000L) {
                        aiAssistantRepository.generateImage(
                            userPrompt = clean,
                            userOverrideKey = _userGeminiApiKey.value
                        )
                    }
                    _aiMessages.update { turns ->
                        turns + AiAssistantTurn(
                            id = botTurnId,
                            isUser = false,
                            text = replyText,
                            isError = false,
                            imageBitmap = bitmap
                        )
                    }
                } catch (e: Exception) {
                    val fallbackText = "Failed to generate image: ${e.localizedMessage ?: "Unknown error"}"
                    _aiMessages.update { turns ->
                        turns + AiAssistantTurn(
                            id = botTurnId,
                            isUser = false,
                            text = fallbackText,
                            isError = true
                        )
                    }
                } finally {
                    _aiUiState.value = AiAssistantUiState.Idle
                }
            } else {
                val historyPairs = _aiMessages.value
                    .filter { it.id != "welcome_ai" && !it.isError && it.text.isNotBlank() }
                    .map { it.isUser to it.text }

                aiAssistantRepository.streamReply(
                    userPrompt = clean,
                    history = historyPairs,
                    userOverrideKey = _userGeminiApiKey.value
                ).collect { chunkResult ->
                    chunkResult.onSuccess { replyText ->
                        _aiMessages.update { turns ->
                            val existing = turns.any { it.id == botTurnId }
                            if (existing) {
                                turns.map { turn ->
                                    if (turn.id == botTurnId) turn.copy(text = replyText, isError = false) else turn
                                }
                            } else {
                                turns + AiAssistantTurn(id = botTurnId, isUser = false, text = replyText, isError = false)
                            }
                        }
                        _aiStatus.value = aiAssistantRepository.currentConfigStatus(_userGeminiApiKey.value)
                    }.onFailure { err ->
                        val fallback = aiAssistantRepository.generateBuiltInMomiReply(clean)
                        _aiMessages.update { turns ->
                            turns + AiAssistantTurn(id = botTurnId, isUser = false, text = fallback, isError = false)
                        }
                        _aiUiState.value = AiAssistantUiState.Idle
                        Log.w(TAG, "AI fallback used", err)
                    }
                }
                if (_aiUiState.value is AiAssistantUiState.Generating) {
                    _aiUiState.value = AiAssistantUiState.Idle
                }
            }
        }
    }

    fun loadAppearance(context: android.content.Context) {
        val appCtx = context.applicationContext
        cachedAppContext = appCtx
        _themePreference.value = AppearancePrefs.loadThemePreference(appCtx)
        _colorScheme.value = AppearancePrefs.loadColorScheme(appCtx)
        val savedGeminiKey = AppearancePrefs.loadUserGeminiApiKey(appCtx)
        _userGeminiApiKey.value = savedGeminiKey
        _aiStatus.value = aiAssistantRepository.currentConfigStatus(savedGeminiKey)
        if (isOwnerAccount) {
            _adminViewEnabled.value = AppearancePrefs.loadAdminViewMode(appCtx)
        }
        restoreLocalCaches(appCtx)
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
        val targetCount = (post.likesCount + if (nextLiked) 1L else -1L).coerceAtLeast(0L)
        val expiresAt = System.currentTimeMillis() + MUTATION_PROTECTION_MS
        pendingLikes[post.id] = nextLiked to expiresAt
        pendingMinLikes[post.id] = targetCount to expiresAt
        optimisticPostLikes.update { it + (post.id to nextLiked) }
        viewModelScope.launch {
            supaLikedPostIds.update { if (nextLiked) it + post.id else it - post.id }
            supaPostLikersByPostId.update { map ->
                val cur = map[post.id].orEmpty().toMutableList()
                if (nextLiked) {
                    if (currentUserId !in cur) cur.add(0, currentUserId)
                } else {
                    cur.remove(currentUserId)
                }
                map + (post.id to cur)
            }
            supaPosts.update { list -> list.map { if (it.id == post.id) it.copy(likesCount = targetCount) else it } }
            val res = supabaseBackend.setInteraction(currentUserId, "LIKED_POST", post.id, nextLiked)
                .mapCatching { supabaseBackend.updatePostCounts(post.id, likesCount = targetCount).getOrThrow() }
            res.onSuccess {
                if (nextLiked && post.userId != currentUserId && !post.id.startsWith("showcase_")) {
                    val notif = NotificationItem(
                        id = "like_${post.id}_$currentUserId",
                        userId = post.userId,
                        actorUid = currentUserId,
                        actorUsername = currentProfile.username,
                        actorAvatarKey = currentProfile.avatarKey,
                        message = "liked your post",
                        timeLabel = "Just now",
                        section = "Today",
                        previewImageKey = post.imageKey,
                        isUnread = true,
                        createdAt = Timestamp.now()
                    )
                    supabaseBackend.sendNotification(notif)
                }
                optimisticPostLikes.update { it - post.id }
            }.onFailure { err ->
                pendingLikes.remove(post.id)
                pendingMinLikes.remove(post.id)
                optimisticPostLikes.update { it - post.id }
                _errorMessage.value = unwrapErrorMessage(err, "Could not update like")
            }
        }
    }

    fun doubleTapLike(post: Post) {
        if (post.isLiked) return
        val targetCount = post.likesCount + 1L
        val expiresAt = System.currentTimeMillis() + MUTATION_PROTECTION_MS
        pendingLikes[post.id] = true to expiresAt
        pendingMinLikes[post.id] = targetCount to expiresAt
        optimisticPostLikes.update { it + (post.id to true) }
        viewModelScope.launch {
            supaLikedPostIds.update { it + post.id }
            supaPostLikersByPostId.update { map ->
                val cur = map[post.id].orEmpty().toMutableList()
                if (currentUserId !in cur) cur.add(0, currentUserId)
                map + (post.id to cur)
            }
            supaPosts.update { list -> list.map { if (it.id == post.id) it.copy(likesCount = targetCount) else it } }
            val res = supabaseBackend.setInteraction(currentUserId, "LIKED_POST", post.id, true)
                .mapCatching { supabaseBackend.updatePostCounts(post.id, likesCount = targetCount).getOrThrow() }
            res.onSuccess {
                optimisticPostLikes.update { it - post.id }
            }.onFailure { err ->
                pendingLikes.remove(post.id)
                pendingMinLikes.remove(post.id)
                optimisticPostLikes.update { it - post.id }
                _errorMessage.value = unwrapErrorMessage(err, "Could not like post")
            }
        }
    }

    fun toggleBookmark(post: Post) {
        val nextBookmarked = !post.isBookmarked
        pendingBookmarks[post.id] = nextBookmarked to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
        optimisticBookmarks.update { it + (post.id to nextBookmarked) }
        viewModelScope.launch {
            supaBookmarkedPostIds.update { if (nextBookmarked) it + post.id else it - post.id }
            supabaseBackend.setInteraction(currentUserId, "BOOKMARK", post.id, nextBookmarked)
                .onSuccess {
                    optimisticBookmarks.update { it - post.id }
                }
                .onFailure { err ->
                    pendingBookmarks.remove(post.id)
                    optimisticBookmarks.update { it - post.id }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update bookmark")
                }
        }
    }

    fun toggleFollow(targetUid: String, targetUsername: String, currentlyFollowing: Boolean) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val currentProfile = fallbackProfile()
        val nextFollowing = !currentlyFollowing
        val edgeId = "${currentProfile.userId}_$targetUid"
        val pendingEdge = if (nextFollowing) {
            FollowEdge(
                id = edgeId,
                followerUid = currentUserId,
                targetUid = targetUid,
                followerUsername = currentProfile.username,
                targetUsername = targetUsername,
                createdAt = Timestamp.now()
            )
        } else {
            null
        }
        pendingFollowStates[edgeId] = pendingEdge to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
        optimisticFollows.update { it + (targetUid to (nextFollowing to targetUsername)) }
        viewModelScope.launch {
            supaFollows.update { current ->
                if (currentlyFollowing) {
                    current.filterNot { it.id == edgeId || (it.followerUid == currentUserId && it.targetUid == targetUid) }
                } else {
                    current.filterNot { it.id == edgeId } + pendingEdge!!
                }
            }
            supabaseBackend.toggleFollow(currentProfile, targetUid, targetUsername, currentlyFollowing)
                .onSuccess {
                    if (!currentlyFollowing && !targetUid.startsWith("showcase_")) {
                        supabaseBackend.sendNotification(
                            NotificationItem(
                                id = "follow_${currentUserId}_$targetUid",
                                userId = targetUid,
                                actorUid = currentUserId,
                                actorUsername = currentProfile.username,
                                actorAvatarKey = currentProfile.avatarKey,
                                message = "started following you",
                                timeLabel = "Just now",
                                section = "Today",
                                isFollowAction = true,
                                isUnread = true,
                                createdAt = Timestamp.now()
                            )
                        )
                    }
                    optimisticFollows.update { it - targetUid }
                }
                .onFailure { err ->
                    pendingFollowStates.remove(edgeId)
                    optimisticFollows.update { it - targetUid }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update follow state")
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
            authorAvatarUrl = currentProfile.avatarUrl,
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
            val comment = Comment(
                id = "cmt_${UUID.randomUUID()}",
                userId = currentUserId,
                authorUsername = currentProfile.username,
                authorAvatarKey = currentProfile.avatarKey,
                authorAvatarUrl = currentProfile.avatarUrl,
                text = trimmed.take(500),
                likesCount = 0L,
                isLiked = false,
                createdAt = Timestamp.now()
            )
            val nextCount = post.commentsCount + 1L
            supabaseBackend.addComment(postId, currentProfile, comment, nextCount)
                .onSuccess {
                    supaCommentsByPost.update { map -> map + (postId to (map[postId].orEmpty() + comment)) }
                    supaPosts.update { list -> list.map { if (it.id == postId) it.copy(commentsCount = nextCount) else it } }
                    if (post.userId != currentUserId && !post.id.startsWith("showcase_")) {
                        supabaseBackend.sendNotification(
                            NotificationItem(
                                id = "cmt_${comment.id}",
                                userId = post.userId,
                                actorUid = currentUserId,
                                actorUsername = currentProfile.username,
                                actorAvatarKey = currentProfile.avatarKey,
                                message = "commented: ${trimmed.take(80)}",
                                timeLabel = "Just now",
                                section = "Today",
                                previewImageKey = post.imageKey,
                                isUnread = true,
                                createdAt = Timestamp.now()
                            )
                        )
                    }
                    optimisticCommentsByPost.update { map ->
                        val remaining = map[postId].orEmpty().filterNot { it.id == tempComment.id }
                        if (remaining.isEmpty()) map - postId else map + (postId to remaining)
                    }
                }
                .onFailure { err ->
                    optimisticCommentsByPost.update { map ->
                        val remaining = map[postId].orEmpty().filterNot { it.id == tempComment.id }
                        if (remaining.isEmpty()) map - postId else map + (postId to remaining)
                    }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not post comment")
                }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val postId = _activeCommentPostId.value ?: return
        if (comment.id.startsWith("pending_")) return
        val nextLiked = !comment.isLiked
        optimisticCommentLikes.update { it + (comment.id to nextLiked) }
        viewModelScope.launch {
            val targetCount = (comment.likesCount + if (nextLiked) 1L else -1L).coerceAtLeast(0L)
            supabaseBackend.setInteraction(currentUserId, "LIKED_COMMENT", comment.id, nextLiked)
                .mapCatching { supabaseBackend.setCommentLikes(comment.id, targetCount).getOrThrow() }
                .onSuccess {
                    supaLikedCommentIds.update { if (nextLiked) it + comment.id else it - comment.id }
                    supaCommentsByPost.update { map ->
                        val list = map[postId].orEmpty().map {
                            if (it.id == comment.id) it.copy(likesCount = targetCount) else it
                        }
                        map + (postId to list)
                    }
                    optimisticCommentLikes.update { it - comment.id }
                }
                .onFailure { err ->
                    optimisticCommentLikes.update { it - comment.id }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update comment like")
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun resolveRemoteVideoUri(
        context: android.content.Context,
        collectionName: String,
        mediaId: String,
        expectedChunksCount: Long
    ): Uri? {
        if (mediaId.isBlank() || mediaId.startsWith("showcase_")) return null
        return MediaAssets.resolveCachedVideoUri(context.applicationContext, mediaId)
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
        pageId: String? = null,
        groupId: String? = null,
        communityName: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        val currentProfile = fallbackProfile()
        val extraPaths = localExtraImagePaths.filter { it.isNotBlank() }.take(4)
        val mergedHashtags = MediaAssets.extractHashtags(caption, hashtags)
        val mergedTags = MediaAssets.extractMentions(caption, taggedUsernames)
        viewModelScope.launch {
            val postId = "post_${UUID.randomUUID()}"
            val uploaded = supabaseBackend.prepareAndUploadPostMedia(
                context = context.applicationContext,
                mediaId = postId,
                localImagePath = localImagePath,
                localExtraImagePaths = extraPaths,
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                localAudioPath = localAudioPath
            )
            if (uploaded.failureReason != null ||
                (mediaType == "VIDEO" && uploaded.videoUrl == null && uploaded.videoDataFallback == null && uploaded.videoChunksFallback.isEmpty())
            ) {
                _errorMessage.value = uploaded.failureReason ?: "Could not upload video clip. Please try a shorter clip."
                onComplete(false)
                return@launch
            }
            val newPost = Post(
                id = postId,
                userId = currentUserId,
                authorUsername = currentProfile.username,
                authorDisplayName = currentProfile.displayName,
                authorAvatarKey = currentProfile.avatarKey,
                authorAvatarUrl = currentProfile.avatarUrl,
                authorVerified = currentProfile.isVerified,
                location = location.trim().take(80),
                imageKey = imageKey,
                imageUrl = uploaded.imageUrl,
                imageData = uploaded.imageDataFallback,
                extraImageUrls = uploaded.extraImageUrls,
                extraImageDatas = uploaded.extraImageDatasFallback,
                mediaType = mediaType,
                videoUrl = uploaded.videoUrl,
                videoData = uploaded.videoDataFallback,
                videoChunksCount = uploaded.videoChunksFallback.size.toLong(),
                videoDurationMs = videoDurationMs,
                filterTag = filterTag,
                aspectRatio = aspectRatio,
                taggedUsernames = mergedTags,
                hashtags = mergedHashtags,
                caption = caption.trim().take(2000),
                likesCount = 0L,
                viewsCount = 0L,
                commentsCount = 0L,
                category = category,
                isReel = isReel,
                audioTitle = audioTitle.trim().take(80),
                audioUrl = uploaded.audioUrl,
                audioData = uploaded.audioDataFallback,
                pageId = pageId,
                groupId = groupId,
                communityName = communityName.trim().take(80),
                createdAt = Timestamp.now()
            )
            pendingCreatedPosts[postId] = newPost to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
            supaPosts.update { current -> listOf(newPost) + current.filterNot { it.id == newPost.id } }
            supabaseBackend.createPost(currentProfile, newPost)
                .onSuccess {
                    onComplete(true)
                }
                .onFailure { err ->
                    pendingCreatedPosts.remove(postId)
                    supaPosts.update { current -> current.filterNot { it.id == newPost.id } }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not publish post")
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
            val storyId = "story_${UUID.randomUUID()}"
            val uploaded = supabaseBackend.prepareAndUploadPostMedia(
                context = context.applicationContext,
                mediaId = storyId,
                localImagePath = localImagePath,
                localExtraImagePaths = emptyList(),
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                localAudioPath = localAudioPath
            )
            if (uploaded.failureReason != null ||
                (mediaType == "VIDEO" && uploaded.videoUrl == null && uploaded.videoDataFallback == null)
            ) {
                _errorMessage.value = uploaded.failureReason ?: "Could not upload story clip."
                onComplete(false)
                return@launch
            }
            val newStory = Story(
                id = storyId,
                userId = currentUserId,
                username = currentProfile.username,
                avatarKey = currentProfile.avatarKey,
                avatarUrl = currentProfile.avatarUrl,
                imageKey = imageKey,
                imageUrl = uploaded.imageUrl,
                imageData = uploaded.imageDataFallback,
                mediaType = mediaType,
                videoUrl = uploaded.videoUrl,
                videoData = uploaded.videoDataFallback,
                videoDurationMs = videoDurationMs,
                audioTitle = audioTitle.trim().take(80),
                audioUrl = uploaded.audioUrl,
                audioData = uploaded.audioDataFallback,
                caption = caption.trim().take(240),
                createdAt = Timestamp.now(),
                isOwnStory = true,
                isSeen = false
            )
            supabaseBackend.createStory(currentProfile, newStory)
                .onSuccess {
                    supaStories.update { current -> listOf(newStory) + current.filterNot { it.id == newStory.id } }
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not share story")
                    onComplete(false)
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            pendingDeletedPostIds[postId] = System.currentTimeMillis() + MUTATION_PROTECTION_MS
            pendingCreatedPosts.remove(postId)
            supaPosts.update { list -> list.filterNot { it.id == postId } }
            supabaseBackend.deletePost(postId)
                .onFailure {
                    pendingDeletedPostIds.remove(postId)
                    _errorMessage.value = unwrapErrorMessage(it, "Could not delete post")
                }
        }
    }

    fun markStorySeen(storyId: String) {
        viewModelScope.launch {
            supaSeenStoryIds.update { it + storyId }
            supabaseBackend.setInteraction(currentUserId, "SEEN_STORY", storyId, true)
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
        val currentProfile = profile.value ?: fallbackProfile()
        viewModelScope.launch {
            var nextAvatarUrl: String? = if (removeAvatar) null else currentProfile.avatarUrl
            var nextAvatarData: String? = if (removeAvatar) null else currentProfile.avatarData
            if (!removeAvatar && !pickedAvatarLocalPath.isNullOrBlank()) {
                val uploadedAvatar = supabaseBackend.uploadImageToStorage(
                    bucket = "momi-avatars",
                    objectPath = "avatars/${currentUserId}_${System.currentTimeMillis()}.jpg",
                    localImagePath = pickedAvatarLocalPath
                )
                if (uploadedAvatar != null) {
                    nextAvatarUrl = uploadedAvatar
                    nextAvatarData = null
                } else {
                    nextAvatarData = withContext(Dispatchers.IO) {
                        MediaAssets.encodeAvatarFileToBase64(pickedAvatarLocalPath)
                    } ?: currentProfile.avatarData
                }
            }

            val updated = currentProfile.copy(
                displayName = displayName.trim().take(80).ifBlank { currentProfile.displayName },
                username = username.trim().lowercase().replace(Regex("[^a-z0-9._]"), "").take(50).ifBlank { currentProfile.username },
                category = category.trim().take(60),
                bio = bio.trim().take(240),
                website = website.trim().take(120),
                avatarUrl = nextAvatarUrl,
                avatarData = nextAvatarData
            )
            pendingProfileOverride = updated to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
            supaProfiles.update { list ->
                if (list.any { it.userId == updated.userId }) list.map { if (it.userId == updated.userId) updated else it }
                else listOf(updated) + list
            }
            cachedProfile.value = updated
            cachedAppContext?.let { ctx ->
                AppearancePrefs.saveCachedProfileJson(ctx, updated.userId, serializeProfileToJson(updated))
            }
            supabaseBackend.upsertProfile(updated)
                .onSuccess {
                    _statusNotice.value = "Profile saved!"
                }
                .onFailure {
                    pendingProfileOverride = null
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
            supabaseClient.patchRows("profiles", "user_id=eq.${java.net.URLEncoder.encode(targetUid, "UTF-8")}", mapOf("is_verified" to verified))
                .onSuccess {
                    supaProfiles.update { list -> list.map { if (it.userId == targetUid) it.copy(isVerified = verified) else it } }
                }
                .onFailure {
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
            val adminEmail = initialEmail ?: "syali6511@gmail.com"
            supabaseClient.patchRows(
                "profiles",
                "user_id=eq.${java.net.URLEncoder.encode(targetUid, "UTF-8")}",
                mapOf("role" to role, "moderation_scopes" to scopes, "assigned_by_email" to adminEmail)
            ).onSuccess {
                supaProfiles.update { list ->
                    list.map { if (it.userId == targetUid) it.copy(role = role, moderationScopes = scopes, assignedByEmail = adminEmail) else it }
                }
                _statusNotice.value = if (role == MomiRoles.USER) {
                    "Team role removed — user restored to regular member."
                } else {
                    "Updated team member to ${MomiRoles.labelOf(role)} (${scopes.size} duties)."
                }
            }.onFailure { err ->
                _errorMessage.value = unwrapErrorMessage(err, "Could not update team member role")
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
            val report = ContentReport(
                id = "rep_${UUID.randomUUID()}",
                targetType = targetType,
                targetId = targetId,
                targetAuthorUid = targetAuthorUid,
                targetAuthorUsername = targetAuthorUsername,
                targetPreviewText = targetPreviewText.take(200),
                category = category,
                reasonDetail = reasonDetail.trim().take(500),
                reporterUid = currentUserId,
                reporterUsername = currentProfile.username,
                status = "OPEN",
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertReport(report)
                .onSuccess {
                    supaContentReports.update { listOf(report) + it }
                    _statusNotice.value = "Report submitted to Momi Safety Team."
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not submit report")
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
        val nextStatus = if (removeContent) "REMOVED" else "DISMISSED"
        val note = resolutionNote.ifBlank {
            if (removeContent) "Removed violating content" else "Reviewed and cleared"
        }
        viewModelScope.launch {
            if (removeContent) {
                when (report.targetType.uppercase()) {
                    "POST" -> if (!report.targetId.startsWith("showcase_")) {
                        supabaseBackend.deletePost(report.targetId)
                        supaPosts.update { list -> list.filterNot { it.id == report.targetId } }
                    }
                    "PRODUCT" -> {
                        supabaseBackend.deleteProduct(report.targetId)
                        supaProducts.update { list -> list.filterNot { it.id == report.targetId } }
                    }
                }
            }
            val updated = report.copy(
                status = nextStatus,
                reviewedByUid = reviewer.userId,
                reviewedByUsername = reviewer.username,
                reviewedByRole = role,
                resolutionNote = note
            )
            supabaseBackend.upsertReport(updated)
                .onSuccess {
                    supaContentReports.update { list -> list.map { if (it.id == updated.id) updated else it } }
                    _statusNotice.value = if (removeContent) "Content removed & report closed." else "Report dismissed."
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update report status")
                }
        }
    }

    fun toggleNotificationFollow(notification: NotificationItem) {
        toggleFollow(notification.actorUid, notification.actorUsername, notification.isFollowingBack)
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            supaNotifications.update { list -> list.map { it.copy(isUnread = false) } }
            supabaseBackend.markNotificationsRead(currentUserId)
        }
    }

    fun startDirectChat(peerProfile: UserProfile, onOpened: (MessageThread) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val sorted = listOf(currentUserId, peerProfile.userId).sorted()
            val threadId = "${sorted[0]}__${sorted[1]}"
            val existing = supaThreads.value.firstOrNull { it.id == threadId }
            if (existing != null) {
                onOpened(existing)
                return@launch
            }
            val newThread = MessageThread(
                id = threadId,
                participantUids = sorted,
                userAId = currentUserId,
                userAUsername = currentProfile.username,
                userADisplayName = currentProfile.displayName,
                userAAvatarKey = currentProfile.avatarKey,
                userBId = peerProfile.userId,
                userBUsername = peerProfile.username,
                userBDisplayName = peerProfile.displayName,
                userBAvatarKey = peerProfile.avatarKey,
                lastMessage = "",
                lastSenderUid = "",
                unreadCount = 0L,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.upsertThread(newThread)
                .onSuccess {
                    supaThreads.update { list -> listOf(newThread) + list.filterNot { it.id == threadId } }
                    onOpened(newThread)
                }
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
                    avatarUrl = m.avatarUrl,
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
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val msg = ChatMessage(
                id = "msg_${UUID.randomUUID()}",
                participantUids = thread.participantUids.ifEmpty { listOf(thread.userAId, thread.userBId).filter { it.isNotBlank() } },
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                messageType = "TEXT",
                text = trimmed.take(1000),
                createdAt = Timestamp.now()
            )
            val updatedThread = thread.copy(
                lastMessage = msg.text.take(80),
                lastSenderUid = currentUserId,
                unreadUid = thread.peerUidFor(currentUserId),
                unreadCount = thread.unreadCount + 1L,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.sendChatMessage(updatedThread, msg)
                .onSuccess {
                    supaChatMessagesByThread.update { map -> map + (thread.id to (map[thread.id].orEmpty() + msg)) }
                    supaThreads.update { list -> listOf(updatedThread) + list.filterNot { it.id == thread.id } }
                }
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not send message")
                }
        }
    }

    fun sendPhotoMessage(thread: MessageThread, localImagePath: String) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val msgId = "msg_${UUID.randomUUID()}"
            val imageUrl = supabaseBackend.uploadImageToStorage("momi-media", "chat/${thread.id}_$msgId.jpg", localImagePath)
            val imageData = if (imageUrl == null) {
                withContext(Dispatchers.IO) { MediaAssets.encodeImageFileToBase64(localImagePath) }
            } else null
            if (imageUrl == null && imageData.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = "Could not upload photo for chat."
                return@launch
            }
            val msg = ChatMessage(
                id = msgId,
                participantUids = thread.participantUids.ifEmpty { listOf(thread.userAId, thread.userBId).filter { it.isNotBlank() } },
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                messageType = "IMAGE",
                text = "Photo",
                mediaUrl = imageUrl,
                imageData = imageData,
                createdAt = Timestamp.now()
            )
            val updatedThread = thread.copy(
                lastMessage = "Sent a photo",
                lastSenderUid = currentUserId,
                unreadUid = thread.peerUidFor(currentUserId),
                unreadCount = thread.unreadCount + 1L,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.sendChatMessage(updatedThread, msg)
                .onSuccess {
                    supaChatMessagesByThread.update { map -> map + (thread.id to (map[thread.id].orEmpty() + msg)) }
                    supaThreads.update { list -> listOf(updatedThread) + list.filterNot { it.id == thread.id } }
                }
                .onFailure { _errorMessage.value = unwrapErrorMessage(it, "Could not send photo") }
            _isSendingChatMedia.value = false
        }
    }

    fun sendVideoMessage(context: Context, thread: MessageThread, localVideoPath: String, durationMs: Long) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val msgId = "msg_${UUID.randomUUID()}"
            val rawVideo = MediaAssets.readUploadBytesUpToLimit(localVideoPath, 16_000_000L)
            val videoUrl = if (rawVideo != null) {
                supabaseClient.uploadStorageBytes("momi-videos", "chat/${thread.id}_$msgId.mp4", rawVideo, "video/mp4").getOrNull()
            } else null
            val videoData = if (videoUrl == null) {
                MediaAssets.prepareCompactVideoForChat(context.applicationContext, localVideoPath).getOrNull()
            } else null
            if (videoUrl == null && videoData.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = "Video clip is too large for chat."
                return@launch
            }
            val msg = ChatMessage(
                id = msgId,
                participantUids = thread.participantUids.ifEmpty { listOf(thread.userAId, thread.userBId).filter { it.isNotBlank() } },
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                messageType = "VIDEO",
                text = "Video clip",
                mediaUrl = videoUrl,
                videoData = videoData,
                videoDurationMs = durationMs,
                createdAt = Timestamp.now()
            )
            val updatedThread = thread.copy(
                lastMessage = "Sent a video",
                lastSenderUid = currentUserId,
                unreadUid = thread.peerUidFor(currentUserId),
                unreadCount = thread.unreadCount + 1L,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.sendChatMessage(updatedThread, msg)
                .onSuccess {
                    supaChatMessagesByThread.update { map -> map + (thread.id to (map[thread.id].orEmpty() + msg)) }
                    supaThreads.update { list -> listOf(updatedThread) + list.filterNot { it.id == thread.id } }
                }
                .onFailure { _errorMessage.value = unwrapErrorMessage(it, "Could not send video") }
            _isSendingChatMedia.value = false
        }
    }

    fun sendVoiceMessage(context: Context, thread: MessageThread, localAudioPath: String, durationMs: Long) {
        val currentProfile = fallbackProfile()
        _isSendingChatMedia.value = true
        viewModelScope.launch {
            val msgId = "msg_${UUID.randomUUID()}"
            val rawAudio = StudioMusicCatalog.readAudioUploadBytes(localAudioPath)
            val audioUrl = if (rawAudio != null) {
                supabaseClient.uploadStorageBytes("momi-media", "chat/${thread.id}_$msgId.m4a", rawAudio, "audio/mp4").getOrNull()
            } else null
            val audioData = if (audioUrl == null) {
                StudioMusicCatalog.encodeAudioForFirestore(context.applicationContext, localAudioPath)
            } else null
            runCatching { File(localAudioPath).delete() }
            if (audioUrl == null && audioData.isNullOrBlank()) {
                _isSendingChatMedia.value = false
                _errorMessage.value = "Voice clip could not be encoded."
                return@launch
            }
            val msg = ChatMessage(
                id = msgId,
                participantUids = thread.participantUids.ifEmpty { listOf(thread.userAId, thread.userBId).filter { it.isNotBlank() } },
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                messageType = "AUDIO",
                text = "Voice message",
                mediaUrl = audioUrl,
                audioData = audioData,
                audioDurationMs = durationMs,
                createdAt = Timestamp.now()
            )
            val updatedThread = thread.copy(
                lastMessage = "Voice message",
                lastSenderUid = currentUserId,
                unreadUid = thread.peerUidFor(currentUserId),
                unreadCount = thread.unreadCount + 1L,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.sendChatMessage(updatedThread, msg)
                .onSuccess {
                    supaChatMessagesByThread.update { map -> map + (thread.id to (map[thread.id].orEmpty() + msg)) }
                    supaThreads.update { list -> listOf(updatedThread) + list.filterNot { it.id == thread.id } }
                }
                .onFailure { _errorMessage.value = unwrapErrorMessage(it, "Could not send voice note") }
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
            val existing = supaMerchants.value.firstOrNull { it.userId == currentUserId }
            val merchant = MerchantAccount(
                id = currentUserId,
                userId = currentUserId,
                ownerUsername = currentProfile.username,
                storeName = storeName.trim().take(80),
                tagline = tagline.trim().take(160),
                category = category.trim().take(60),
                location = location.trim().take(80),
                avatarKey = currentProfile.avatarKey,
                avatarUrl = currentProfile.avatarUrl,
                avatarData = currentProfile.avatarData,
                isVerified = existing?.isVerified ?: false,
                createdAt = existing?.createdAt ?: Timestamp.now()
            )
            supabaseBackend.upsertMerchant(merchant)
                .onSuccess {
                    supaMerchants.update { list -> listOf(merchant) + list.filterNot { it.userId == merchant.userId } }
                    _statusNotice.value = "Merchant storefront saved!"
                    onComplete(true)
                }
                .onFailure {
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
            val adminEmail = initialEmail ?: "syali6511@gmail.com"
            supabaseClient.patchRows(
                "merchants",
                "user_id=eq.${java.net.URLEncoder.encode(merchantUid, "UTF-8")}",
                mapOf("is_verified" to verified, "verified_by_email" to adminEmail)
            ).onSuccess {
                supaMerchants.update { list -> list.map { if (it.userId == merchantUid) it.copy(isVerified = verified) else it } }
            }.onFailure {
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
            supabaseClient.patchRows(
                "market_products",
                "id=eq.${java.net.URLEncoder.encode(productId, "UTF-8")}",
                mapOf("is_featured" to featured)
            ).onSuccess {
                supaProducts.update { list -> list.map { if (it.id == productId) it.copy(isFeatured = featured) else it } }
                _statusNotice.value = if (featured) "Added to Editor's Picks!" else "Removed from Editor's Picks."
            }.onFailure {
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
            val productId = "prod_${UUID.randomUUID()}"
            val uploaded = supabaseBackend.prepareAndUploadPostMedia(
                context = context.applicationContext,
                mediaId = productId,
                localImagePath = cleanPaths.firstOrNull(),
                localExtraImagePaths = cleanPaths.drop(1),
                mediaType = if (!localVideoPath.isNullOrBlank()) "VIDEO" else "IMAGE",
                localVideoPath = localVideoPath,
                localAudioPath = null
            )
            if (uploaded.failureReason != null ||
                (!localVideoPath.isNullOrBlank() && uploaded.videoUrl == null && uploaded.videoDataFallback == null)
            ) {
                _errorMessage.value = uploaded.failureReason ?: "Could not prepare product video."
                onComplete(false)
                return@launch
            }
            val product = MarketProduct(
                id = productId,
                sellerUid = currentUserId,
                sellerUsername = currentProfile.username,
                storeName = merchant.storeName,
                sellerVerified = merchant.isVerified,
                title = title.trim().take(100),
                description = description.trim().take(1000),
                category = category,
                priceUsd = priceUsd,
                originalPriceUsd = originalPriceUsd,
                freeShipping = freeShipping,
                imageUrl = uploaded.imageUrl,
                imageData = uploaded.imageDataFallback,
                extraImageUrls = uploaded.extraImageUrls,
                extraImageDatas = uploaded.extraImageDatasFallback,
                videoUrl = uploaded.videoUrl,
                videoData = uploaded.videoDataFallback,
                videoChunksCount = uploaded.videoChunksFallback.size.toLong(),
                videoDurationMs = videoDurationMs,
                stockCount = stockCount.coerceAtLeast(1L),
                isFeatured = false,
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertProduct(product)
                .onSuccess {
                    supaProducts.update { list -> listOf(product) + list.filterNot { it.id == product.id } }
                    _statusNotice.value = "Listing published to Momi Marketplace!"
                    onComplete(true)
                }
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not publish product listing")
                    onComplete(false)
                }
        }
    }

    fun deleteMarketProduct(productId: String) {
        viewModelScope.launch {
            supabaseBackend.deleteProduct(productId)
                .onSuccess { supaProducts.update { list -> list.filterNot { it.id == productId } } }
                .onFailure { _errorMessage.value = unwrapErrorMessage(it, "Could not delete listing") }
        }
    }

    fun placeMarketOrder(product: MarketProduct, shippingAddress: String, onComplete: (Boolean) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val order = MarketOrder(
                id = "ord_${UUID.randomUUID()}",
                productId = product.id,
                productTitle = product.title,
                productImageKey = product.imageKey,
                productImageUrl = product.imageUrl,
                productImageData = product.imageData,
                priceUsd = product.priceUsd,
                buyerUid = currentUserId,
                buyerUsername = currentProfile.username,
                sellerUid = product.sellerUid,
                sellerStoreName = product.storeName,
                shippingAddress = shippingAddress.trim().take(240),
                status = "PLACED",
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertOrder(order)
                .onSuccess {
                    supaOrders.update { list -> listOf(order) + list }
                    supabaseBackend.sendNotification(
                        NotificationItem(
                            id = "ord_${order.id}",
                            userId = product.sellerUid,
                            actorUid = currentUserId,
                            actorUsername = currentProfile.username,
                            actorAvatarKey = currentProfile.avatarKey,
                            message = "ordered ${product.title}",
                            timeLabel = "Just now",
                            section = "Today",
                            previewImageKey = product.imageKey,
                            isUnread = true,
                            createdAt = Timestamp.now()
                        )
                    )
                    _statusNotice.value = "Order placed with ${product.storeName}!"
                    onComplete(true)
                }
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not place order")
                    onComplete(false)
                }
        }
    }

    fun updateMarketOrderStatus(orderId: String, nextStatus: String) {
        viewModelScope.launch {
            supabaseClient.patchRows(
                "market_orders",
                "id=eq.${java.net.URLEncoder.encode(orderId, "UTF-8")}",
                mapOf("status" to nextStatus)
            ).onSuccess {
                supaOrders.update { list -> list.map { if (it.id == orderId) it.copy(status = nextStatus) else it } }
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update order status")
            }
        }
    }

    fun clearThreadUnread(thread: MessageThread) {
        viewModelScope.launch {
            supabaseClient.patchRows(
                "threads",
                "id=eq.${java.net.URLEncoder.encode(thread.id, "UTF-8")}",
                mapOf("unread_count" to 0L)
            )
            supaThreads.update { list -> list.map { if (it.id == thread.id) it.copy(unreadCount = 0L) else it } }
        }
    }

    fun recordPostView(postId: String) {
        if (postId.isBlank()) return
        val post = allPosts.value.firstOrNull { it.id == postId }
        val shouldRecord = synchronized(recordedPostViewIds) {
            recordedPostViewIds.add(postId)
        }
        if (!shouldRecord) return
        val nextViews = (post?.viewsCount ?: 0L) + 1L
        pendingMinViews[postId] = nextViews to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
        viewModelScope.launch {
            supaPosts.update { list -> list.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, nextViews)) else it } }
            supaPostViewersByPostId.update { map ->
                val cur = map[postId].orEmpty().toMutableList()
                if (currentUserId !in cur) cur.add(0, currentUserId)
                map + (postId to cur)
            }
            supabaseBackend.setInteraction(currentUserId, "VIEWED_POST", postId, true)
            supabaseBackend.updatePostCounts(postId, viewsCount = nextViews)
        }
    }

    fun startLiveStream(title: String, category: String = "Live", onStarted: (Result<String>) -> Unit) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val now = Timestamp.now()
            val streamId = currentUserId
            val stream = LiveStream(
                id = streamId,
                userId = streamId,
                username = currentProfile.username,
                displayName = currentProfile.displayName,
                avatarKey = currentProfile.avatarKey,
                avatarUrl = currentProfile.avatarUrl,
                authorVerified = currentProfile.isVerified,
                title = title.trim().ifBlank { "@${currentProfile.username} is live" },
                category = category,
                viewerCount = 0L,
                likesCount = 0L,
                giftsCredits = 0L,
                isLive = true,
                createdAt = now,
                updatedAt = now
            )
            val initialFrame = LiveFrame(
                id = streamId,
                userId = streamId,
                seq = 1L,
                frameData = "",
                cameraEnabled = true,
                micMuted = false,
                updatedAt = now
            )
            val res = supabaseBackend.startLiveStream(stream, initialFrame).map { streamId }
            res.onSuccess {
                supaLiveStreams.update { list -> listOf(stream) + list.filterNot { it.id == streamId } }
                supaLiveFrameByStream.update { map -> map + (streamId to initialFrame) }
                supaLiveCommentsByStream.update { map -> map + (streamId to emptyList()) }
            }.onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Could not start live broadcast")
            }
            onStarted(res)
        }
    }

    fun endLiveStream(streamId: String = currentUserId, onEnded: () -> Unit = {}) {
        viewModelScope.launch {
            supabaseBackend.endLiveStream(streamId)
            supaLiveStreams.update { list -> list.filterNot { it.id == streamId } }
            supaLiveFrameByStream.update { map -> map - streamId }
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
        viewModelScope.launch {
            val frame = LiveFrame(
                id = streamId,
                userId = streamId,
                seq = seq,
                frameData = frameData,
                cameraEnabled = cameraEnabled,
                micMuted = micMuted,
                updatedAt = Timestamp.now()
            )
            supaLiveFrameByStream.update { map -> map + (streamId to frame) }
            supabaseBackend.publishLiveFrame(frame).onFailure { err ->
                Log.w(TAG, "Supabase live frame publish skipped", err)
            }
        }
    }

    fun joinLiveStream(streamId: String, onJoined: (Boolean) -> Unit = {}) {
        if (streamId.isBlank() || streamId == currentUserId) {
            onJoined(false)
            return
        }
        viewModelScope.launch {
            val current = supaLiveStreams.value.firstOrNull { it.id == streamId }
            val nextCount = (current?.viewerCount ?: 0L) + 1L
            supabaseBackend.updateLiveCounters(streamId, viewerCount = nextCount)
            supaLiveStreams.update { list -> list.map { if (it.id == streamId) it.copy(viewerCount = nextCount) else it } }
            onJoined(true)
        }
    }

    fun leaveLiveStream(streamId: String) {
        if (streamId.isBlank() || streamId == currentUserId) return
        viewModelScope.launch {
            val current = supaLiveStreams.value.firstOrNull { it.id == streamId }
            val nextCount = ((current?.viewerCount ?: 1L) - 1L).coerceAtLeast(0L)
            supabaseBackend.updateLiveCounters(streamId, viewerCount = nextCount)
            supaLiveStreams.update { list -> list.map { if (it.id == streamId) it.copy(viewerCount = nextCount) else it } }
        }
    }

    fun sendLiveHeart(streamId: String) {
        viewModelScope.launch {
            val current = supaLiveStreams.value.firstOrNull { it.id == streamId }
            val nextLikes = (current?.likesCount ?: 0L) + 1L
            supaLiveStreams.update { list -> list.map { if (it.id == streamId) it.copy(likesCount = nextLikes) else it } }
            supabaseBackend.updateLiveCounters(streamId, likesCount = nextLikes)
        }
    }

    fun sendLiveComment(streamId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val cmt = LiveComment(
                id = "lcmt_${UUID.randomUUID()}",
                userId = currentUserId,
                username = currentProfile.username,
                avatarKey = currentProfile.avatarKey,
                text = trimmed.take(200),
                createdAt = Timestamp.now()
            )
            supaLiveCommentsByStream.update { map -> map + (streamId to (map[streamId].orEmpty() + cmt)) }
            supabaseBackend.sendLiveComment(streamId, cmt).onFailure { err ->
                _errorMessage.value = unwrapErrorMessage(err, "Could not send live comment")
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
            val currentW = supaWallet.value ?: UserWallet(id = currentUserId, userId = currentUserId)
            val nextPurchased = currentW.totalPurchased + pkg.credits
            val txId = "tx_${UUID.randomUUID()}"
            val updatedW = currentW.copy(
                balance = currentW.balance + pkg.credits,
                totalPurchased = nextPurchased,
                accountTier = MomiCreditCatalog.tierForTotalPurchased(nextPurchased, currentW.unlockedPerks),
                lastTxId = txId,
                updatedAt = Timestamp.now()
            )
            val tx = CreditTransaction(
                id = txId,
                txId = txId,
                type = CreditTransaction.TYPE_TOP_UP,
                paymentMethod = paymentMethod,
                paymentRef = paymentRef,
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                recipientUid = currentUserId,
                recipientUsername = currentProfile.username,
                creditsAmount = pkg.credits,
                priceUsd = pkg.fallbackPriceUsd,
                status = "COMPLETED",
                recipientClaimed = true,
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertWalletAndTx(updatedW, tx)
                .onSuccess {
                    supaWallet.value = updatedW
                    supaCreditTransactions.update { listOf(tx) + it }
                    _statusNotice.value = "Added +${pkg.credits} Momi credits to your account!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not add credits to wallet")
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
            val currentW = supaWallet.value ?: UserWallet(id = currentUserId, userId = currentUserId)
            if (currentW.balance < gift.creditsCost) {
                _isProcessingWalletAction.value = false
                _errorMessage.value = "Not enough credits (need ${gift.creditsCost} cr). Top up in Wallet."
                onComplete(false)
                return@launch
            }
            val txId = "gift_${UUID.randomUUID()}"
            val updatedSenderW = currentW.copy(
                balance = currentW.balance - gift.creditsCost,
                totalSpent = currentW.totalSpent + gift.creditsCost,
                lastTxId = txId,
                updatedAt = Timestamp.now()
            )
            val tx = CreditTransaction(
                id = txId,
                txId = txId,
                type = CreditTransaction.TYPE_GIFT,
                paymentMethod = CreditTransaction.METHOD_WALLET_CREDITS,
                paymentRef = streamId ?: "",
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                recipientUid = recipientUid,
                recipientUsername = recipientUsername,
                creditsAmount = gift.creditsCost,
                giftKey = gift.key,
                giftName = "${gift.emojiLabel} ${gift.name}",
                streamId = streamId,
                status = "COMPLETED",
                recipientClaimed = true,
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertWalletAndTx(updatedSenderW, tx)
                .onSuccess {
                    supaWallet.value = updatedSenderW
                    supaCreditTransactions.update { listOf(tx) + it }
                    if (!streamId.isNullOrBlank()) {
                        val stream = supaLiveStreams.value.firstOrNull { it.id == streamId }
                        val nextCredits = (stream?.giftsCredits ?: 0L) + gift.creditsCost
                        supabaseBackend.updateLiveCounters(streamId, giftsCredits = nextCredits)
                        supaLiveStreams.update { list ->
                            list.map { if (it.id == streamId) it.copy(giftsCredits = nextCredits) else it }
                        }
                        val giftCmt = LiveComment(
                            id = "lcmt_gift_${UUID.randomUUID()}",
                            userId = currentUserId,
                            username = currentProfile.username,
                            avatarKey = currentProfile.avatarKey,
                            text = "[GIFT] sent ${gift.emojiLabel} ${gift.name} (+${gift.creditsCost} cr)",
                            createdAt = Timestamp.now()
                        )
                        supaLiveCommentsByStream.update { map -> map + (streamId to (map[streamId].orEmpty() + giftCmt)) }
                        supabaseBackend.sendLiveComment(streamId, giftCmt)
                    }
                    _statusNotice.value =
                        "Sent ${gift.emojiLabel} ${gift.name} (-${gift.creditsCost} cr) to @$recipientUsername!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not send gift")
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
            val currentW = supaWallet.value ?: UserWallet(id = currentUserId, userId = currentUserId)
            if (currentW.balance < perk.creditsCost) {
                _isProcessingWalletAction.value = false
                _errorMessage.value = "Need ${perk.creditsCost} credits to unlock ${perk.title}."
                onComplete(false)
                return@launch
            }
            val nextPerks = (currentW.unlockedPerks + perk.key).distinct()
            val txId = "perk_${UUID.randomUUID()}"
            val updatedW = currentW.copy(
                balance = currentW.balance - perk.creditsCost,
                totalSpent = currentW.totalSpent + perk.creditsCost,
                accountTier = MomiCreditCatalog.tierForTotalPurchased(currentW.totalPurchased, nextPerks),
                verificationTier = MomiCreditCatalog.verificationTierForPerks(currentW.verificationTier, nextPerks),
                unlockedPerks = nextPerks,
                lastTxId = txId,
                updatedAt = Timestamp.now()
            )
            val tx = CreditTransaction(
                id = txId,
                txId = txId,
                type = CreditTransaction.TYPE_PERK_UNLOCK,
                paymentMethod = CreditTransaction.METHOD_WALLET_CREDITS,
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                recipientUid = currentUserId,
                recipientUsername = currentProfile.username,
                creditsAmount = perk.creditsCost,
                perkKey = perk.key,
                status = "COMPLETED",
                recipientClaimed = true,
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertWalletAndTx(updatedW, tx)
                .onSuccess {
                    supaWallet.value = updatedW
                    supaCreditTransactions.update { listOf(tx) + it }
                    _statusNotice.value = "Unlocked ${perk.title}!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not unlock perk")
                    onComplete(false)
                }
            _isProcessingWalletAction.value = false
        }
    }

    fun showInfoNotice(message: String) {
        _statusNotice.value = message
    }

    fun saveAdMonetizationConfig(
        config: AdMonetizationConfig,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to update monetizer settings."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            val adminEmail = initialEmail ?: "syali6511@gmail.com"
            val finalCfg = config.copy(updatedByEmail = adminEmail, updatedAt = Timestamp.now())
            supabaseBackend.saveAppConfig(finalCfg)
                .onSuccess {
                    supaMonetizationConfig.value = finalCfg
                    cachedMonetizationConfig.value = finalCfg
                    _statusNotice.value = "Updated AdMob & Start.io config for Android & Web!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not save monetizer settings")
                    onComplete(false)
                }
            _isProcessingWalletAction.value = false
        }
    }

    fun updatePartnerConsent(
        consented: Boolean,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            supabaseClient.patchRows(
                "profiles",
                "user_id=eq.${java.net.URLEncoder.encode(currentUserId, "UTF-8")}",
                mapOf("partner_consent" to consented)
            ).onSuccess {
                supaProfiles.update { list -> list.map { if (it.userId == currentUserId) it.copy(partnerConsent = consented) else it } }
                _statusNotice.value = if (consented) {
                    "Partner Boost & Perks Consent enabled — you're eligible for owner grants & featured perks!"
                } else {
                    "Partner Boost & Perks Consent turned off."
                }
                onComplete(true)
            }.onFailure { err ->
                _errorMessage.value = unwrapErrorMessage(err, "Could not update consent preference")
                onComplete(false)
            }
        }
    }

    fun boostPostWithCredits(
        post: Post,
        option: PostBoostOption,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            val currentW = supaWallet.value ?: UserWallet(id = currentUserId, userId = currentUserId)
            if (currentW.balance < option.creditsCost) {
                _isProcessingWalletAction.value = false
                _errorMessage.value = "Need ${option.creditsCost} credits to boost post."
                onComplete(false)
                return@launch
            }
            val txId = "boost_${UUID.randomUUID()}"
            val updatedW = currentW.copy(
                balance = currentW.balance - option.creditsCost,
                totalSpent = currentW.totalSpent + option.creditsCost,
                lastTxId = txId,
                updatedAt = Timestamp.now()
            )
            val tx = CreditTransaction(
                id = txId,
                txId = txId,
                type = CreditTransaction.TYPE_POST_BOOST,
                paymentMethod = CreditTransaction.METHOD_WALLET_CREDITS,
                paymentRef = post.id,
                postId = post.id,
                senderUid = currentUserId,
                senderUsername = currentProfile.username,
                recipientUid = currentUserId,
                recipientUsername = currentProfile.username,
                creditsAmount = option.creditsCost,
                status = "COMPLETED",
                recipientClaimed = true,
                createdAt = Timestamp.now()
            )
            supabaseBackend.upsertWalletAndTx(updatedW, tx)
                .mapCatching {
                    supabaseClient.patchRows(
                        "posts",
                        "id=eq.${java.net.URLEncoder.encode(post.id, "UTF-8")}",
                        mapOf("is_boosted" to true, "boost_tier" to option.tier)
                    ).getOrThrow()
                }
                .onSuccess {
                    supaWallet.value = updatedW
                    supaCreditTransactions.update { listOf(tx) + it }
                    supaPosts.update { list -> list.map { if (it.id == post.id) it.copy(isBoosted = true, boostTier = option.tier) else it } }
                    _statusNotice.value = "${option.title} activated (-${option.creditsCost} cr)! Your post is now promoted."
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not boost post")
                    onComplete(false)
                }
            _isProcessingWalletAction.value = false
        }
    }

    fun adminSetPostRecommended(
        post: Post,
        recommended: Boolean,
        label: String = "Recommended by Momi",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to recommend posts."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val finalLabel = if (recommended) label else ""
            supabaseClient.patchRows(
                "posts",
                "id=eq.${java.net.URLEncoder.encode(post.id, "UTF-8")}",
                mapOf("recommended_label" to finalLabel)
            ).onSuccess {
                supaPosts.update { list -> list.map { if (it.id == post.id) it.copy(recommendedLabel = finalLabel) else it } }
                _statusNotice.value = if (recommended) {
                    "Post by @${post.authorUsername} marked \"$label\"!"
                } else {
                    "Removed recommendation badge from @${post.authorUsername}'s post."
                }
                onComplete(true)
            }.onFailure { err ->
                _errorMessage.value = unwrapErrorMessage(err, "Could not update post recommendation")
                onComplete(false)
            }
        }
    }

    fun adminGrantConsentingUser(
        targetUser: UserProfile,
        creditsAmount: Long,
        perk: AccountPerkItem? = null,
        grantVerified: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to grant perks or credits."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _isProcessingWalletAction.value = true
            val adminProfile = fallbackProfile()
            val txId = "grant_${UUID.randomUUID()}"
            val tx = CreditTransaction(
                id = txId,
                txId = txId,
                type = CreditTransaction.TYPE_OWNER_GRANT,
                paymentMethod = CreditTransaction.METHOD_OWNER_ADMIN,
                senderUid = currentUserId,
                senderUsername = adminProfile.username,
                recipientUid = targetUser.userId,
                recipientUsername = targetUser.username,
                creditsAmount = creditsAmount.coerceAtLeast(0L),
                perkKey = perk?.key,
                status = "COMPLETED",
                recipientClaimed = true,
                createdAt = Timestamp.now()
            )
            val targetW = UserWallet(
                id = targetUser.userId,
                userId = targetUser.userId,
                balance = creditsAmount.coerceAtLeast(0L),
                totalEarned = creditsAmount.coerceAtLeast(0L),
                unlockedPerks = listOfNotNull(perk?.key),
                lastTxId = txId,
                updatedAt = Timestamp.now()
            )
            supabaseBackend.upsertWalletAndTx(targetW, tx)
                .onSuccess {
                    if (grantVerified) {
                        supabaseClient.patchRows(
                            "profiles",
                            "user_id=eq.${java.net.URLEncoder.encode(targetUser.userId, "UTF-8")}",
                            mapOf("is_verified" to true)
                        )
                        supaProfiles.update { list -> list.map { if (it.userId == targetUser.userId) it.copy(isVerified = true) else it } }
                    }
                    _statusNotice.value = "Granted rewards to @${targetUser.username}!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not send owner grant")
                    onComplete(false)
                }
            _isProcessingWalletAction.value = false
        }
    }

    fun publishShowcaseActivityBurst() {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to manage starter showcase accounts."
            return
        }
        if (_isShowcaseActionBusy.value) return
        viewModelScope.launch {
            _isShowcaseActionBusy.value = true
            supabaseBackend.publishShowcaseBurst()
                .onSuccess { count ->
                    refreshFromSupabase(forceEnsureProfile = false)
                    _statusNotice.value = "Published $count starter showcase posts, comments & stories!"
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not publish starter showcase activity")
                }
            _isShowcaseActionBusy.value = false
        }
    }

    fun engageRecentPostsWithShowcase() {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to trigger starter engagement."
            return
        }
        if (_isShowcaseActionBusy.value) return
        viewModelScope.launch {
            _isShowcaseActionBusy.value = true
            val recentPosts = allPosts.value.take(6)
            if (recentPosts.isEmpty()) {
                _statusNotice.value = "No posts available yet — publish a showcase burst first."
                _isShowcaseActionBusy.value = false
                return@launch
            }
            recentPosts.forEachIndexed { idx, p ->
                val nextLikes = p.likesCount + 2L
                supabaseBackend.updatePostCounts(p.id, likesCount = nextLikes)
                val persona = ShowcaseCatalog.PERSONAS[idx % ShowcaseCatalog.PERSONAS.size]
                val comment = Comment(
                    id = "showcase_eng_${System.currentTimeMillis()}_$idx",
                    userId = "showcase_${persona.username}",
                    authorUsername = persona.username,
                    authorAvatarKey = persona.avatarKey,
                    text = ShowcaseCatalog.COMMENTS[idx % ShowcaseCatalog.COMMENTS.size],
                    likesCount = 1L,
                    createdAt = Timestamp.now()
                )
                supabaseBackend.addComment(
                    p.id,
                    UserProfile(
                        id = comment.userId,
                        userId = comment.userId,
                        username = persona.username,
                        displayName = persona.displayName,
                        avatarKey = persona.avatarKey
                    ),
                    comment,
                    p.commentsCount + 1L
                )
            }
            refreshFromSupabase(forceEnsureProfile = false)
            _statusNotice.value = "Added starter likes & comments across ${recentPosts.size} recent posts!"
            _isShowcaseActionBusy.value = false
        }
    }

    fun removeAllShowcaseContent() {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to remove starter showcase accounts."
            return
        }
        if (_isShowcaseActionBusy.value) return
        viewModelScope.launch {
            _isShowcaseActionBusy.value = true
            supabaseBackend.removeAllShowcase()
                .onSuccess {
                    supaPosts.update { list -> list.filterNot { it.id.startsWith("showcase_") } }
                    supaStories.update { list -> list.filterNot { it.id.startsWith("showcase_") } }
                    _statusNotice.value = "Removed starter showcase posts & stories."
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not remove starter showcase accounts")
                }
            _isShowcaseActionBusy.value = false
        }
    }

    fun testSupabaseConnection() {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to run Supabase diagnostics."
            return
        }
        if (_isSupabaseBusy.value) return
        viewModelScope.launch {
            _isSupabaseBusy.value = true
            val result = supabaseClient.checkConnection()
            _supabaseStatus.value = result.message
            if (result.connected) {
                refreshFromSupabase(forceEnsureProfile = false)
                _statusNotice.value = result.message
            } else if (result.configured) {
                _errorMessage.value = result.message
            } else {
                _statusNotice.value = result.message
            }
            _isSupabaseBusy.value = false
        }
    }

    fun buildSupabaseMigrationSnapshotJson(): String =
        MomiSupabaseSchema.buildMigrationBundleJson(
            profiles = allProfiles.value.ifEmpty { cachedProfiles.value },
            posts = allPosts.value.ifEmpty { cachedRawPosts.value },
            stories = stories.value.ifEmpty { cachedStories.value },
            merchants = merchants.value.ifEmpty { cachedMerchants.value },
            products = marketProducts.value.ifEmpty { cachedProducts.value },
            wallet = wallet.value ?: cachedWallet.value,
            monetizationConfig = monetizationConfig.value
        )

    fun dismissInAppAnnouncement(announcementId: String) {
        _dismissedAnnouncementId.value = announcementId
    }

    fun publishInAppAnnouncement(
        title: String,
        body: String,
        latestVersionName: String,
        releaseNotes: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to publish announcements."
            onComplete(false)
            return
        }
        val cleanTitle = title.trim().take(120)
        val cleanBody = body.trim().take(500)
        if (cleanTitle.isBlank() && cleanBody.isBlank()) {
            _errorMessage.value = "Enter an announcement title or message."
            onComplete(false)
            return
        }
        val annId = "ann_${System.currentTimeMillis()}"
        val updatedCfg = monetizationConfig.value.copy(
            announcementId = annId,
            announcementTitle = cleanTitle,
            announcementBody = cleanBody,
            latestVersionName = latestVersionName.trim().ifBlank { BuildConfig.VERSION_NAME }.take(24),
            releaseNotes = releaseNotes.trim().take(400),
            updatedByEmail = initialEmail ?: "syali6511@gmail.com",
            updatedAt = Timestamp.now()
        )
        saveAdMonetizationConfig(updatedCfg) { ok ->
            if (ok) {
                _statusNotice.value = "In-app announcement & release notice broadcast to Android + Web!"
            }
            onComplete(ok)
        }
    }

    fun mergeLegacyProfileIntoMine(
        sourceProfile: UserProfile,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!isOwnerAccount) {
            onComplete(false, "Only the owner account can merge legacy profiles.")
            return
        }
        val target = (profile.value ?: fallbackProfileBase()).copy(
            id = currentUserId,
            userId = currentUserId
        )
        if (sourceProfile.userId.isBlank() || sourceProfile.userId == currentUserId) {
            onComplete(false, "Select a different legacy profile to merge.")
            return
        }
        viewModelScope.launch {
            _isSupabaseBusy.value = true
            supabaseClient.mergeLegacyProfileIntoUser(sourceProfile.userId, target)
                .onSuccess { movedPosts ->
                    supaPosts.update { list ->
                        list.map { p ->
                            if (p.userId == sourceProfile.userId) {
                                p.copy(
                                    userId = target.userId,
                                    authorUsername = target.username,
                                    authorDisplayName = target.displayName,
                                    authorAvatarKey = target.avatarKey,
                                    authorAvatarUrl = target.avatarUrl,
                                    authorVerified = target.isVerified
                                )
                            } else p
                        }
                    }
                    supaStories.update { list ->
                        list.map { s ->
                            if (s.userId == sourceProfile.userId) {
                                s.copy(
                                    userId = target.userId,
                                    username = target.username,
                                    avatarKey = target.avatarKey,
                                    avatarUrl = target.avatarUrl
                                )
                            } else s
                        }
                    }
                    supaProfiles.update { list -> list.filterNot { it.userId == sourceProfile.userId } }
                    refreshFromSupabase(forceEnsureProfile = false)
                    val msg = "Merged @$sourceProfile.username ($movedPosts posts) into @${target.username}!"
                        .replace("@$sourceProfile.username", "@${sourceProfile.username}")
                    _statusNotice.value = msg
                    onComplete(true, msg)
                }
                .onFailure { err ->
                    val msg = unwrapErrorMessage(err, "Could not merge legacy profile")
                    _errorMessage.value = msg
                    onComplete(false, msg)
                }
            _isSupabaseBusy.value = false
        }
    }

    fun createPage(
        name: String,
        handle: String,
        category: String,
        bio: String,
        coverKey: String = "img_post_coastal",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanName = name.trim().take(80)
        if (cleanName.isBlank()) {
            _errorMessage.value = "Please enter a Page name."
            onComplete(false)
            return
        }
        val currentProfile = fallbackProfile()
        val page = MomiPage(
            id = "page_${UUID.randomUUID()}",
            ownerUid = currentUserId,
            ownerUsername = currentProfile.username,
            name = cleanName,
            handle = handle.trim().lowercase().replace(Regex("[^a-z0-9._]"), "").ifBlank { cleanName.lowercase().replace(Regex("[^a-z0-9]"), "") }.take(40),
            category = category.trim().ifBlank { "Creator" }.take(40),
            bio = bio.trim().take(300),
            coverKey = coverKey,
            isVerified = currentProfile.isVerified,
            followersCount = 1L,
            isFollowing = true,
            createdAt = Timestamp.now()
        )
        viewModelScope.launch {
            supaPages.update { listOf(page) + it }
            supaPageFollowedIds.update { it + page.id }
            supabaseBackend.upsertPage(page)
                .onSuccess {
                    _statusNotice.value = "Created Page \"${page.name}\"!"
                    onComplete(true)
                }
                .onFailure { err ->
                    supaPages.update { list -> list.filterNot { it.id == page.id } }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not create Page")
                    onComplete(false)
                }
        }
    }

    fun toggleFollowPage(page: MomiPage) {
        val currentProfile = fallbackProfile()
        val currentlyFollowing = page.id in supaPageFollowedIds.value || page.isFollowing
        val optimisticCount = (page.followersCount + if (currentlyFollowing) -1L else 1L).coerceAtLeast(0L)
        supaPageFollowedIds.update { if (currentlyFollowing) it - page.id else it + page.id }
        supaPages.update { list -> list.map { if (it.id == page.id) it.copy(followersCount = optimisticCount) else it } }
        viewModelScope.launch {
            supabaseBackend.toggleFollowPage(page, currentUserId, currentProfile.username, currentlyFollowing)
                .onSuccess { confirmedCount ->
                    supaPages.update { list -> list.map { if (it.id == page.id) it.copy(followersCount = confirmedCount) else it } }
                }
                .onFailure { err ->
                    supaPageFollowedIds.update { if (currentlyFollowing) it + page.id else it - page.id }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update Page follow")
                }
        }
    }

    fun createGroup(
        name: String,
        category: String,
        description: String,
        privacy: String = "PUBLIC",
        coverKey: String = "img_post_architecture",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanName = name.trim().take(80)
        if (cleanName.isBlank()) {
            _errorMessage.value = "Please enter a Group name."
            onComplete(false)
            return
        }
        val currentProfile = fallbackProfile()
        val group = MomiGroup(
            id = "grp_${UUID.randomUUID()}",
            ownerUid = currentUserId,
            ownerUsername = currentProfile.username,
            name = cleanName,
            category = category.trim().ifBlank { "Community" }.take(40),
            description = description.trim().take(300),
            privacy = if (privacy.equals("PRIVATE", ignoreCase = true)) "PRIVATE" else "PUBLIC",
            coverKey = coverKey,
            membersCount = 1L,
            isMember = true,
            createdAt = Timestamp.now()
        )
        viewModelScope.launch {
            supaGroups.update { listOf(group) + it }
            supaGroupJoinedIds.update { it + group.id }
            supabaseBackend.upsertGroup(group, currentProfile.username)
                .onSuccess {
                    _statusNotice.value = "Created Group \"${group.name}\"!"
                    onComplete(true)
                }
                .onFailure { err ->
                    supaGroups.update { list -> list.filterNot { it.id == group.id } }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not create Group")
                    onComplete(false)
                }
        }
    }

    fun toggleJoinGroup(group: MomiGroup) {
        val currentProfile = fallbackProfile()
        val currentlyMember = group.id in supaGroupJoinedIds.value || group.isMember
        val optimisticCount = (group.membersCount + if (currentlyMember) -1L else 1L).coerceAtLeast(0L)
        supaGroupJoinedIds.update { if (currentlyMember) it - group.id else it + group.id }
        supaGroups.update { list -> list.map { if (it.id == group.id) it.copy(membersCount = optimisticCount) else it } }
        viewModelScope.launch {
            supabaseBackend.toggleJoinGroup(group, currentUserId, currentProfile.username, currentlyMember)
                .onSuccess { confirmedCount ->
                    supaGroups.update { list -> list.map { if (it.id == group.id) it.copy(membersCount = confirmedCount) else it } }
                }
                .onFailure { err ->
                    supaGroupJoinedIds.update { if (currentlyMember) it + group.id else it - group.id }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update Group membership")
                }
        }
    }

    fun authorizeShowcaseAccount(account: ShowcaseAccount, authorized: Boolean) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to authorize showcase starters."
            return
        }
        val updated = account.copy(
            isAuthorized = authorized,
            updatedByEmail = initialEmail ?: "syali6511@gmail.com"
        )
        supaShowcaseAccounts.update { list ->
            list.filterNot { it.username == updated.username } + updated
        }
        viewModelScope.launch {
            supabaseBackend.upsertShowcaseAccount(updated)
                .onSuccess {
                    _statusNotice.value = if (authorized) {
                        "Authorized @${updated.username} for posting & engagement!"
                    } else {
                        "Paused authorization for @${updated.username}."
                    }
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update starter authorization")
                }
        }
    }

    fun updateShowcasePersonaProfile(
        account: ShowcaseAccount,
        displayName: String,
        bio: String,
        category: String,
        location: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to edit showcase profiles."
            onComplete(false)
            return
        }
        val updated = account.copy(
            displayName = displayName.trim().ifBlank { account.displayName }.take(80),
            bio = bio.trim().take(240),
            category = category.trim().ifBlank { account.category }.take(40),
            location = location.trim().take(120),
            updatedByEmail = initialEmail ?: "syali6511@gmail.com"
        )
        supaShowcaseAccounts.update { list ->
            list.filterNot { it.username == updated.username } + updated
        }
        viewModelScope.launch {
            val uid = "showcase_${updated.username}"
            supabaseBackend.upsertShowcaseAccount(updated)
                .mapCatching {
                    supabaseBackend.upsertProfile(
                        UserProfile(
                            id = uid,
                            userId = uid,
                            username = updated.username,
                            displayName = updated.displayName,
                            category = updated.category,
                            bio = updated.bio,
                            avatarKey = updated.avatarKey,
                            isVerified = true
                        )
                    ).getOrThrow()
                }
                .onSuccess {
                    supaProfiles.update { list ->
                        list.map {
                            if (it.userId == uid) it.copy(
                                displayName = updated.displayName,
                                bio = updated.bio,
                                category = updated.category
                            ) else it
                        }
                    }
                    _statusNotice.value = "Updated profile for @${updated.username}!"
                    onComplete(true)
                }
                .onFailure { err ->
                    _errorMessage.value = unwrapErrorMessage(err, "Could not update showcase profile")
                    onComplete(false)
                }
        }
    }

    fun publishCustomShowcasePost(
        context: Context,
        account: ShowcaseAccount,
        caption: String,
        mediaType: String,
        imageKey: String,
        localImagePath: String?,
        localVideoPath: String?,
        isReel: Boolean,
        pageId: String? = null,
        groupId: String? = null,
        communityName: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to post as a showcase starter."
            onComplete(false)
            return
        }
        if (!account.isAuthorized) {
            _errorMessage.value = "Authorize @${account.username} first before posting."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _isShowcaseActionBusy.value = true
            val postId = "showcase_post_${UUID.randomUUID()}"
            val uploaded = supabaseBackend.prepareAndUploadPostMedia(
                context = context.applicationContext,
                mediaId = postId,
                localImagePath = localImagePath,
                localExtraImagePaths = emptyList(),
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                localAudioPath = null
            )
            if (uploaded.failureReason != null || (mediaType == "VIDEO" && uploaded.videoUrl == null && uploaded.videoDataFallback == null)) {
                _isShowcaseActionBusy.value = false
                _errorMessage.value = uploaded.failureReason ?: "Could not upload starter video."
                onComplete(false)
                return@launch
            }
            val uid = "showcase_${account.username}"
            val newPost = Post(
                id = postId,
                userId = uid,
                authorUsername = account.username,
                authorDisplayName = account.displayName,
                authorAvatarKey = account.avatarKey,
                authorVerified = true,
                location = account.location,
                imageKey = imageKey.ifBlank { "img_post_coastal" },
                imageUrl = uploaded.imageUrl,
                imageData = uploaded.imageDataFallback,
                mediaType = mediaType,
                videoUrl = uploaded.videoUrl,
                videoData = uploaded.videoDataFallback,
                caption = caption.trim().ifBlank { "New moment from @${account.username} on Momi" }.take(1000),
                likesCount = 12L,
                viewsCount = 85L,
                commentsCount = 0L,
                category = account.category.ifBlank { "For You" },
                isReel = isReel || mediaType == "VIDEO",
                pageId = pageId,
                groupId = groupId,
                communityName = communityName,
                createdAt = Timestamp.now()
            )
            pendingCreatedPosts[postId] = newPost to (System.currentTimeMillis() + MUTATION_PROTECTION_MS)
            supaPosts.update { listOf(newPost) + it }
            supabaseBackend.publishCustomShowcasePost(account, newPost)
                .onSuccess {
                    _statusNotice.value = "Published ${if (mediaType == "VIDEO") "video" else "post"} as @${account.username}!"
                    onComplete(true)
                }
                .onFailure { err ->
                    pendingCreatedPosts.remove(postId)
                    supaPosts.update { list -> list.filterNot { it.id == postId } }
                    _errorMessage.value = unwrapErrorMessage(err, "Could not publish starter post")
                    onComplete(false)
                }
            _isShowcaseActionBusy.value = false
        }
    }

    fun boostPostMetricsWithShowcase(
        post: Post,
        account: ShowcaseAccount,
        extraLikes: Long,
        extraViews: Long,
        commentText: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to manage starter engagement."
            onComplete(false)
            return
        }
        val nextLikes = (post.likesCount + extraLikes.coerceAtLeast(0L)).coerceAtLeast(0L)
        val nextViews = (post.viewsCount + extraViews.coerceAtLeast(0L)).coerceAtLeast(0L)
        val expiresAt = System.currentTimeMillis() + MUTATION_PROTECTION_MS
        pendingMinLikes[post.id] = nextLikes to expiresAt
        pendingMinViews[post.id] = nextViews to expiresAt
        viewModelScope.launch {
            _isShowcaseActionBusy.value = true
            val cleanComment = commentText.trim().take(400)
            val nextCommentsCount = post.commentsCount + if (cleanComment.isNotBlank()) 1L else 0L
            supaPosts.update { list ->
                list.map {
                    if (it.id == post.id) it.copy(
                        likesCount = nextLikes,
                        viewsCount = nextViews,
                        commentsCount = nextCommentsCount
                    ) else it
                }
            }
            val countRes = supabaseBackend.updatePostCounts(
                postId = post.id,
                likesCount = nextLikes,
                commentsCount = nextCommentsCount,
                viewsCount = nextViews
            )
            if (cleanComment.isNotBlank()) {
                val cmt = Comment(
                    id = "showcase_cmt_${UUID.randomUUID()}",
                    userId = "showcase_${account.username}",
                    authorUsername = account.username,
                    authorAvatarKey = account.avatarKey,
                    text = cleanComment,
                    likesCount = 1L,
                    createdAt = Timestamp.now()
                )
                supabaseBackend.addComment(
                    post.id,
                    UserProfile(
                        id = cmt.userId,
                        userId = cmt.userId,
                        username = account.username,
                        displayName = account.displayName,
                        avatarKey = account.avatarKey,
                        isVerified = true
                    ),
                    cmt,
                    nextCommentsCount
                )
            }
            countRes.onSuccess {
                _statusNotice.value = "Updated +$extraLikes likes & +$extraViews views as @${account.username}!"
                onComplete(true)
            }.onFailure { err ->
                _errorMessage.value = unwrapErrorMessage(err, "Could not boost post metrics")
                onComplete(false)
            }
            _isShowcaseActionBusy.value = false
        }
    }

    private var isSupabaseSyncInFlight = false

    private suspend fun refreshFromSupabase(forceEnsureProfile: Boolean = false) {
        if (!supabaseClient.isConfigured() || isSupabaseSyncInFlight) return
        isSupabaseSyncInFlight = true
        try {
            if (forceEnsureProfile) {
                val base = fallbackProfileBase()
                supabaseClient.ensureProfileExists(
                    userId = base.userId,
                    username = base.username,
                    displayName = base.displayName,
                    avatarKey = base.avatarKey,
                    avatarUrl = base.avatarUrl,
                    email = base.email
                )
            }
            supabaseClient.fetchFeedBundle(currentUserId).onSuccess { bundle ->
                val nowMs = System.currentTimeMillis()
                pendingLikes.entries.removeIf { it.value.second <= nowMs }
                pendingBookmarks.entries.removeIf { it.value.second <= nowMs }
                pendingFollowStates.entries.removeIf { it.value.second <= nowMs }
                pendingMinViews.entries.removeIf { it.value.second <= nowMs }
                pendingMinLikes.entries.removeIf { it.value.second <= nowMs }
                pendingCreatedPosts.entries.removeIf { it.value.second <= nowMs }
                pendingDeletedPostIds.entries.removeIf { it.value <= nowMs }
                if ((pendingProfileOverride?.second ?: 0L) <= nowMs) {
                    pendingProfileOverride = null
                }

                if (bundle.profiles.isNotEmpty()) {
                    val overrideProf = pendingProfileOverride?.first
                    val mergedProfiles = if (overrideProf != null) {
                        listOf(overrideProf) + bundle.profiles.filterNot { it.userId == overrideProf.userId }
                    } else {
                        bundle.profiles
                    }
                    supaProfiles.value = mergedProfiles
                    cachedProfiles.value = mergedProfiles
                    val cleanEmail = initialEmail?.trim()?.takeIf { it.isNotBlank() }
                    val ownRemote = mergedProfiles.firstOrNull { it.userId == currentUserId }
                        ?: if (cleanEmail != null) mergedProfiles.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) } else null
                    if (ownRemote != null) {
                        cachedProfile.value = ownRemote
                        cachedAppContext?.let { ctx ->
                            AppearancePrefs.saveCachedProfileJson(ctx, currentUserId, serializeProfileToJson(ownRemote))
                        }
                    }
                }

                val mergedPosts = buildList {
                    val serverIds = bundle.posts.map { it.id }.toSet()
                    pendingCreatedPosts.values.forEach { (pendingPost, _) ->
                        if (pendingPost.id !in serverIds && !pendingDeletedPostIds.containsKey(pendingPost.id)) {
                            add(pendingPost)
                        }
                    }
                    bundle.posts.forEach { post ->
                        if (!pendingDeletedPostIds.containsKey(post.id)) {
                            val minV = pendingMinViews[post.id]?.first ?: 0L
                            val minL = pendingMinLikes[post.id]?.first ?: 0L
                            add(
                                post.copy(
                                    viewsCount = maxOf(post.viewsCount, minV),
                                    likesCount = maxOf(post.likesCount, minL)
                                )
                            )
                        }
                    }
                }
                supaPosts.value = mergedPosts
                cachedRawPosts.value = mergedPosts
                cachedAppContext?.let { ctx ->
                    if (mergedPosts.isNotEmpty()) {
                        AppearancePrefs.saveCachedFeedPostsJson(ctx, serializePostsToJson(mergedPosts.take(25)))
                    }
                }

                val cutoffStoryMs = nowMs - MomiSupabaseSchema.STORY_TTL_MS
                val validStories = bundle.stories.filter { story ->
                    val createdMs = story.createdAt?.toDate()?.time
                    createdMs == null || createdMs >= cutoffStoryMs
                }
                supaStories.value = validStories
                cachedStories.value = validStories
                supaProducts.value = bundle.products
                cachedProducts.value = bundle.products
                supaMerchants.value = bundle.merchants
                cachedMerchants.value = bundle.merchants
                supaLiveStreams.value = bundle.liveStreams
                cachedLiveStreams.value = bundle.liveStreams

                val mergedFollows = bundle.follows.filterNot { edge ->
                    pendingFollowStates.containsKey(edge.id) && pendingFollowStates[edge.id]?.first == null
                }.toMutableList()
                pendingFollowStates.values.forEach { (edge, _) ->
                    if (edge != null && mergedFollows.none { it.id == edge.id }) {
                        mergedFollows.add(edge)
                    }
                }
                supaFollows.value = mergedFollows
                cachedFollows.value = mergedFollows

                val nextLikes = bundle.likedPostIds.toMutableSet()
                pendingLikes.forEach { (postId, pair) ->
                    if (pair.first) nextLikes.add(postId) else nextLikes.remove(postId)
                }
                supaLikedPostIds.value = nextLikes
                supaPostLikersByPostId.value = bundle.postLikersByPostId
                supaPostViewersByPostId.value = bundle.postViewersByPostId

                val nextBookmarks = bundle.bookmarkedPostIds.toMutableSet()
                pendingBookmarks.forEach { (postId, pair) ->
                    if (pair.first) nextBookmarks.add(postId) else nextBookmarks.remove(postId)
                }
                supaBookmarkedPostIds.value = nextBookmarks

                supaSeenStoryIds.value = bundle.seenStoryIds
                supaLikedCommentIds.value = bundle.likedCommentIds
                supaNotifications.value = bundle.notifications
                cachedNotifications.value = bundle.notifications
                supaThreads.value = bundle.threads
                cachedThreads.value = bundle.threads
                supaOrders.value = bundle.orders
                cachedOrders.value = bundle.orders
                supaPages.value = bundle.pages
                supaPageFollowedIds.value = bundle.pageFollowedIds
                supaGroups.value = bundle.groups
                supaGroupJoinedIds.value = bundle.groupJoinedIds
                if (bundle.showcaseAccounts.isNotEmpty()) {
                    supaShowcaseAccounts.value = bundle.showcaseAccounts
                }
                if (bundle.reports.isNotEmpty() || isOwnerAccount) {
                    supaContentReports.value = bundle.reports
                    cachedContentReports.value = bundle.reports
                }
                bundle.wallet?.let {
                    supaWallet.value = it
                    cachedWallet.value = it
                }
                if (bundle.transactions.isNotEmpty()) {
                    supaCreditTransactions.value = bundle.transactions
                    cachedCreditTransactions.value = bundle.transactions
                }
                bundle.monetizationConfig?.let {
                    supaMonetizationConfig.value = it
                    cachedMonetizationConfig.value = it
                    val supabaseUpdate = AppUpdateChecker.evaluateSupabaseConfigUpdate(
                        config = it,
                        isPlayStoreInstall = knownPlayStoreInstall
                    )
                    if (supabaseUpdate != null && _availableUpdate.value == null) {
                        val ctx = cachedAppContext
                        val dismissed = ctx != null && AppUpdateChecker.isVersionDismissed(
                            ctx,
                            supabaseUpdate.latestVersionName,
                            supabaseUpdate.remoteBuildNumber
                        )
                        if (!dismissed) {
                            _availableUpdate.value = supabaseUpdate
                        }
                    }
                }
                supaHasLoadedFeed.value = true
            }
        } finally {
            isSupabaseSyncInFlight = false
        }
    }

    fun syncLiveSnapshotToSupabase() {
        if (!isOwnerAccount || !isAdminModeActive.value) {
            _errorMessage.value = "Switch to Admin View to manage Supabase migration."
            return
        }
        if (_isSupabaseBusy.value) return
        viewModelScope.launch {
            _isSupabaseBusy.value = true
            supabaseClient.syncLiveSnapshot(
                profiles = (cachedProfiles.value + supaProfiles.value).distinctBy { it.userId },
                posts = (cachedRawPosts.value + supaPosts.value).distinctBy { it.id },
                stories = (cachedStories.value + supaStories.value).distinctBy { it.id },
                merchants = (cachedMerchants.value + supaMerchants.value).distinctBy { it.userId },
                products = (cachedProducts.value + supaProducts.value).distinctBy { it.id },
                monetizationConfig = cachedMonetizationConfig.value,
                accessToken = MomiSupabaseClient.activeSession?.accessToken
            ).onSuccess { msg ->
                refreshFromSupabase(forceEnsureProfile = false)
                _supabaseStatus.value = msg
                _statusNotice.value = msg
            }.onFailure { err ->
                val msg = unwrapErrorMessage(err, "Supabase sync check failed")
                _supabaseStatus.value = msg
                _errorMessage.value = msg
            }
            _isSupabaseBusy.value = false
        }
    }

    private fun fallbackProfile(): UserProfile {
        profile.value?.let { return it }
        cachedProfile.value?.let { return it }
        return fallbackProfileBase()
    }

    private fun fallbackProfileBase(): UserProfile {
        val cleanEmail = initialEmail?.trim()?.lowercase().orEmpty()
        val fallbackName = initialDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: "Momi Creator"
        val fallbackUsername = cleanEmail.substringBefore("@")
            .lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .takeIf { it.isNotBlank() }
            ?: "momi.creator"
        return UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = fallbackUsername.take(50),
            email = cleanEmail,
            displayName = fallbackName.take(80),
            avatarKey = MediaAssets.avatarKeyForUid(currentUserId)
        )
    }

    private fun restoreLocalCaches(context: Context) {
        AppearancePrefs.loadCachedProfileJson(context, currentUserId)?.let { jsonStr ->
            parseProfileFromJson(jsonStr)?.let { cachedProf ->
                if (cachedProfile.value == null) {
                    cachedProfile.value = cachedProf
                }
                if (supaProfiles.value.none { it.userId == cachedProf.userId }) {
                    supaProfiles.update { listOf(cachedProf) + it }
                }
            }
        }
        AppearancePrefs.loadCachedFeedPostsJson(context)?.let { jsonStr ->
            val restoredPosts = parsePostsFromJson(jsonStr)
            if (restoredPosts.isNotEmpty() && cachedRawPosts.value.isEmpty()) {
                cachedRawPosts.value = restoredPosts
                if (supaPosts.value.isEmpty()) {
                    supaPosts.value = restoredPosts
                }
            }
        }
    }

    private fun serializeProfileToJson(p: UserProfile): String = JSONObject().apply {
        put("userId", p.userId)
        put("username", p.username)
        put("email", p.email)
        put("displayName", p.displayName)
        put("category", p.category)
        put("bio", p.bio)
        put("website", p.website)
        put("avatarKey", p.avatarKey)
        put("avatarUrl", p.avatarUrl ?: "")
        put("avatarData", p.avatarData?.take(60_000) ?: "")
        put("isVerified", p.isVerified)
        put("role", p.role)
    }.toString()

    private fun parseProfileFromJson(raw: String): UserProfile? = runCatching {
        val o = JSONObject(raw)
        val uid = o.optString("userId").takeIf { it.isNotBlank() } ?: return@runCatching null
        UserProfile(
            id = uid,
            userId = uid,
            username = o.optString("username", "momi.creator"),
            email = o.optString("email"),
            displayName = o.optString("displayName", "Momi Creator"),
            category = o.optString("category"),
            bio = o.optString("bio"),
            website = o.optString("website"),
            avatarKey = o.optString("avatarKey", "avatar_aria"),
            avatarUrl = o.optString("avatarUrl").takeIf { it.isNotBlank() },
            avatarData = o.optString("avatarData").takeIf { it.isNotBlank() },
            isVerified = o.optBoolean("isVerified", false),
            role = o.optString("role", MomiRoles.USER)
        )
    }.getOrNull()

    private fun serializePostsToJson(posts: List<Post>): String {
        val arr = JSONArray()
        posts.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("userId", p.userId)
                    put("authorUsername", p.authorUsername)
                    put("authorDisplayName", p.authorDisplayName)
                    put("authorAvatarKey", p.authorAvatarKey)
                    put("authorAvatarUrl", p.authorAvatarUrl ?: "")
                    put("authorVerified", p.authorVerified)
                    put("location", p.location)
                    put("imageKey", p.imageKey)
                    put("imageUrl", p.imageUrl ?: "")
                    put("mediaType", p.mediaType)
                    put("videoUrl", p.videoUrl ?: "")
                    put("videoDurationMs", p.videoDurationMs)
                    put("filterTag", p.filterTag)
                    put("aspectRatio", p.aspectRatio)
                    put("caption", p.caption)
                    put("likesCount", p.likesCount)
                    put("viewsCount", p.viewsCount)
                    put("commentsCount", p.commentsCount)
                    put("category", p.category)
                    put("isReel", p.isReel)
                    put("audioTitle", p.audioTitle)
                    put("audioUrl", p.audioUrl ?: "")
                    put("communityName", p.communityName)
                    put("createdAtMs", p.createdAt?.toDate()?.time ?: 0L)
                }
            )
        }
        return arr.toString()
    }

    private fun parsePostsFromJson(raw: String): List<Post> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val createdMs = o.optLong("createdAtMs", 0L)
                add(
                    Post(
                        id = id,
                        userId = o.optString("userId"),
                        authorUsername = o.optString("authorUsername", "momi.creator"),
                        authorDisplayName = o.optString("authorDisplayName", "Momi Creator"),
                        authorAvatarKey = o.optString("authorAvatarKey", "avatar_aria"),
                        authorAvatarUrl = o.optString("authorAvatarUrl").takeIf { it.isNotBlank() },
                        authorVerified = o.optBoolean("authorVerified", false),
                        location = o.optString("location"),
                        imageKey = o.optString("imageKey", "img_post_coastal"),
                        imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                        mediaType = o.optString("mediaType", "IMAGE"),
                        videoUrl = o.optString("videoUrl").takeIf { it.isNotBlank() },
                        videoDurationMs = o.optLong("videoDurationMs", 0L),
                        filterTag = o.optString("filterTag", "NONE"),
                        aspectRatio = o.optString("aspectRatio", "1:1"),
                        caption = o.optString("caption"),
                        likesCount = o.optLong("likesCount", 0L),
                        viewsCount = o.optLong("viewsCount", 0L),
                        commentsCount = o.optLong("commentsCount", 0L),
                        category = o.optString("category", "For You"),
                        isReel = o.optBoolean("isReel", false),
                        audioTitle = o.optString("audioTitle"),
                        audioUrl = o.optString("audioUrl").takeIf { it.isNotBlank() },
                        communityName = o.optString("communityName"),
                        createdAt = if (createdMs > 0L) Timestamp(java.util.Date(createdMs)) else null
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun isQuotaExceeded(error: Throwable?): Boolean {
        if (error == null) return false
        return generateSequence(error) { it.cause }.any { cause ->
            cause.message?.contains("quota", ignoreCase = true) == true ||
                cause.message?.contains("rate limit", ignoreCase = true) == true
        }
    }

    private fun unwrapOrRethrowCancellation(error: Throwable): Throwable {
        if (error is CancellationException) throw error
        return error
    }

    private fun unwrapErrorMessage(error: Throwable, fallback: String): String {
        if (isQuotaExceeded(error)) {
            return RATE_LIMIT_FRIENDLY_MESSAGE
        }
        return error.localizedMessage ?: fallback
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
        const val MUTATION_PROTECTION_MS = 12_000L
        const val RATE_LIMIT_FRIENDLY_MESSAGE =
            "Supabase rate limit reached briefly — cached content remains visible. Retrying shortly."
    }
}
