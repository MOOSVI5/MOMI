package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.MessageThread
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
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

    fun observeAllPosts(userId: String): Flow<List<Post>> {
        val colRef = userDoc(userId).collection("posts")
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
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePost(userId: String, postId: String): Flow<Post?> {
        val docRef = userDoc(userId).collection("posts").document(postId)
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
                logFlowErrorIfSignedIn(userId, error, OperationType.GET, path)
                throw error
            }
    }

    fun observeComments(userId: String, postId: String): Flow<List<Comment>> {
        val colRef = userDoc(userId).collection("posts").document(postId).collection("comments")
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
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeStories(userId: String): Flow<List<Story>> {
        val colRef = userDoc(userId).collection("stories")
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(Story::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<Story> { it.isOwnStory }
                            .thenBy { it.isSeen }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
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
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMessageThreads(userId: String): Flow<List<MessageThread>> {
        val colRef = userDoc(userId).collection("threads")
        val path = colRef.path
        return colRef
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(MessageThread::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<MessageThread> { it.unreadCount > 0L }
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
        val colRef = userDoc(userId).collection("threads").document(threadId).collection("messages")
        val path = colRef.path
        return colRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
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
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("posts").document(postId)
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
        if (existingProfile.exists()) return@runCatching

        val rawDisplay = displayNameHint?.trim()?.takeIf { it.isNotBlank() } ?: "Aria Vance"
        val displayName = rawDisplay.take(80)
        val username = deriveUsername(emailHint, rawDisplay)

        val batch1 = db.batch()
        batch1.set(
            profileRef,
            mapOf(
                "userId" to uid,
                "username" to username,
                "displayName" to displayName,
                "category" to "Visual Creator · Studio",
                "bio" to "Chasing silk blue skies & deep maroon shadows.\n35mm · Architecture · Espresso",
                "website" to "momi.photo/$username",
                "avatarKey" to "avatar_aria",
                "followersCount" to 12840L,
                "followingCount" to 418L,
                "isVerified" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val postsCol = profileRef.collection("posts")
        val seedPosts = listOf(
            "post_1" to mapOf(
                "userId" to uid,
                "authorUsername" to "elena.maris",
                "authorDisplayName" to "Elena Maris",
                "authorAvatarKey" to "avatar_elena",
                "authorVerified" to true,
                "location" to "Positano, Amalfi Coast",
                "imageKey" to "img_post_coastal",
                "localImagePath" to null,
                "filterTag" to "NONE",
                "caption" to "Golden hour spilling over the cliffs before the ferries drift back in. Still dreaming about this turquoise water. #amalfi #coastal #goldenhour #momi",
                "likesCount" to 2481L,
                "isLiked" to false,
                "isBookmarked" to true,
                "isFollowingAuthor" to true,
                "commentsCount" to 3L,
                "category" to "Travel",
                "isReel" to true,
                "audioTitle" to "Elena Maris · Mediterranean Breeze",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_2" to mapOf(
                "userId" to uid,
                "authorUsername" to "matcha.ritual",
                "authorDisplayName" to "Matcha Ritual",
                "authorAvatarKey" to "avatar_matcha",
                "authorVerified" to true,
                "location" to "Lisbon, Chiado",
                "imageKey" to "img_post_coffee",
                "localImagePath" to null,
                "filterTag" to "NONE",
                "caption" to "Morning ritual: stone-ground Uji matcha over crystal ice and a still-warm laminated croissant. #matcha #morninglight #cafeculture",
                "likesCount" to 1394L,
                "isLiked" to true,
                "isBookmarked" to false,
                "isFollowingAuthor" to true,
                "commentsCount" to 2L,
                "category" to "Coffee",
                "isReel" to true,
                "audioTitle" to "Nori Beats · Sunday Travertine",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_3" to mapOf(
                "userId" to uid,
                "authorUsername" to "atelier.nord",
                "authorDisplayName" to "Atelier Nord",
                "authorAvatarKey" to "avatar_nord",
                "authorVerified" to true,
                "location" to "Copenhagen, Denmark",
                "imageKey" to "img_post_architecture",
                "localImagePath" to null,
                "filterTag" to "NONE",
                "caption" to "When raw concrete meets warm white oak and four o'clock sunbeams. New gallery wing opens this Friday. #architecture #minimal #lightandshadow",
                "likesCount" to 3910L,
                "isLiked" to false,
                "isBookmarked" to true,
                "isFollowingAuthor" to true,
                "commentsCount" to 2L,
                "category" to "Architecture",
                "isReel" to true,
                "audioTitle" to "Atelier Nord · Quiet Atrium",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_4" to mapOf(
                "userId" to uid,
                "authorUsername" to "kenji.lens",
                "authorDisplayName" to "Kenji Takahashi",
                "authorAvatarKey" to "avatar_kenji",
                "authorVerified" to true,
                "location" to "Shinjuku, Tokyo",
                "imageKey" to "img_post_neon_street",
                "localImagePath" to null,
                "filterTag" to "NONE",
                "caption" to "Silk blue & maroon neon reflections after a sudden midnight downpour. Shot on 35mm f/1.4. #tokyo #nightphotography #streetframe",
                "likesCount" to 5620L,
                "isLiked" to false,
                "isBookmarked" to false,
                "isFollowingAuthor" to true,
                "commentsCount" to 2L,
                "category" to "Night",
                "isReel" to true,
                "audioTitle" to "Kenji · Rain on Cobblestone",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_5" to mapOf(
                "userId" to uid,
                "authorUsername" to username,
                "authorDisplayName" to displayName,
                "authorAvatarKey" to "avatar_aria",
                "authorVerified" to true,
                "location" to "Ravello Terrace",
                "imageKey" to "img_post_coastal",
                "localImagePath" to null,
                "filterTag" to "BLUE",
                "caption" to "Color-graded frame from last week's coastal study — leaning into silk blues and warm stone. #momicolor #filmstudy",
                "likesCount" to 892L,
                "isLiked" to true,
                "isBookmarked" to false,
                "isFollowingAuthor" to true,
                "commentsCount" to 1L,
                "category" to "Travel",
                "isReel" to false,
                "audioTitle" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_6" to mapOf(
                "userId" to uid,
                "authorUsername" to username,
                "authorDisplayName" to displayName,
                "authorAvatarKey" to "avatar_aria",
                "authorVerified" to true,
                "location" to "Studio North",
                "imageKey" to "img_post_architecture",
                "localImagePath" to null,
                "filterTag" to "MONO",
                "caption" to "Monochrome geometry test in the atrium. High contrast reveals every beam of light. #monochrome #minimalism",
                "likesCount" to 741L,
                "isLiked" to false,
                "isBookmarked" to false,
                "isFollowingAuthor" to true,
                "commentsCount" to 1L,
                "category" to "Minimal",
                "isReel" to false,
                "audioTitle" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "post_7" to mapOf(
                "userId" to uid,
                "authorUsername" to username,
                "authorDisplayName" to displayName,
                "authorAvatarKey" to "avatar_aria",
                "authorVerified" to true,
                "location" to "Chiado Roastery",
                "imageKey" to "img_post_coffee",
                "localImagePath" to null,
                "filterTag" to "MAROON",
                "caption" to "Warm maroon highlights on the morning table before editing client selects. #workfromcafe #editorial",
                "likesCount" to 635L,
                "isLiked" to false,
                "isBookmarked" to false,
                "isFollowingAuthor" to true,
                "commentsCount" to 0L,
                "category" to "Coffee",
                "isReel" to false,
                "audioTitle" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        seedPosts.forEach { (id, payload) ->
            batch1.set(postsCol.document(id), payload)
        }

        val storiesCol = profileRef.collection("stories")
        val seedStories = listOf(
            "story_1" to mapOf(
                "userId" to uid,
                "username" to "elena.maris",
                "avatarKey" to "avatar_elena",
                "imageKey" to "img_post_coastal",
                "localImagePath" to null,
                "caption" to "Morning ferry to Amalfi",
                "isSeen" to false,
                "isOwnStory" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "story_2" to mapOf(
                "userId" to uid,
                "username" to "matcha.ritual",
                "avatarKey" to "avatar_matcha",
                "imageKey" to "img_post_coffee",
                "localImagePath" to null,
                "caption" to "First pour of the day",
                "isSeen" to false,
                "isOwnStory" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "story_3" to mapOf(
                "userId" to uid,
                "username" to "atelier.nord",
                "avatarKey" to "avatar_nord",
                "imageKey" to "img_post_architecture",
                "localImagePath" to null,
                "caption" to "Atrium light check at 16:00",
                "isSeen" to false,
                "isOwnStory" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "story_4" to mapOf(
                "userId" to uid,
                "username" to "kenji.lens",
                "avatarKey" to "avatar_kenji",
                "imageKey" to "img_post_neon_street",
                "localImagePath" to null,
                "caption" to "Midnight rain walk",
                "isSeen" to true,
                "isOwnStory" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        seedStories.forEach { (id, payload) ->
            batch1.set(storiesCol.document(id), payload)
        }

        val notificationsCol = profileRef.collection("notifications")
        val seedNotifications = listOf(
            "notif_1" to mapOf(
                "userId" to uid,
                "actorUsername" to "kenji.lens",
                "actorAvatarKey" to "avatar_kenji",
                "message" to "liked your photo.",
                "timeLabel" to "12m",
                "section" to "Today",
                "previewImageKey" to "img_post_coastal",
                "isFollowAction" to false,
                "isFollowingBack" to false,
                "isUnread" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "notif_2" to mapOf(
                "userId" to uid,
                "actorUsername" to "elena.maris",
                "actorAvatarKey" to "avatar_elena",
                "message" to "commented: \"Love the silk blue grade on this!\"",
                "timeLabel" to "48m",
                "section" to "Today",
                "previewImageKey" to "img_post_coastal",
                "isFollowAction" to false,
                "isFollowingBack" to false,
                "isUnread" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "notif_3" to mapOf(
                "userId" to uid,
                "actorUsername" to "sora.frames",
                "actorAvatarKey" to "avatar_sora",
                "message" to "started following you.",
                "timeLabel" to "3h",
                "section" to "Today",
                "previewImageKey" to null,
                "isFollowAction" to true,
                "isFollowingBack" to false,
                "isUnread" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "notif_4" to mapOf(
                "userId" to uid,
                "actorUsername" to "atelier.nord",
                "actorAvatarKey" to "avatar_nord",
                "message" to "mentioned you in a story.",
                "timeLabel" to "2d",
                "section" to "This Week",
                "previewImageKey" to "img_post_architecture",
                "isFollowAction" to false,
                "isFollowingBack" to false,
                "isUnread" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "notif_5" to mapOf(
                "userId" to uid,
                "actorUsername" to "matcha.ritual",
                "actorAvatarKey" to "avatar_matcha",
                "message" to "liked your comment: \"Need the name of this spot!\"",
                "timeLabel" to "4d",
                "section" to "This Week",
                "previewImageKey" to "img_post_coffee",
                "isFollowAction" to false,
                "isFollowingBack" to false,
                "isUnread" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        seedNotifications.forEach { (id, payload) ->
            batch1.set(notificationsCol.document(id), payload)
        }

        val threadsCol = profileRef.collection("threads")
        val seedThreads = listOf(
            "thread_1" to mapOf(
                "userId" to uid,
                "peerUsername" to "elena.maris",
                "peerDisplayName" to "Elena Maris",
                "peerAvatarKey" to "avatar_elena",
                "lastMessage" to "Sent the high-res Amalfi LUTs over!",
                "timeLabel" to "14m",
                "unreadCount" to 1L,
                "isOnline" to true,
                "noteText" to "Amalfi golden hour",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "thread_2" to mapOf(
                "userId" to uid,
                "peerUsername" to "kenji.lens",
                "peerDisplayName" to "Kenji Takahashi",
                "peerAvatarKey" to "avatar_kenji",
                "lastMessage" to "Let's shoot Shinjuku in the rain next week",
                "timeLabel" to "2h",
                "unreadCount" to 2L,
                "isOnline" to true,
                "noteText" to "35mm night walk",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "thread_3" to mapOf(
                "userId" to uid,
                "peerUsername" to "atelier.nord",
                "peerDisplayName" to "Atelier Nord",
                "peerAvatarKey" to "avatar_nord",
                "lastMessage" to "VIP pass is reserved for Friday 6 PM",
                "timeLabel" to "1d",
                "unreadCount" to 0L,
                "isOnline" to false,
                "noteText" to "Gallery opening",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "thread_4" to mapOf(
                "userId" to uid,
                "peerUsername" to "matcha.ritual",
                "peerDisplayName" to "Matcha Ritual",
                "peerAvatarKey" to "avatar_matcha",
                "lastMessage" to "Thanks for tagging the Chiado spot!",
                "timeLabel" to "3d",
                "unreadCount" to 0L,
                "isOnline" to false,
                "noteText" to "Iced matcha",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        seedThreads.forEach { (id, payload) ->
            batch1.set(threadsCol.document(id), payload)
        }

        try {
            batch1.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profileRef.path)
            throw e
        }

        // Batch 2 commits subcollection records after parent posts & threads exist (rules verify parent exists()).
        val batch2 = db.batch()
        val seedComments = listOf(
            Triple("post_1", "comment_1", mapOf("userId" to uid, "authorUsername" to username, "authorAvatarKey" to "avatar_aria", "text" to "The light on those pastel walls is unreal.", "likesCount" to 18L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_1", "comment_2", mapOf("userId" to uid, "authorUsername" to "kenji.lens", "authorAvatarKey" to "avatar_kenji", "text" to "Those turquoise tones look straight off medium format.", "likesCount" to 9L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_1", "comment_3", mapOf("userId" to uid, "authorUsername" to "atelier.nord", "authorAvatarKey" to "avatar_nord", "text" to "Adding Positano to our autumn shoot itinerary.", "likesCount" to 5L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_2", "comment_4", mapOf("userId" to uid, "authorUsername" to "elena.maris", "authorAvatarKey" to "avatar_elena", "text" to "That ribbed glass + travertine combo is perfection.", "likesCount" to 14L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_2", "comment_5", mapOf("userId" to uid, "authorUsername" to username, "authorAvatarKey" to "avatar_aria", "text" to "Need the name of this spot in Chiado!", "likesCount" to 6L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_3", "comment_6", mapOf("userId" to uid, "authorUsername" to "matcha.ritual", "authorAvatarKey" to "avatar_matcha", "text" to "Those diagonal sunbeams are dramatic in the best way.", "likesCount" to 22L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_3", "comment_7", mapOf("userId" to uid, "authorUsername" to username, "authorAvatarKey" to "avatar_aria", "text" to "See you at the Friday preview.", "likesCount" to 7L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_4", "comment_8", mapOf("userId" to uid, "authorUsername" to "atelier.nord", "authorAvatarKey" to "avatar_nord", "text" to "Blue & maroon palette in real life — love this frame.", "likesCount" to 31L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_4", "comment_9", mapOf("userId" to uid, "authorUsername" to "elena.maris", "authorAvatarKey" to "avatar_elena", "text" to "The wet cobblestone reflections are crisp!", "likesCount" to 12L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_5", "comment_10", mapOf("userId" to uid, "authorUsername" to "elena.maris", "authorAvatarKey" to "avatar_elena", "text" to "Love the silk blue grade on this!", "likesCount" to 11L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())),
            Triple("post_6", "comment_11", mapOf("userId" to uid, "authorUsername" to "kenji.lens", "authorAvatarKey" to "avatar_kenji", "text" to "Sharp geometry.", "likesCount" to 8L, "isLiked" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        )
        seedComments.forEach { (postId, commentId, payload) ->
            batch2.set(postsCol.document(postId).collection("comments").document(commentId), payload)
        }

        val seedMessages = listOf(
            Triple("thread_1", "msg_1", mapOf("userId" to uid, "senderUsername" to "elena.maris", "text" to "Hey! Just saw your Ravello color study.", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_1", "msg_2", mapOf("userId" to uid, "senderUsername" to username, "text" to "Thank you! Your Positano frame inspired the palette.", "isFromMe" to true, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_1", "msg_3", mapOf("userId" to uid, "senderUsername" to "elena.maris", "text" to "Sent the high-res Amalfi LUTs over!", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_2", "msg_4", mapOf("userId" to uid, "senderUsername" to "kenji.lens", "text" to "The reflections tonight were wild.", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_2", "msg_5", mapOf("userId" to uid, "senderUsername" to "kenji.lens", "text" to "Let's shoot Shinjuku in the rain next week", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_3", "msg_6", mapOf("userId" to uid, "senderUsername" to "atelier.nord", "text" to "VIP pass is reserved for Friday 6 PM", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp())),
            Triple("thread_4", "msg_7", mapOf("userId" to uid, "senderUsername" to "matcha.ritual", "text" to "Thanks for tagging the Chiado spot!", "isFromMe" to false, "createdAt" to FieldValue.serverTimestamp()))
        )
        seedMessages.forEach { (threadId, msgId, payload) ->
            batch2.set(threadsCol.document(threadId).collection("messages").document(msgId), payload)
        }

        try {
            batch2.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${profileRef.path}/seed_subcollections")
            throw e
        }
    }

    suspend fun toggleLike(post: Post): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val docRef = userDoc(uid).collection("posts").document(post.id)
        val nextLiked = !post.isLiked
        val nextLikes = if (nextLiked) post.likesCount + 1L else (post.likesCount - 1L).coerceAtLeast(0L)
        try {
            docRef.update(
                mapOf(
                    "isLiked" to nextLiked,
                    "likesCount" to nextLikes,
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

    suspend fun setLiked(post: Post): Result<Unit> = runCatching {
        if (post.isLiked) return@runCatching
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val docRef = userDoc(uid).collection("posts").document(post.id)
        try {
            docRef.update(
                mapOf(
                    "isLiked" to true,
                    "likesCount" to post.likesCount + 1L,
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

    suspend fun toggleBookmark(post: Post): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val docRef = userDoc(uid).collection("posts").document(post.id)
        try {
            docRef.update(
                mapOf(
                    "isBookmarked" to !post.isBookmarked,
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

    suspend fun toggleFollow(username: String, currentlyFollowing: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val postsCol = userDoc(uid).collection("posts")
        val matchingDocs = try {
            postsCol.whereEqualTo("authorUsername", username).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, postsCol.path)
            throw e
        }
        if (matchingDocs.isEmpty) return@runCatching
        val batch = db.batch()
        matchingDocs.documents.forEach { doc ->
            batch.update(
                doc.reference,
                mapOf(
                    "isFollowingAuthor" to !currentlyFollowing,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postsCol.path)
            throw e
        }
    }

    suspend fun addComment(post: Post, username: String, avatarKey: String, text: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(post.id.isNotBlank()) { "Post ID must not be blank" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Comment text must not be empty" }
        val postRef = userDoc(uid).collection("posts").document(post.id)
        val commentRef = postRef.collection("comments").document()
        val payload = mapOf(
            "userId" to uid,
            "authorUsername" to username,
            "authorAvatarKey" to avatarKey,
            "text" to trimmed.take(500),
            "likesCount" to 0L,
            "isLiked" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val batch = db.batch()
        batch.set(commentRef, payload)
        batch.update(
            postRef,
            mapOf(
                "commentsCount" to post.commentsCount + 1L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
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
        val docRef = userDoc(uid).collection("posts").document(postId)
            .collection("comments").document(comment.id)
        val nextLiked = !comment.isLiked
        val nextLikes = if (nextLiked) comment.likesCount + 1L else (comment.likesCount - 1L).coerceAtLeast(0L)
        try {
            docRef.update(
                mapOf(
                    "isLiked" to nextLiked,
                    "likesCount" to nextLikes,
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

    suspend fun createPost(
        profile: UserProfile,
        imageKey: String,
        localImagePath: String?,
        filterTag: String,
        caption: String,
        location: String,
        category: String,
        isReel: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("posts").document()
        val payload = mapOf(
            "userId" to uid,
            "authorUsername" to profile.username,
            "authorDisplayName" to profile.displayName,
            "authorAvatarKey" to profile.avatarKey,
            "authorVerified" to profile.isVerified,
            "location" to location.trim().take(120),
            "imageKey" to imageKey,
            "localImagePath" to localImagePath,
            "filterTag" to filterTag,
            "caption" to caption.trim().take(1000),
            "likesCount" to 1L,
            "isLiked" to true,
            "isBookmarked" to false,
            "isFollowingAuthor" to true,
            "commentsCount" to 0L,
            "category" to category,
            "isReel" to isReel,
            "audioTitle" to if (isReel) "${profile.displayName} · Original Audio" else "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
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
        caption: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("stories").document()
        val payload = mapOf(
            "userId" to uid,
            "username" to profile.username,
            "avatarKey" to profile.avatarKey,
            "imageKey" to imageKey,
            "localImagePath" to localImagePath,
            "caption" to caption.ifBlank { "Shared a moment" }.take(300),
            "isSeen" to false,
            "isOwnStory" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(postId.isNotBlank()) { "Post ID must not be blank" }
        val docRef = userDoc(uid).collection("posts").document(postId)
        try {
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
        val docRef = userDoc(uid).collection("stories").document(storyId)
        try {
            docRef.update(
                mapOf(
                    "isSeen" to true,
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

    suspend fun updateProfile(
        current: UserProfile,
        displayName: String,
        username: String,
        category: String,
        bio: String,
        website: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val cleanDisplay = displayName.trim().ifBlank { current.displayName }.take(80)
        val cleanUsername = username.trim().lowercase().replace(" ", "_")
            .ifBlank { current.username }.take(50)
        try {
            docRef.update(
                mapOf(
                    "displayName" to cleanDisplay,
                    "username" to cleanUsername,
                    "category" to category.trim().take(80),
                    "bio" to bio.trim().take(300),
                    "website" to website.trim().take(200),
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

    suspend fun toggleNotificationFollow(notification: NotificationItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(notification.id.isNotBlank()) { "Notification ID must not be blank" }
        val docRef = userDoc(uid).collection("notifications").document(notification.id)
        try {
            docRef.update(
                mapOf(
                    "isFollowingBack" to !notification.isFollowingBack,
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

    suspend fun sendMessage(thread: MessageThread, senderUsername: String, text: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(thread.id.isNotBlank()) { "Thread ID must not be blank" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message text must not be empty" }
        val threadRef = userDoc(uid).collection("threads").document(thread.id)
        val msgRef = threadRef.collection("messages").document()
        val msgPayload = mapOf(
            "userId" to uid,
            "senderUsername" to senderUsername,
            "text" to trimmed.take(1000),
            "isFromMe" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(
            threadRef,
            mapOf(
                "lastMessage" to trimmed.take(500),
                "timeLabel" to "now",
                "unreadCount" to 0L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun clearThreadUnread(thread: MessageThread): Result<Unit> = runCatching {
        if (thread.unreadCount <= 0L) return@runCatching
        val uid = requireUserId()
        require(thread.id.isNotBlank()) { "Thread ID must not be blank" }
        val docRef = userDoc(uid).collection("threads").document(thread.id)
        try {
            docRef.update(
                mapOf(
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
        return (fromName ?: "aria.studio").take(50)
    }
}
