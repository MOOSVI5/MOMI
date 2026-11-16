package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.FollowEdge
import com.example.data.model.MessageThread
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.repository.MomiFirestoreRepository
import com.example.ui.theme.AppColorScheme
import com.example.ui.theme.AppearancePrefs
import com.example.ui.theme.ThemePreference
import com.example.util.MediaAssets
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.UUID

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MomiViewModel(
    private val repository: MomiFirestoreRepository,
    val currentUserId: String,
    private val initialDisplayName: String?,
    private val initialEmail: String?
) : ViewModel() {

    val isAdminUser: Boolean = initialEmail?.trim().equals("syali6511@gmail.com", ignoreCase = true)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Optimistic UI overlays so likes, bookmarks, follows, and comments respond in 0ms
    private val optimisticPostLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticBookmarks = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticFollows = MutableStateFlow<Map<String, Pair<Boolean, String>>>(emptyMap())
    private val optimisticCommentLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticCommentsByPost = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    init {
        viewModelScope.launch {
            repository.ensureUserSeeded(
                displayNameHint = initialDisplayName,
                emailHint = initialEmail
            ).onFailure { error ->
                _errorMessage.value = unwrapErrorMessage(error, "Failed to initialize Momi profile")
            }
        }
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

    fun clearError() {
        _errorMessage.value = null
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

    fun createPost(
        context: android.content.Context,
        imageKey: String,
        localImagePath: String?,
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
        onComplete: () -> Unit
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            val (encodedVideo, videoSynced) = if (mediaType == "VIDEO" && !localVideoPath.isNullOrBlank()) {
                MediaAssets.prepareVideoDataForFirestore(context.applicationContext, localVideoPath)
            } else {
                null to false
            }
            repository.createPost(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                videoData = encodedVideo,
                videoDurationMs = videoDurationMs,
                audioTitle = audioTitle,
                localAudioPath = localAudioPath,
                filterTag = filterTag,
                caption = caption,
                location = location,
                category = category,
                isReel = isReel
            ).onSuccess {
                if (mediaType == "VIDEO" && !videoSynced) {
                    _errorMessage.value = "Video posted! Long clips play in full on this device and sync a cover preview."
                }
                onComplete()
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not publish post")
            }
        }
    }

    fun createStory(
        context: android.content.Context,
        imageKey: String,
        localImagePath: String?,
        mediaType: String,
        localVideoPath: String?,
        videoDurationMs: Long,
        audioTitle: String,
        localAudioPath: String?,
        caption: String,
        onComplete: () -> Unit
    ) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            val (encodedVideo, _) = if (mediaType == "VIDEO" && !localVideoPath.isNullOrBlank()) {
                MediaAssets.prepareVideoDataForFirestore(context.applicationContext, localVideoPath)
            } else {
                null to false
            }
            repository.createStory(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
                mediaType = mediaType,
                localVideoPath = localVideoPath,
                videoData = encodedVideo,
                videoDurationMs = videoDurationMs,
                audioTitle = audioTitle,
                localAudioPath = localAudioPath,
                caption = caption
            ).onSuccess {
                onComplete()
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not share story")
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

    fun sendMessage(thread: MessageThread, text: String) {
        val currentProfile = fallbackProfile()
        viewModelScope.launch {
            repository.sendMessage(thread, currentProfile, text).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not send message")
            }
        }
    }

    fun clearThreadUnread(thread: MessageThread) {
        viewModelScope.launch {
            repository.clearThreadUnread(thread)
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
