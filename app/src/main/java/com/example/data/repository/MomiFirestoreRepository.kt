package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.FollowEdge
import com.example.data.model.InteractionMarker
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.MessageThread
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.util.MediaAssets
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MomiFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun postsCol() = db.collection("posts")
    private fun storiesCol() = db.collection("stories")
    private fun followsCol() = db.collection("follows")
    private fun threadsCol() = db.collection("threads")
    private fun liveStreamsCol() = db.collection("liveStreams")

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = userDoc(userId)
        val path = docRef.path
        return docRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.GET, path)
                throw error
            }
    }

    fun observeAllProfiles(currentUid: String): Flow<List<UserProfile>> {
        val colRef = db.collection("users")
        val path = colRef.path
        return colRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<UserProfile> { it.userId == currentUid }
                            .thenBy { it.username }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllPosts(currentUid: String): Flow<List<Post>> {
        val colRef = postsCol()
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(Post::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<Post> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePost(currentUid: String, postId: String): Flow<Post?> {
        val docRef = postsCol().document(postId)
        val path = docRef.path
        return docRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(Post::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.GET, path)
                throw error
            }
    }

    fun observeComments(currentUid: String, postId: String): Flow<List<Comment>> {
        val colRef = postsCol().document(postId).collection("comments")
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(Comment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<Comment> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeStories(currentUid: String): Flow<List<Story>> {
        val colRef = storiesCol()
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(Story::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<Story> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeFollows(currentUid: String): Flow<List<FollowEdge>> {
        val colRef = followsCol()
        val path = colRef.path
        return colRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(FollowEdge::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeLikedPostIds(userId: String): Flow<Set<String>> =
        observeMarkerIds(userId, "likedPosts")

    fun observeBookmarkedPostIds(userId: String): Flow<Set<String>> =
        observeMarkerIds(userId, "bookmarks")

    fun observeSeenStoryIds(userId: String): Flow<Set<String>> =
        observeMarkerIds(userId, "seenStories")

    fun observeLikedCommentIds(userId: String): Flow<Set<String>> =
        observeMarkerIds(userId, "likedComments")

    private fun observeMarkerIds(userId: String, subcollection: String): Flow<Set<String>> {
        val colRef = userDoc(userId).collection(subcollection)
        val path = colRef.path
        return colRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(InteractionMarker::class.java, ServerTimestampBehavior.ESTIMATE)
                    .map { it.targetId.ifBlank { it.id } }
                    .toSet()
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> {
        val colRef = userDoc(userId).collection("notifications")
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<NotificationItem> { it.isUnread }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMessageThreads(userId: String): Flow<List<MessageThread>> {
        val colRef = threadsCol()
        val path = colRef.path
        return colRef
            .whereArrayContains("participantUids", userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(MessageThread::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<MessageThread> { it.isUnreadFor(userId) }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeChatMessages(userId: String, threadId: String): Flow<List<ChatMessage>> {
        val colRef = threadsCol().document(threadId).collection("messages")
        val path = colRef.path
        return colRef
            .whereArrayContains("participantUids", userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<ChatMessage> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getPostById(postId: String): Result<Post?> = runCatching {
        requireUserId()
        val docRef = postsCol().document(postId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(Post::class.java, ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun ensureUserSeeded(
        displayNameHint: String? = null,
        emailHint: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = userDoc(uid)
        val existingProfile = try {
            profileRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, profileRef.path)
            throw e
        }

        if (!existingProfile.exists()) {
            val rawDisplay = displayNameHint?.trim()?.takeIf { it.isNotBlank() } ?: "Momi Creator"
            val displayName = rawDisplay.take(80)
            val username = deriveUsername(emailHint, rawDisplay)
            val avatarKey = MediaAssets.avatarKeyForUid(uid)

            try {
                profileRef.set(
                    mapOf(
                        "userId" to uid,
                        "username" to username,
                        "displayName" to displayName,
                        "category" to "Creator · Momi",
                        "bio" to "Sharing moments in silk blue & maroon.",
                        "website" to "momi.photo/$username",
                        "avatarKey" to avatarKey,
                        "followersCount" to 0L,
                        "followingCount" to 0L,
                        "isVerified" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, profileRef.path)
                throw e
            }
        }

        // Seed starter showcase posts only once if the global feed has zero posts yet
        val existingAnyPost = try {
            postsCol().limit(1).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, postsCol().path)
            throw e
        }

        if (existingAnyPost.isEmpty) {
            seedGlobalShowcasePosts(uid)
        }
    }

    private suspend fun seedGlobalShowcasePosts(seedCreatorUid: String) {
        val batch = db.batch()
        val showcasePosts = listOf(
            "showcase_post_1" to mapOf(
                "userId" to seedCreatorUid,
                "authorUsername" to "elena.maris",
                "authorDisplayName" to "Elena Maris",
                "authorAvatarKey" to "avatar_elena",
                "authorVerified" to true,
                "location" to "Positano, Amalfi Coast",
                "imageKey" to "img_post_coastal",
                "localImagePath" to null,
                "imageData" to null,
                "filterTag" to "NONE",
                "caption" to "Golden hour spilling over the cliffs before the ferries drift back in. #amalfi #coastal #momi",
                "likesCount" to 24L,
                "viewsCount" to 128L,
                "commentsCount" to 0L,
                "category" to "Travel",
                "isReel" to true,
                "audioTitle" to "Elena Maris · Mediterranean Breeze",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "showcase_post_2" to mapOf(
                "userId" to seedCreatorUid,
                "authorUsername" to "matcha.ritual",
                "authorDisplayName" to "Matcha Ritual",
                "authorAvatarKey" to "avatar_matcha",
                "authorVerified" to true,
                "location" to "Lisbon, Chiado",
                "imageKey" to "img_post_coffee",
                "localImagePath" to null,
                "imageData" to null,
                "filterTag" to "NONE",
                "caption" to "Morning ritual: stone-ground Uji matcha over crystal ice and warm pastry. #matcha #cafeculture",
                "likesCount" to 18L,
                "viewsCount" to 94L,
                "commentsCount" to 0L,
                "category" to "Coffee",
                "isReel" to true,
                "audioTitle" to "Nori Beats · Sunday Travertine",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "showcase_post_3" to mapOf(
                "userId" to seedCreatorUid,
                "authorUsername" to "atelier.nord",
                "authorDisplayName" to "Atelier Nord",
                "authorAvatarKey" to "avatar_nord",
                "authorVerified" to true,
                "location" to "Copenhagen, Denmark",
                "imageKey" to "img_post_architecture",
                "localImagePath" to null,
                "imageData" to null,
                "filterTag" to "NONE",
                "caption" to "When raw concrete meets warm white oak and four o'clock sunbeams. #architecture #minimal",
                "likesCount" to 31L,
                "viewsCount" to 165L,
                "commentsCount" to 0L,
                "category" to "Architecture",
                "isReel" to true,
                "audioTitle" to "Atelier Nord · Quiet Atrium",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "showcase_post_4" to mapOf(
                "userId" to seedCreatorUid,
                "authorUsername" to "kenji.lens",
                "authorDisplayName" to "Kenji Takahashi",
                "authorAvatarKey" to "avatar_kenji",
                "authorVerified" to true,
                "location" to "Shinjuku, Tokyo",
                "imageKey" to "img_post_neon_street",
                "localImagePath" to null,
                "imageData" to null,
                "filterTag" to "NONE",
                "caption" to "Silk blue & maroon neon reflections after a sudden midnight downpour. #tokyo #nightphotography",
                "likesCount" to 42L,
                "viewsCount" to 210L,
                "commentsCount" to 0L,
                "category" to "Night",
                "isReel" to true,
                "audioTitle" to "Kenji · Rain on Cobblestone",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        showcasePosts.forEach { (id, payload) ->
            batch.set(postsCol().document(id), payload)
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, postsCol().path)
            throw e
        }
    }

    suspend fun toggleLike(post: Post, actorProfile: UserProfile?): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val postRef = postsCol().document(post.id)
        val markerRef = userDoc(uid).collection("likedPosts").document(post.id)

        val becameLiked = try {
            db.runTransaction { transaction ->
                val postSnap = transaction.get(postRef)
                if (!postSnap.exists()) {
                    throw IllegalStateException("Post is no longer available")
                }
                val markerSnap = transaction.get(markerRef)
                val currentLikes = (postSnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                if (!markerSnap.exists()) {
                    transaction.set(
                        markerRef,
                        mapOf(
                            "targetId" to post.id,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.update(
                        postRef,
                        mapOf(
                            "likesCount" to currentLikes + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                } else {
                    transaction.delete(markerRef)
                    val nextLikes = if (currentLikes > 0L) currentLikes - 1L else 0L
                    if (nextLikes != currentLikes) {
                        transaction.update(
                            postRef,
                            mapOf(
                                "likesCount" to nextLikes,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    false
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }

        if (becameLiked && !post.id.startsWith("showcase_") && post.userId.isNotBlank() && post.userId != uid && actorProfile != null) {
            sendNotificationSafe(
                targetUid = post.userId,
                actorProfile = actorProfile,
                message = "liked your photo.",
                previewImageKey = post.imageKey,
                isFollowAction = false
            )
        }
    }

    suspend fun setLiked(post: Post, actorProfile: UserProfile?): Result<Unit> = runCatching {
        if (post.isLiked) return@runCatching
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val postRef = postsCol().document(post.id)
        val markerRef = userDoc(uid).collection("likedPosts").document(post.id)

        val becameLiked = try {
            db.runTransaction { transaction ->
                val postSnap = transaction.get(postRef)
                if (!postSnap.exists()) return@runTransaction false
                val markerSnap = transaction.get(markerRef)
                if (markerSnap.exists()) return@runTransaction false
                val currentLikes = (postSnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                transaction.set(
                    markerRef,
                    mapOf(
                        "targetId" to post.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    postRef,
                    mapOf(
                        "likesCount" to currentLikes + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                true
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }

        if (becameLiked && !post.id.startsWith("showcase_") && post.userId.isNotBlank() && post.userId != uid && actorProfile != null) {
            sendNotificationSafe(
                targetUid = post.userId,
                actorProfile = actorProfile,
                message = "liked your photo.",
                previewImageKey = post.imageKey,
                isFollowAction = false
            )
        }
    }

    suspend fun toggleBookmark(post: Post): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val markerRef = userDoc(uid).collection("bookmarks").document(post.id)
        try {
            if (post.isBookmarked) {
                markerRef.delete().await()
            } else {
                markerRef.set(
                    mapOf(
                        "targetId" to post.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, markerRef.path)
            throw e
        }
    }

    suspend fun toggleFollow(
        actorProfile: UserProfile,
        targetUid: String,
        targetUsername: String,
        currentlyFollowing: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (targetUid.isBlank() || targetUid == uid) return@runCatching
        val followId = "${uid}_${targetUid}"
        val followRef = followsCol().document(followId)
        try {
            if (currentlyFollowing) {
                followRef.delete().await()
            } else {
                followRef.set(
                    mapOf(
                        "followerUid" to uid,
                        "targetUid" to targetUid,
                        "followerUsername" to actorProfile.username.ifBlank { "creator" }.take(50),
                        "targetUsername" to targetUsername.ifBlank { "creator" }.take(50),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                sendNotificationSafe(
                    targetUid = targetUid,
                    actorProfile = actorProfile,
                    message = "started following you.",
                    previewImageKey = null,
                    isFollowAction = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            throw e
        }
    }

    suspend fun addComment(post: Post, actorProfile: UserProfile, text: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Comment text must not be empty" }

        val postRef = postsCol().document(post.id)
        val commentRef = postRef.collection("comments").document()
        val payload = mapOf(
            "userId" to uid,
            "authorUsername" to actorProfile.username.ifBlank { "creator" }.take(50),
            "authorAvatarKey" to actorProfile.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "text" to trimmed.take(500),
            "likesCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val batch = db.batch()
        batch.set(commentRef, payload)
        batch.update(
            postRef,
            mapOf(
                "commentsCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
            if (!post.id.startsWith("showcase_") && post.userId.isNotBlank() && post.userId != uid) {
                sendNotificationSafe(
                    targetUid = post.userId,
                    actorProfile = actorProfile,
                    message = "commented: \"${trimmed.take(80)}\"",
                    previewImageKey = post.imageKey,
                    isFollowAction = false
                )
            }
            commentRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    suspend fun toggleCommentLike(postId: String, comment: Comment): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(postId.isNotBlank() && comment.id.isNotBlank()) { "Post ID and Comment ID must not be blank" }
        val commentRef = postsCol().document(postId).collection("comments").document(comment.id)
        val markerRef = userDoc(uid).collection("likedComments").document(comment.id)

        try {
            db.runTransaction { transaction ->
                val commentSnap = transaction.get(commentRef)
                if (!commentSnap.exists()) return@runTransaction
                val markerSnap = transaction.get(markerRef)
                val currentLikes = (commentSnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                if (!markerSnap.exists()) {
                    transaction.set(
                        markerRef,
                        mapOf(
                            "targetId" to comment.id,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.update(
                        commentRef,
                        mapOf(
                            "likesCount" to currentLikes + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    transaction.delete(markerRef)
                    val nextLikes = if (currentLikes > 0L) currentLikes - 1L else 0L
                    if (nextLikes != currentLikes) {
                        transaction.update(
                            commentRef,
                            mapOf(
                                "likesCount" to nextLikes,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, commentRef.path)
            throw e
        }
    }

    suspend fun createPost(
        profile: UserProfile,
        imageKey: String,
        localImagePath: String?,
        imageData: String?,
        mediaType: String = "IMAGE",
        localVideoPath: String? = null,
        videoData: String? = null,
        videoChunks: List<String> = emptyList(),
        videoDurationMs: Long = 0L,
        audioTitle: String = "",
        localAudioPath: String? = null,
        filterTag: String,
        caption: String,
        location: String,
        category: String,
        isReel: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = postsCol().document()
        val resolvedAudioTitle = audioTitle.trim().take(120).ifBlank {
            if (isReel || mediaType == "VIDEO") "${profile.displayName} · Original Audio" else ""
        }
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("authorUsername", profile.username)
            put("authorDisplayName", profile.displayName)
            put("authorAvatarKey", profile.avatarKey)
            put("authorVerified", profile.isVerified)
            put("location", location.trim().take(120))
            put("imageKey", imageKey)
            if (!localImagePath.isNullOrBlank()) put("localImagePath", localImagePath.take(500))
            if (!imageData.isNullOrBlank()) put("imageData", imageData)
            put("mediaType", mediaType)
            if (!localVideoPath.isNullOrBlank()) put("localVideoPath", localVideoPath.take(500))
            if (!videoData.isNullOrBlank()) put("videoData", videoData)
            if (videoChunks.isNotEmpty()) put("videoChunksCount", videoChunks.size.toLong())
            put("videoDurationMs", videoDurationMs.coerceIn(0L, 3_600_000L))
            if (!localAudioPath.isNullOrBlank()) put("localAudioPath", localAudioPath.take(500))
            put("filterTag", filterTag)
            put("caption", caption.trim().take(1000))
            put("likesCount", 0L)
            put("viewsCount", 0L)
            put("commentsCount", 0L)
            put("category", category)
            put("isReel", isReel)
            put("audioTitle", resolvedAudioTitle)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            if (videoChunks.isNotEmpty()) {
                try {
                    uploadVideoChunks(docRef.collection("videoChunks"), videoChunks)
                } catch (chunkErr: Exception) {
                    runCatching { docRef.delete().await() }
                    throw chunkErr
                }
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun createStory(
        profile: UserProfile,
        imageKey: String,
        localImagePath: String?,
        imageData: String?,
        mediaType: String = "IMAGE",
        localVideoPath: String? = null,
        videoData: String? = null,
        videoChunks: List<String> = emptyList(),
        videoDurationMs: Long = 0L,
        audioTitle: String = "",
        localAudioPath: String? = null,
        caption: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = storiesCol().document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("username", profile.username)
            put("avatarKey", profile.avatarKey)
            put("imageKey", imageKey)
            if (!localImagePath.isNullOrBlank()) put("localImagePath", localImagePath.take(500))
            if (!imageData.isNullOrBlank()) put("imageData", imageData)
            put("mediaType", mediaType)
            if (!localVideoPath.isNullOrBlank()) put("localVideoPath", localVideoPath.take(500))
            if (!videoData.isNullOrBlank()) put("videoData", videoData)
            if (videoChunks.isNotEmpty()) put("videoChunksCount", videoChunks.size.toLong())
            put("videoDurationMs", videoDurationMs.coerceIn(0L, 3_600_000L))
            if (audioTitle.isNotBlank()) put("audioTitle", audioTitle.trim().take(120))
            if (!localAudioPath.isNullOrBlank()) put("localAudioPath", localAudioPath.take(500))
            put("caption", caption.ifBlank { "Shared a moment" }.take(300))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            if (videoChunks.isNotEmpty()) {
                try {
                    uploadVideoChunks(docRef.collection("videoChunks"), videoChunks)
                } catch (chunkErr: Exception) {
                    runCatching { docRef.delete().await() }
                    throw chunkErr
                }
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    private suspend fun uploadVideoChunks(
        chunksCollection: com.google.firebase.firestore.CollectionReference,
        chunks: List<String>
    ) {
        // Commit 5 chunks (~2.3 MB Base64 max) per Firestore batch so RPC stays safely below 10 MiB.
        chunks.chunked(5).forEachIndexed { groupIdx, slice ->
            val batch = db.batch()
            slice.forEachIndexed { offset, chunkData ->
                val globalIndex = groupIdx * 5 + offset
                val chunkDocId = "chunk_${globalIndex.toString().padStart(3, '0')}"
                batch.set(
                    chunksCollection.document(chunkDocId),
                    mapOf(
                        "index" to globalIndex.toLong(),
                        "data" to chunkData,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
    }

    suspend fun fetchVideoChunks(
        collectionName: String,
        mediaId: String,
        expectedCount: Long
    ): List<String> {
        requireUserId()
        if (mediaId.isBlank() || mediaId.startsWith("showcase_")) return emptyList()
        val safeCollection = if (collectionName == "stories") "stories" else "posts"
        val chunksRef = db.collection(safeCollection).document(mediaId).collection("videoChunks")
        val retryDelaysMs = longArrayOf(600L, 1000L, 1500L, 2000L, 2500L)

        try {
            for (attempt in 0..retryDelaysMs.size) {
                val snap = chunksRef.orderBy("index", Query.Direction.ASCENDING).get().await()
                val ordered = snap.documents
                    .mapNotNull { doc ->
                        val idx = doc.getLong("index") ?: return@mapNotNull null
                        val data = doc.getString("data") ?: return@mapNotNull null
                        if (data.isBlank()) null else idx to data
                    }
                    .sortedBy { it.first }
                    .map { it.second }

                if ((expectedCount <= 0L && ordered.isNotEmpty()) ||
                    (expectedCount > 0L && ordered.size >= expectedCount.toInt())
                ) {
                    return ordered
                }
                if (attempt < retryDelaysMs.size) {
                    delay(retryDelaysMs[attempt])
                }
            }
            return emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, chunksRef.path)
            return emptyList()
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        requireUserId()
        require(postId.isNotBlank()) { "Post ID must not be blank" }
        val docRef = postsCol().document(postId)
        try {
            runCatching {
                val chunksSnap = docRef.collection("videoChunks").get().await()
                if (!chunksSnap.isEmpty) {
                    chunksSnap.documents.chunked(20).forEach { group ->
                        val batch = db.batch()
                        group.forEach { chunkDoc -> batch.delete(chunkDoc.reference) }
                        batch.commit().await()
                    }
                }
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun markStorySeen(storyId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(storyId.isNotBlank()) { "Story ID must not be blank" }
        val markerRef = userDoc(uid).collection("seenStories").document(storyId)
        try {
            val snap = markerRef.get().await()
            if (!snap.exists()) {
                markerRef.set(
                    mapOf(
                        "targetId" to storyId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, markerRef.path)
            throw e
        }
    }

    suspend fun updateProfile(
        current: UserProfile,
        displayName: String,
        username: String,
        category: String,
        bio: String,
        website: String,
        avatarData: String? = current.avatarData
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val cleanDisplay = displayName.trim().ifBlank { current.displayName }.take(80)
        val cleanUsername = username.trim().lowercase().replace(" ", "_")
            .ifBlank { current.username }.take(50)
        try {
            val updates = hashMapOf<String, Any?>(
                "displayName" to cleanDisplay,
                "username" to cleanUsername,
                "category" to category.trim().take(80),
                "bio" to bio.trim().take(300),
                "website" to website.trim().take(200),
                "avatarData" to avatarData,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun setUserVerified(
        targetUid: String,
        verified: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        require(targetUid.isNotBlank()) { "Target user ID must not be blank" }
        val docRef = userDoc(targetUid)
        try {
            docRef.update(
                mapOf(
                    "isVerified" to verified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleNotificationFollow(
        actorProfile: UserProfile,
        notification: NotificationItem
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(notification.id.isNotBlank()) { "Notification ID must not be blank" }
        val docRef = userDoc(uid).collection("notifications").document(notification.id)
        val nextFollowingBack = !notification.isFollowingBack
        try {
            docRef.update(
                mapOf(
                    "isFollowingBack" to nextFollowingBack,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (notification.actorUid.isNotBlank() && notification.actorUid != uid) {
                toggleFollow(
                    actorProfile = actorProfile,
                    targetUid = notification.actorUid,
                    targetUsername = notification.actorUsername,
                    currentlyFollowing = !nextFollowingBack
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun markNotificationsRead(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("notifications")
        val unread = try {
            colRef.whereEqualTo("isUnread", true).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
        if (unread.isEmpty) return@runCatching
        val batch = db.batch()
        unread.documents.forEach { doc ->
            batch.update(
                doc.reference,
                mapOf(
                    "isUnread" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, colRef.path)
            throw e
        }
    }

    suspend fun openOrCreateThread(
        currentProfile: UserProfile,
        peerProfile: UserProfile
    ): Result<MessageThread> = runCatching {
        val uid = requireUserId()
        require(peerProfile.userId.isNotBlank() && peerProfile.userId != uid) {
            "Cannot open chat with invalid or self user"
        }
        val (userA, userB) = if (uid < peerProfile.userId) {
            currentProfile to peerProfile
        } else {
            peerProfile to currentProfile
        }
        val threadId = "${userA.userId}_${userB.userId}"
        val threadRef = threadsCol().document(threadId)

        val existing = try {
            threadRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, threadRef.path)
            throw e
        }

        if (existing.exists()) {
            return@runCatching existing.toObject(MessageThread::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: error("Failed to parse thread $threadId")
        }

        val payload = mapOf(
            "participantUids" to listOf(userA.userId, userB.userId),
            "userAId" to userA.userId,
            "userAUsername" to userA.username.ifBlank { "creator" }.take(50),
            "userADisplayName" to userA.displayName.ifBlank { "Creator" }.take(80),
            "userAAvatarKey" to userA.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "userBId" to userB.userId,
            "userBUsername" to userB.username.ifBlank { "creator" }.take(50),
            "userBDisplayName" to userB.displayName.ifBlank { "Creator" }.take(80),
            "userBAvatarKey" to userB.avatarKey.ifBlank { "avatar_elena" }.take(64),
            "lastMessage" to "Start a conversation",
            "lastSenderUid" to uid,
            "unreadUid" to "",
            "timeLabel" to "now",
            "unreadCount" to 0L,
            "isOnline" to true,
            "noteText" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            threadRef.set(payload).await()
            val createdSnap = threadRef.get().await()
            createdSnap.toObject(MessageThread::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: MessageThread(
                    id = threadId,
                    participantUids = listOf(userA.userId, userB.userId),
                    userAId = userA.userId,
                    userAUsername = userA.username,
                    userADisplayName = userA.displayName,
                    userAAvatarKey = userA.avatarKey,
                    userBId = userB.userId,
                    userBUsername = userB.username,
                    userBDisplayName = userB.displayName,
                    userBAvatarKey = userB.avatarKey,
                    lastMessage = "Start a conversation",
                    lastSenderUid = uid,
                    unreadUid = "",
                    timeLabel = "now",
                    unreadCount = 0L,
                    isOnline = true
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, threadRef.path)
            throw e
        }
    }

    suspend fun sendMessage(
        thread: MessageThread,
        senderProfile: UserProfile,
        text: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(thread.id.isNotBlank()) { "Thread ID must not be blank" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message text must not be empty" }

        val threadRef = threadsCol().document(thread.id)
        val msgRef = threadRef.collection("messages").document()
        val peerUid = thread.peerUidFor(uid)

        val msgPayload = mapOf(
            "participantUids" to thread.participantUids,
            "senderUid" to uid,
            "senderUsername" to senderProfile.username.ifBlank { "creator" }.take(50),
            "text" to trimmed.take(1000),
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            db.runTransaction { transaction ->
                val threadSnap = transaction.get(threadRef)
                val existingUnreadUid = threadSnap.getString("unreadUid") ?: ""
                val existingUnreadCount = threadSnap.getLong("unreadCount") ?: 0L
                val nextUnreadCount = if (existingUnreadUid == peerUid) existingUnreadCount + 1L else 1L

                transaction.set(msgRef, msgPayload)
                transaction.update(
                    threadRef,
                    mapOf(
                        "lastMessage" to trimmed.take(500),
                        "lastSenderUid" to uid,
                        "timeLabel" to "now",
                        "unreadUid" to peerUid,
                        "unreadCount" to nextUnreadCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun clearThreadUnread(thread: MessageThread): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (thread.unreadUid != uid || thread.unreadCount <= 0L) return@runCatching
        require(thread.id.isNotBlank()) { "Thread ID must not be blank" }
        val docRef = threadsCol().document(thread.id)
        try {
            docRef.update(
                mapOf(
                    "unreadUid" to "",
                    "unreadCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun incrementPostView(postId: String): Result<Unit> = runCatching {
        requireUserId()
        if (postId.isBlank()) return@runCatching
        val postRef = postsCol().document(postId)
        try {
            db.runTransaction { transaction ->
                val snap = transaction.get(postRef)
                if (!snap.exists()) return@runTransaction
                val currentViews = (snap.getLong("viewsCount") ?: 0L).coerceAtLeast(0L)
                transaction.update(
                    postRef,
                    mapOf(
                        "viewsCount" to currentViews + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    fun observeActiveLiveStreams(currentUid: String): Flow<List<LiveStream>> {
        val colRef = liveStreamsCol()
        val path = colRef.path
        return colRef
            .whereEqualTo("isLive", true)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(LiveStream::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<LiveStream> { it.userId == currentUid }
                            .thenByDescending { it.viewerCount }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeLiveStream(currentUid: String, streamId: String): Flow<LiveStream?> {
        val docRef = liveStreamsCol().document(streamId)
        val path = docRef.path
        return docRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(LiveStream::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.GET, path)
                throw error
            }
    }

    fun observeLiveComments(currentUid: String, streamId: String): Flow<List<LiveComment>> {
        val colRef = liveStreamsCol().document(streamId).collection("liveComments")
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(LiveComment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<LiveComment> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun startLiveStream(
        profile: UserProfile,
        title: String,
        category: String = "Live"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val streamRef = liveStreamsCol().document(uid)
        val cleanTitle = title.trim().ifBlank { "${profile.displayName} is live on Momi" }.take(140)
        val payload = mapOf(
            "userId" to uid,
            "username" to profile.username.ifBlank { "creator" }.take(50),
            "displayName" to profile.displayName.ifBlank { "Momi Creator" }.take(80),
            "avatarKey" to profile.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "authorVerified" to profile.isVerified,
            "title" to cleanTitle,
            "category" to category.trim().ifBlank { "Live" }.take(40),
            "isLive" to true,
            "viewerCount" to 0L,
            "likesCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            streamRef.set(payload).await()
            uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, streamRef.path)
            throw e
        }
    }

    suspend fun endLiveStream(streamId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val targetId = streamId.ifBlank { uid }
        val streamRef = liveStreamsCol().document(targetId)
        try {
            val snap = streamRef.get().await()
            if (snap.exists() && snap.getString("userId") == uid) {
                streamRef.update(
                    mapOf(
                        "isLive" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path)
            throw e
        }
    }

    suspend fun joinLiveStream(streamId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (streamId.isBlank() || streamId == uid) return@runCatching
        val streamRef = liveStreamsCol().document(streamId)
        try {
            db.runTransaction { transaction ->
                val snap = transaction.get(streamRef)
                if (!snap.exists() || snap.getBoolean("isLive") != true) return@runTransaction
                val current = (snap.getLong("viewerCount") ?: 0L).coerceAtLeast(0L)
                transaction.update(
                    streamRef,
                    mapOf(
                        "viewerCount" to current + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path)
            throw e
        }
    }

    suspend fun leaveLiveStream(streamId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (streamId.isBlank() || streamId == uid) return@runCatching
        val streamRef = liveStreamsCol().document(streamId)
        try {
            db.runTransaction { transaction ->
                val snap = transaction.get(streamRef)
                if (!snap.exists()) return@runTransaction
                val current = (snap.getLong("viewerCount") ?: 0L).coerceAtLeast(0L)
                if (current > 0L) {
                    transaction.update(
                        streamRef,
                        mapOf(
                            "viewerCount" to current - 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path)
            throw e
        }
    }

    suspend fun sendLiveHeart(streamId: String): Result<Unit> = runCatching {
        requireUserId()
        if (streamId.isBlank()) return@runCatching
        val streamRef = liveStreamsCol().document(streamId)
        try {
            db.runTransaction { transaction ->
                val snap = transaction.get(streamRef)
                if (!snap.exists()) return@runTransaction
                val current = (snap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                transaction.update(
                    streamRef,
                    mapOf(
                        "likesCount" to current + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path)
            throw e
        }
    }

    suspend fun sendLiveComment(
        streamId: String,
        actorProfile: UserProfile,
        text: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(streamId.isNotBlank()) { "Stream ID must not be blank" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Live comment cannot be empty" }
        val commentRef = liveStreamsCol().document(streamId).collection("liveComments").document()
        val payload = mapOf(
            "userId" to uid,
            "username" to actorProfile.username.ifBlank { "creator" }.take(50),
            "avatarKey" to actorProfile.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "text" to trimmed.take(300),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            commentRef.set(payload).await()
            commentRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    private suspend fun sendNotificationSafe(
        targetUid: String,
        actorProfile: UserProfile,
        message: String,
        previewImageKey: String?,
        isFollowAction: Boolean
    ) {
        runCatching {
            val notifRef = userDoc(targetUid).collection("notifications").document()
            val payload = buildMap<String, Any> {
                put("userId", targetUid)
                put("actorUid", actorProfile.userId)
                put("actorUsername", actorProfile.username.ifBlank { "creator" }.take(50))
                put("actorAvatarKey", actorProfile.avatarKey.ifBlank { "avatar_aria" }.take(64))
                put("message", message.take(300))
                put("timeLabel", "now")
                put("section", "Today")
                if (!previewImageKey.isNullOrBlank()) put("previewImageKey", previewImageKey.take(64))
                put("isFollowAction", isFollowAction)
                put("isFollowingBack", false)
                put("isUnread", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            notifRef.set(payload).await()
        }
    }

    private fun logFlowErrorIfSignedIn(
        uid: String,
        error: Throwable,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun deriveUsername(email: String?, displayName: String): String {
        val emailPrefix = email?.substringBefore("@")
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._]"), "")
            ?.takeIf { it.isNotBlank() }
        if (emailPrefix != null) return emailPrefix.take(50)
        val fromName = displayName.lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .takeIf { it.isNotBlank() }
        return (fromName ?: "momi.creator").take(50)
    }
}
