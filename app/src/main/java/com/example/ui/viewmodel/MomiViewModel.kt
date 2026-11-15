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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MomiViewModel(
    private val repository: MomiFirestoreRepository,
    val currentUserId: String,
    initialDisplayName: String?,
    initialEmail: String?
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    val allProfiles: StateFlow<List<UserProfile>> = repository.observeAllProfiles(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing creators", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val follows: StateFlow<List<FollowEdge>> = repository.observeFollows(currentUserId)
        .catch { error ->
            val root = unwrapOrRethrowCancellation(error)
            Log.w(TAG, "Error observing follows", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val followingUids: StateFlow<Set<String>> = follows
        .map { edges ->
            edges.filter { it.followerUid == currentUserId }.map { it.targetUid }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

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

    val allPostsState: StateFlow<StreamUiState<List<Post>>> = combine(
        repository.observeAllPosts(currentUserId),
        likedPostIds,
        bookmarkedPostIds,
        followingUids
    ) { posts, liked, bookmarked, following ->
        val enriched = posts.map { post ->
            post.copy(
                isLiked = liked.contains(post.id),
                isBookmarked = bookmarked.contains(post.id),
                isFollowingAuthor = post.userId == currentUserId || following.contains(post.userId)
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
        seenStoryIds
    ) { rawStories, seen ->
        rawStories
            .map { story ->
                story.copy(
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
            val matchesCategory = category == "For You" || post.category.equals(category, ignoreCase = true)
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

    val activePostForComments: StateFlow<Post?> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId == null) flowOf(null) else observePost(postId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val activeComments: StateFlow<List<Comment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.observeComments(currentUserId, postId),
                    likedCommentIds
                ) { comments, liked ->
                    comments.map { it.copy(isLiked = liked.contains(it.id)) }
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
        likedPostIds,
        bookmarkedPostIds,
        followingUids
    ) { rawPost, liked, bookmarked, following ->
        rawPost?.copy(
            isLiked = liked.contains(rawPost.id),
            isBookmarked = bookmarked.contains(rawPost.id),
            isFollowingAuthor = rawPost.userId == currentUserId || following.contains(rawPost.userId)
        )
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

    fun setThemePreference(preference: ThemePreference) {
        _themePreference.value = preference
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setExploreCategory(category: String) {
        _selectedExploreCategory.value = category
    }

    fun toggleLike(post: Post) {
        val currentProfile = profile.value
        viewModelScope.launch {
            repository.toggleLike(post, currentProfile).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update like")
            }
        }
    }

    fun doubleTapLike(post: Post) {
        val currentProfile = profile.value
        viewModelScope.launch {
            repository.setLiked(post, currentProfile).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not like post")
            }
        }
    }

    fun toggleBookmark(post: Post) {
        viewModelScope.launch {
            repository.toggleBookmark(post).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update bookmark")
            }
        }
    }

    fun toggleFollow(targetUid: String, targetUsername: String, currentlyFollowing: Boolean) {
        val currentProfile = profile.value ?: return
        if (targetUid.isBlank() || targetUid == currentUserId) return
        viewModelScope.launch {
            repository.toggleFollow(currentProfile, targetUid, targetUsername, currentlyFollowing)
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not update follow state")
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
        val post = activePostForComments.value ?: return
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            repository.addComment(post, currentProfile, text)
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not post comment")
                }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val postId = _activeCommentPostId.value ?: return
        viewModelScope.launch {
            repository.toggleCommentLike(postId, comment).onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not update comment like")
            }
        }
    }

    fun createPost(
        imageKey: String,
        localImagePath: String?,
        filterTag: String,
        caption: String,
        location: String,
        category: String,
        isReel: Boolean,
        onComplete: () -> Unit
    ) {
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            repository.createPost(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
                filterTag = filterTag,
                caption = caption,
                location = location,
                category = category,
                isReel = isReel
            ).onSuccess {
                onComplete()
            }.onFailure {
                _errorMessage.value = unwrapErrorMessage(it, "Could not publish post")
            }
        }
    }

    fun createStory(
        imageKey: String,
        localImagePath: String?,
        caption: String,
        onComplete: () -> Unit
    ) {
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            val encodedImage = withContext(Dispatchers.IO) {
                MediaAssets.encodeImageFileToBase64(localImagePath)
            }
            repository.createStory(
                profile = currentProfile,
                imageKey = imageKey,
                localImagePath = localImagePath,
                imageData = encodedImage,
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
        website: String
    ) {
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            repository.updateProfile(currentProfile, displayName, username, category, bio, website)
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not update profile")
                }
        }
    }

    fun toggleNotificationFollow(notification: NotificationItem) {
        val currentProfile = profile.value ?: return
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
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            repository.openOrCreateThread(currentProfile, peerProfile)
                .onSuccess(onOpened)
                .onFailure {
                    _errorMessage.value = unwrapErrorMessage(it, "Could not open chat")
                }
        }
    }

    fun sendMessage(thread: MessageThread, text: String) {
        val currentProfile = profile.value ?: return
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

    private companion object {
        const val TAG = "MomiVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
