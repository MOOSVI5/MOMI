package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class MediaFrame(
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageData: String? = null
)

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
    val localExtraImagePaths: List<String> = emptyList(),
    val extraImageDatas: List<String> = emptyList(),
    val mediaType: String = "IMAGE",
    val localVideoPath: String? = null,
    val videoData: String? = null,
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val filterTag: String = "NONE",
    val caption: String = "",
    val likesCount: Long = 0L,
    val viewsCount: Long = 0L,
    @get:PropertyName("isLiked") val isLiked: Boolean = false,
    @get:PropertyName("isBookmarked") val isBookmarked: Boolean = false,
    @get:PropertyName("isFollowingAuthor") val isFollowingAuthor: Boolean = false,
    val commentsCount: Long = 0L,
    val category: String = "For You",
    @get:PropertyName("isReel") val isReel: Boolean = false,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    val audioData: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideoPost: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoData.isNullOrBlank() || videoChunksCount > 0L

    @get:Exclude
    val allImageFrames: List<MediaFrame>
        get() {
            val frames = mutableListOf(
                MediaFrame(
                    imageKey = imageKey,
                    localImagePath = localImagePath,
                    imageData = imageData
                )
            )
            val extraCount = maxOf(localExtraImagePaths.size, extraImageDatas.size)
            for (i in 0 until extraCount) {
                val path = localExtraImagePaths.getOrNull(i)
                val data = extraImageDatas.getOrNull(i)
                if (!path.isNullOrBlank() || !data.isNullOrBlank()) {
                    frames.add(
                        MediaFrame(
                            imageKey = imageKey,
                            localImagePath = path,
                            imageData = data
                        )
                    )
                }
            }
            return frames
        }
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
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    val audioData: String? = null,
    val caption: String = "",
    @get:PropertyName("isSeen") val isSeen: Boolean = false,
    @get:PropertyName("isOwnStory") val isOwnStory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideoStory: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoData.isNullOrBlank() || videoChunksCount > 0L
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
    val messageType: String = "TEXT",
    val imageData: String? = null,
    val videoData: String? = null,
    val videoDurationMs: Long = 0L,
    val audioData: String? = null,
    val audioDurationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarKey: String = "avatar_aria",
    @get:Exclude val avatarData: String? = null,
    @get:PropertyName("authorVerified") val authorVerified: Boolean = false,
    val title: String = "",
    val category: String = "Live",
    @get:PropertyName("isLive") val isLive: Boolean = true,
    val viewerCount: Long = 0L,
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveComment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarKey: String = "avatar_aria",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MerchantAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerUsername: String = "",
    val storeName: String = "",
    val tagline: String = "",
    val category: String = "Collectibles",
    val location: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarData: String? = null,
    @get:PropertyName("isVerified") val isVerified: Boolean = false,
    val verifiedByEmail: String? = null,
    val totalSales: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MarketProduct(
    @DocumentId val id: String = "",
    val sellerUid: String = "",
    val sellerUsername: String = "",
    val storeName: String = "",
    @get:Exclude val sellerVerified: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: String = "Collectibles",
    val priceUsd: Double = 0.0,
    val originalPriceUsd: Double = 0.0,
    val discountPercent: Long = 0L,
    @get:PropertyName("freeShipping") val freeShipping: Boolean = true,
    val imageKey: String = "img_post_coastal",
    val imageData: String? = null,
    val extraImageDatas: List<String> = emptyList(),
    val videoData: String? = null,
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val stockCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideoProduct: Boolean
        get() = !videoData.isNullOrBlank() || videoChunksCount > 0L

    @get:Exclude
    val allProductFrames: List<MediaFrame>
        get() {
            val frames = mutableListOf(MediaFrame(imageKey = imageKey, imageData = imageData))
            extraImageDatas.forEach { data ->
                if (data.isNotBlank()) {
                    frames.add(MediaFrame(imageKey = imageKey, imageData = data))
                }
            }
            return frames
        }
}

@IgnoreExtraProperties
data class MarketOrder(
    @DocumentId val id: String = "",
    val buyerUid: String = "",
    val buyerUsername: String = "",
    val sellerUid: String = "",
    val sellerStoreName: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val productImageKey: String = "img_post_coastal",
    val productImageData: String? = null,
    val priceUsd: Double = 0.0,
    val shippingAddress: String = "",
    val status: String = "PLACED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
