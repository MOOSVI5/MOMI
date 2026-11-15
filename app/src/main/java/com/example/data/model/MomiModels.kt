package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val category: String = "",
    val bio: String = "",
    val website: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarData: String? = null,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @get:PropertyName("isVerified") val isVerified: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarKey: String = "avatar_aria",
    @get:Exclude val authorAvatarData: String? = null,
    val authorVerified: Boolean = false,
    val location: String = "",
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageData: String? = null,
    val mediaType: String = "IMAGE",
    val localVideoPath: String? = null,
    val videoData: String? = null,
    val videoDurationMs: Long = 0L,
    val filterTag: String = "NONE",
    val caption: String = "",
    val likesCount: Long = 0L,
    @get:PropertyName("isLiked") val isLiked: Boolean = false,
    @get:PropertyName("isBookmarked") val isBookmarked: Boolean = false,
    @get:PropertyName("isFollowingAuthor") val isFollowingAuthor: Boolean = false,
    val commentsCount: Long = 0L,
    val category: String = "For You",
    @get:PropertyName("isReel") val isReel: Boolean = false,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideoPost: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoData.isNullOrBlank()
}

@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatarKey: String = "avatar_aria",
    @get:Exclude val authorAvatarData: String? = null,
    val text: String = "",
    val likesCount: Long = 0L,
    @get:PropertyName("isLiked") val isLiked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarKey: String = "avatar_aria",
    @get:Exclude val avatarData: String? = null,
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageData: String? = null,
    val mediaType: String = "IMAGE",
    val localVideoPath: String? = null,
    val videoData: String? = null,
    val videoDurationMs: Long = 0L,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    val caption: String = "",
    @get:PropertyName("isSeen") val isSeen: Boolean = false,
    @get:PropertyName("isOwnStory") val isOwnStory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideoStory: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoData.isNullOrBlank()
}

@IgnoreExtraProperties
data class FollowEdge(
    @DocumentId val id: String = "",
    val followerUid: String = "",
    val targetUid: String = "",
    val followerUsername: String = "",
    val targetUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InteractionMarker(
    @DocumentId val id: String = "",
    val targetId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorUid: String = "",
    val actorUsername: String = "",
    val actorAvatarKey: String = "avatar_aria",
    val message: String = "",
    val timeLabel: String = "",
    val section: String = "Today",
    val previewImageKey: String? = null,
    @get:PropertyName("isFollowAction") val isFollowAction: Boolean = false,
    @get:PropertyName("isFollowingBack") val isFollowingBack: Boolean = false,
    @get:PropertyName("isUnread") val isUnread: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MessageThread(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val userAId: String = "",
    val userAUsername: String = "",
    val userADisplayName: String = "",
    val userAAvatarKey: String = "avatar_aria",
    val userBId: String = "",
    val userBUsername: String = "",
    val userBDisplayName: String = "",
    val userBAvatarKey: String = "avatar_aria",
    val lastMessage: String = "",
    val lastSenderUid: String = "",
    val unreadUid: String = "",
    val timeLabel: String = "",
    val unreadCount: Long = 0L,
    @get:PropertyName("isOnline") val isOnline: Boolean = false,
    val noteText: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun peerUidFor(currentUid: String): String =
        if (userAId == currentUid) userBId else userAId

    fun peerUsernameFor(currentUid: String): String =
        if (userAId == currentUid) userBUsername else userAUsername

    fun peerDisplayNameFor(currentUid: String): String =
        if (userAId == currentUid) userBDisplayName else userADisplayName

    fun peerAvatarKeyFor(currentUid: String): String =
        if (userAId == currentUid) userBAvatarKey else userAAvatarKey

    fun isUnreadFor(currentUid: String): Boolean =
        unreadUid == currentUid && unreadCount > 0L
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderUsername: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
