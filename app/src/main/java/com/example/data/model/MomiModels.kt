package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
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
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @get:PropertyName("isVerified") val isVerified: Boolean = true,
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
    val authorVerified: Boolean = false,
    val location: String = "",
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val filterTag: String = "NONE",
    val caption: String = "",
    val likesCount: Long = 0L,
    @get:PropertyName("isLiked") val isLiked: Boolean = false,
    @get:PropertyName("isBookmarked") val isBookmarked: Boolean = false,
    @get:PropertyName("isFollowingAuthor") val isFollowingAuthor: Boolean = true,
    val commentsCount: Long = 0L,
    val category: String = "For You",
    @get:PropertyName("isReel") val isReel: Boolean = false,
    val audioTitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatarKey: String = "avatar_aria",
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
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val caption: String = "",
    @get:PropertyName("isSeen") val isSeen: Boolean = false,
    @get:PropertyName("isOwnStory") val isOwnStory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
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
    val userId: String = "",
    val peerUsername: String = "",
    val peerDisplayName: String = "",
    val peerAvatarKey: String = "avatar_aria",
    val lastMessage: String = "",
    val timeLabel: String = "",
    val unreadCount: Long = 0L,
    @get:PropertyName("isOnline") val isOnline: Boolean = false,
    val noteText: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderUsername: String = "",
    val text: String = "",
    @get:PropertyName("isFromMe") val isFromMe: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
