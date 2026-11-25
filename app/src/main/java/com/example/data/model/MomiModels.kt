package com.example.data.model


data class MediaFrame(
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageData: String? = null,
    val imageUrl: String? = null
)

object MomiRoles {
    const val USER = "USER"
    const val MODERATOR = "MODERATOR"
    const val EDITOR = "EDITOR"
    const val ADMIN = "ADMIN"

    fun labelOf(role: String): String = when (role.uppercase()) {
        ADMIN -> "Admin"
        MODERATOR -> "Moderator"
        EDITOR -> "Editor"
        else -> "Member"
    }
}

object ModerationScopes {
    const val VIOLENCE_GRAPHIC = "VIOLENCE_GRAPHIC"
    const val SEXUAL_NUDITY = "SEXUAL_NUDITY"
    const val HARASSMENT_SPAM = "HARASSMENT_SPAM"
    const val MARKETPLACE_SAFETY = "MARKETPLACE_SAFETY"
    const val EDITORIAL_CURATION = "EDITORIAL_CURATION"

    val SURVEILLANCE_SCOPES = listOf(
        VIOLENCE_GRAPHIC,
        SEXUAL_NUDITY,
        HARASSMENT_SPAM,
        MARKETPLACE_SAFETY
    )

    val ALL_SCOPES = SURVEILLANCE_SCOPES + EDITORIAL_CURATION

    fun labelOf(scope: String): String = when (scope) {
        VIOLENCE_GRAPHIC -> "Violent & Graphic"
        SEXUAL_NUDITY -> "Sexual & Explicit"
        HARASSMENT_SPAM -> "Harassment & Spam"
        MARKETPLACE_SAFETY -> "Marketplace Safety"
        EDITORIAL_CURATION -> "Editorial Curation"
        else -> scope
    }

    fun descriptionOf(scope: String): String = when (scope) {
        VIOLENCE_GRAPHIC -> "Review & remove reported gore, violence, or graphic posts"
        SEXUAL_NUDITY -> "Review & remove reported adult, sexual, or NSFW uploads"
        HARASSMENT_SPAM -> "Review & remove reported abuse, hate, or spam content"
        MARKETPLACE_SAFETY -> "Surveil & remove unsafe or prohibited Marketplace items"
        EDITORIAL_CURATION -> "Feature top Marketplace deals as Editor's Picks"
        else -> ""
    }
}

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val category: String = "",
    val bio: String = "",
    val website: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarUrl: String? = null,
    val avatarData: String? = null,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val isVerified: Boolean = false,
    val role: String = MomiRoles.USER,
    val moderationScopes: List<String> = emptyList(),
    val assignedByEmail: String? = null,
    val partnerConsent: Boolean = false,
    val consentUpdatedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class Post(
    val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarKey: String = "avatar_aria",
    val authorAvatarUrl: String? = null,
    val authorAvatarData: String? = null,
    val authorVerified: Boolean = false,
    val location: String = "",
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageUrl: String? = null,
    val imageData: String? = null,
    val localExtraImagePaths: List<String> = emptyList(),
    val extraImageUrls: List<String> = emptyList(),
    val extraImageDatas: List<String> = emptyList(),
    val mediaType: String = "IMAGE",
    val localVideoPath: String? = null,
    val videoUrl: String? = null,
    val videoData: String? = null,
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val filterTag: String = "NONE",
    val aspectRatio: String = "1:1",
    val taggedUsernames: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val caption: String = "",
    val likesCount: Long = 0L,
    val viewsCount: Long = 0L,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val isFollowingAuthor: Boolean = false,
    val commentsCount: Long = 0L,
    val category: String = "For You",
    val isReel: Boolean = false,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    val audioUrl: String? = null,
    val audioData: String? = null,
    val isBoosted: Boolean = false,
    val boostTier: String = "",
    val recommendedLabel: String = "",
    val boostedByUid: String? = null,
    val boostedAt: Timestamp? = null,
    val pageId: String? = null,
    val groupId: String? = null,
    val communityName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isVideoPost: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoUrl.isNullOrBlank() || !videoData.isNullOrBlank() || videoChunksCount > 0L

    val numericAspectRatio: Float
        get() = when {
            isVideoPost -> 0.85f
            aspectRatio == "4:5" -> 0.8f
            aspectRatio == "16:9" -> 1.777f
            else -> 1f
        }

    val allHashtags: List<String>
        get() {
            val fromCaption = Regex("#([a-zA-Z0-9_]+)")
                .findAll(caption)
                .map { it.groupValues[1].lowercase() }
                .toList()
            return (hashtags.map { it.trim().removePrefix("#").lowercase() } + fromCaption)
                .filter { it.isNotBlank() }
                .distinct()
                .take(15)
        }

    val allTaggedUsernames: List<String>
        get() {
            val fromCaption = Regex("@([a-zA-Z0-9._]+)")
                .findAll(caption)
                .map { it.groupValues[1].lowercase() }
                .toList()
            return (taggedUsernames.map { it.trim().removePrefix("@").lowercase() } + fromCaption)
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)
        }

    val allImageFrames: List<MediaFrame>
        get() {
            val frames = mutableListOf(
                MediaFrame(
                    imageKey = imageKey,
                    localImagePath = localImagePath,
                    imageData = imageData,
                    imageUrl = imageUrl
                )
            )
            val extraCount = maxOf(localExtraImagePaths.size, extraImageDatas.size, extraImageUrls.size)
            for (i in 0 until extraCount) {
                val path = localExtraImagePaths.getOrNull(i)
                val data = extraImageDatas.getOrNull(i)
                val url = extraImageUrls.getOrNull(i)
                if (!path.isNullOrBlank() || !data.isNullOrBlank() || !url.isNullOrBlank()) {
                    frames.add(
                        MediaFrame(
                            imageKey = imageKey,
                            localImagePath = path,
                            imageData = data,
                            imageUrl = url
                        )
                    )
                }
            }
            return frames
        }
}

data class Comment(
    val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatarKey: String = "avatar_aria",
    val authorAvatarUrl: String? = null,
    val authorAvatarData: String? = null,
    val text: String = "",
    val likesCount: Long = 0L,
    val isLiked: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class Story(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarUrl: String? = null,
    val avatarData: String? = null,
    val imageKey: String = "img_post_coastal",
    val localImagePath: String? = null,
    val imageUrl: String? = null,
    val imageData: String? = null,
    val mediaType: String = "IMAGE",
    val localVideoPath: String? = null,
    val videoUrl: String? = null,
    val videoData: String? = null,
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val audioTitle: String = "",
    val localAudioPath: String? = null,
    val audioUrl: String? = null,
    val audioData: String? = null,
    val caption: String = "",
    val isSeen: Boolean = false,
    val isOwnStory: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isVideoStory: Boolean
        get() = mediaType == "VIDEO" || !localVideoPath.isNullOrBlank() || !videoUrl.isNullOrBlank() || !videoData.isNullOrBlank() || videoChunksCount > 0L
}

data class FollowEdge(
    val id: String = "",
    val followerUid: String = "",
    val targetUid: String = "",
    val followerUsername: String = "",
    val targetUsername: String = "",
    val createdAt: Timestamp? = null
)

data class InteractionMarker(
    val id: String = "",
    val targetId: String = "",
    val createdAt: Timestamp? = null
)

data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val actorUid: String = "",
    val actorUsername: String = "",
    val actorAvatarKey: String = "avatar_aria",
    val message: String = "",
    val timeLabel: String = "",
    val section: String = "Today",
    val previewImageKey: String? = null,
    val isFollowAction: Boolean = false,
    val isFollowingBack: Boolean = false,
    val isUnread: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class MessageThread(
    val id: String = "",
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
    val isOnline: Boolean = false,
    val noteText: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
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

data class ChatMessage(
    val id: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val messageType: String = "TEXT",
    val mediaUrl: String? = null,
    val imageData: String? = null,
    val videoData: String? = null,
    val videoDurationMs: Long = 0L,
    val audioData: String? = null,
    val audioDurationMs: Long = 0L,
    val createdAt: Timestamp? = null
)

data class LiveStream(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarUrl: String? = null,
    val avatarData: String? = null,
    val authorVerified: Boolean = false,
    val title: String = "",
    val category: String = "Live",
    val isLive: Boolean = true,
    val viewerCount: Long = 0L,
    val likesCount: Long = 0L,
    val giftsCredits: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class LiveFrame(
    val id: String = "",
    val userId: String = "",
    val seq: Long = 0L,
    val frameData: String = "",
    val cameraEnabled: Boolean = true,
    val micMuted: Boolean = false,
    val updatedAt: Timestamp? = null
)

data class LiveComment(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarKey: String = "avatar_aria",
    val text: String = "",
    val createdAt: Timestamp? = null
)

data class UserWallet(
    val id: String = "",
    val userId: String = "",
    val balance: Long = 0L,
    val totalEarned: Long = 0L,
    val totalSpent: Long = 0L,
    val totalPurchased: Long = 0L,
    val accountTier: String = MomiCreditCatalog.TIER_STANDARD,
    val verificationTier: String = MomiCreditCatalog.VERIFICATION_NONE,
    val unlockedPerks: List<String> = emptyList(),
    val lastTxId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class CreditTransaction(
    val id: String = "",
    val txId: String = "",
    val type: String = TYPE_TOP_UP,
    val senderUid: String = "",
    val senderUsername: String = "",
    val recipientUid: String = "",
    val recipientUsername: String = "",
    val creditsAmount: Long = 0L,
    val priceUsd: Double = 0.0,
    val paymentMethod: String = METHOD_GOOGLE_PLAY,
    val paymentRef: String? = null,
    val giftKey: String? = null,
    val giftName: String? = null,
    val streamId: String? = null,
    val perkKey: String? = null,
    val postId: String? = null,
    val recipientClaimed: Boolean = true,
    val status: String = "COMPLETED",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val TYPE_TOP_UP = "TOP_UP"
        const val TYPE_GIFT = "GIFT"
        const val TYPE_PERK_UNLOCK = "PERK_UNLOCK"
        const val TYPE_POST_BOOST = "POST_BOOST"
        const val TYPE_OWNER_GRANT = "OWNER_GRANT"

        const val METHOD_GOOGLE_PLAY = "GOOGLE_PLAY"
        const val METHOD_DEBUG_SANDBOX = "DEBUG_SANDBOX"
        const val METHOD_WALLET_CREDITS = "WALLET_CREDITS"
        const val METHOD_OWNER_ADMIN = "OWNER_ADMIN"
    }
}

data class AdMonetizationConfig(
    val id: String = "monetization",
    val admobEnabled: Boolean = true,
    val admobBannerUnitId: String = "",
    val admobMrecUnitId: String = "",
    val admobInterstitialUnitId: String = "",
    val startIoEnabled: Boolean = true,
    val partnerPromoTitle: String = "Featured Creator Deals & Partner Showcase",
    val partnerPromoSubtitle: String = "Explore verified creator collections, partner offers & exclusive community perks.",
    val partnerPromoCta: String = "Explore Partner",
    val announcementId: String = "",
    val announcementTitle: String = "",
    val announcementBody: String = "",
    val latestVersionName: String = "2.3",
    val releaseNotes: String = "",
    val updatedByEmail: String = "",
    val updatedAt: Timestamp? = null
)

data class PostBoostOption(
    val tier: String,
    val title: String,
    val badgeLabel: String,
    val subtitle: String,
    val creditsCost: Long
)

data class CreditPackageOption(
    val productId: String,
    val title: String,
    val credits: Long,
    val bonusLabel: String,
    val fallbackPriceUsd: Double,
    val isPopular: Boolean = false
)

data class LiveGiftItem(
    val key: String,
    val name: String,
    val emojiLabel: String,
    val creditsCost: Long,
    val accentHex: Long
)

data class AccountPerkItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val creditsCost: Long,
    val tierGranted: String,
    val verificationStageGranted: String,
    val comingSoonFeatures: List<String> = emptyList()
)

object MomiCreditCatalog {
    const val TIER_STANDARD = "STANDARD"
    const val TIER_SUPPORTER = "SUPPORTER"
    const val TIER_CREATOR_PRO = "CREATOR_PRO"
    const val TIER_VIP = "VIP"

    const val VERIFICATION_NONE = "NONE"
    const val VERIFICATION_PENDING_PERK = "PENDING_PERK"
    const val VERIFICATION_CREATOR_PRO = "CREATOR_PRO"
    const val VERIFICATION_VERIFIED = "VERIFIED"

    val packages: List<CreditPackageOption> = listOf(
        CreditPackageOption(
            productId = "momi_credits_50",
            title = "Starter Spark",
            credits = 50L,
            bonusLabel = "Try gifting live creators",
            fallbackPriceUsd = 0.99
        ),
        CreditPackageOption(
            productId = "momi_credits_200",
            title = "Creator Pack",
            credits = 200L,
            bonusLabel = "Popular for live rooms",
            fallbackPriceUsd = 2.99,
            isPopular = true
        ),
        CreditPackageOption(
            productId = "momi_credits_500",
            title = "Patron Bundle",
            credits = 500L,
            bonusLabel = "+15% value · Unlocks Supporter tier",
            fallbackPriceUsd = 6.99
        ),
        CreditPackageOption(
            productId = "momi_credits_1200",
            title = "Studio Vault",
            credits = 1200L,
            bonusLabel = "+25% value · Ready for Creator Pro",
            fallbackPriceUsd = 14.99
        ),
        CreditPackageOption(
            productId = "momi_credits_3000",
            title = "VIP Patron",
            credits = 3000L,
            bonusLabel = "Best value · Unlocks VIP status",
            fallbackPriceUsd = 29.99
        )
    )

    val gifts: List<LiveGiftItem> = listOf(
        LiveGiftItem("rose", "Silk Rose", "🌹", 5L, 0xFFE91E63),
        LiveGiftItem("heart_burst", "Heart Burst", "💖", 20L, 0xFFFF4D6D),
        LiveGiftItem("spotlight", "Spotlight", "✨", 50L, 0xFF4A89B8),
        LiveGiftItem("crown", "Royal Crown", "👑", 150L, 0xFFF59E0B),
        LiveGiftItem("supernova", "Super Nova", "🚀", 500L, 0xFF8C1D40)
    )

    val perks: List<AccountPerkItem> = listOf(
        AccountPerkItem(
            key = "SUPPORTER_FRAME",
            title = "Supporter Profile Badge & Live Glow",
            subtitle = "Stand out in creator live chat & unlock Supporter account tier.",
            creditsCost = 100L,
            tierGranted = TIER_SUPPORTER,
            verificationStageGranted = VERIFICATION_NONE,
            comingSoonFeatures = listOf("Custom chat color flare", "Priority live Q&A queue")
        ),
        AccountPerkItem(
            key = "CREATOR_BOOST",
            title = "Creator Spotlight Boost",
            subtitle = "Featured badge in Creator Discovery & elevated Creator Pro status.",
            creditsCost = 250L,
            tierGranted = TIER_CREATOR_PRO,
            verificationStageGranted = VERIFICATION_CREATOR_PRO,
            comingSoonFeatures = listOf("HD live frame priority", "Featured Explore placement")
        ),
        AccountPerkItem(
            key = "PRIORITY_VERIFICATION",
            title = "Priority Verification Fast-Track",
            subtitle = "Queue your account for official identity & creator verification review.",
            creditsCost = 500L,
            tierGranted = TIER_CREATOR_PRO,
            verificationStageGranted = VERIFICATION_PENDING_PERK,
            comingSoonFeatures = listOf("ID document KYC workflow", "Official verified creator shield")
        ),
        AccountPerkItem(
            key = "VIP_PATRON_PASS",
            title = "Momi VIP Patron Pass",
            subtitle = "Top VIP patron tier with priority support & upcoming subscriber lounges.",
            creditsCost = 1000L,
            tierGranted = TIER_VIP,
            verificationStageGranted = VERIFICATION_CREATOR_PRO,
            comingSoonFeatures = listOf("VIP backstage live rooms", "Zero-fee creator payouts")
        )
    )

    val boostOptions: List<PostBoostOption> = listOf(
        PostBoostOption(
            tier = "SPOTLIGHT",
            title = "Spotlight Boost",
            badgeLabel = "Boosted Spotlight",
            subtitle = "Elevate your post higher in For You & Search discovery",
            creditsCost = 100L
        ),
        PostBoostOption(
            tier = "SUPER_BOOST",
            title = "Super Boost",
            badgeLabel = "Super Boosted",
            subtitle = "Priority placement across Home Feed, Explore & Creator Spotlight",
            creditsCost = 250L
        )
    )

    fun packageByProductId(productId: String): CreditPackageOption? =
        packages.firstOrNull { it.productId == productId }

    fun perkByKey(key: String): AccountPerkItem? =
        perks.firstOrNull { it.key == key }

    fun tierForTotalPurchased(totalPurchased: Long, unlockedPerks: List<String>): String {
        return when {
            "VIP_PATRON_PASS" in unlockedPerks || totalPurchased >= 3000L -> TIER_VIP
            "CREATOR_BOOST" in unlockedPerks || "PRIORITY_VERIFICATION" in unlockedPerks || totalPurchased >= 1200L -> TIER_CREATOR_PRO
            "SUPPORTER_FRAME" in unlockedPerks || totalPurchased >= 200L -> TIER_SUPPORTER
            else -> TIER_STANDARD
        }
    }

    fun verificationTierForPerks(current: String, unlockedPerks: List<String>): String {
        if (current == VERIFICATION_VERIFIED) return VERIFICATION_VERIFIED
        return when {
            "PRIORITY_VERIFICATION" in unlockedPerks -> VERIFICATION_PENDING_PERK
            "CREATOR_BOOST" in unlockedPerks || "VIP_PATRON_PASS" in unlockedPerks -> VERIFICATION_CREATOR_PRO
            else -> VERIFICATION_NONE
        }
    }

    fun tierLabel(tier: String): String = when (tier) {
        TIER_VIP -> "VIP Patron"
        TIER_CREATOR_PRO -> "Creator Pro"
        TIER_SUPPORTER -> "Supporter"
        else -> "Standard"
    }

    fun verificationLabel(tier: String): String = when (tier) {
        VERIFICATION_VERIFIED -> "Verified Creator"
        VERIFICATION_PENDING_PERK -> "Verification Priority Queued"
        VERIFICATION_CREATOR_PRO -> "Creator Pro Eligible"
        else -> "Standard Member"
    }
}

data class MerchantAccount(
    val id: String = "",
    val userId: String = "",
    val ownerUsername: String = "",
    val storeName: String = "",
    val tagline: String = "",
    val category: String = "Collectibles",
    val location: String = "",
    val avatarKey: String = "avatar_aria",
    val avatarUrl: String? = null,
    val avatarData: String? = null,
    val isVerified: Boolean = false,
    val verifiedByEmail: String? = null,
    val totalSales: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class MarketProduct(
    val id: String = "",
    val sellerUid: String = "",
    val sellerUsername: String = "",
    val storeName: String = "",
    val sellerVerified: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: String = "Collectibles",
    val priceUsd: Double = 0.0,
    val originalPriceUsd: Double = 0.0,
    val discountPercent: Long = 0L,
    val freeShipping: Boolean = true,
    val imageKey: String = "img_post_coastal",
    val imageUrl: String? = null,
    val imageData: String? = null,
    val extraImageUrls: List<String> = emptyList(),
    val extraImageDatas: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoData: String? = null,
    val videoChunksCount: Long = 0L,
    val videoDurationMs: Long = 0L,
    val stockCount: Long = 1L,
    val isFeatured: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isVideoProduct: Boolean
        get() = !videoUrl.isNullOrBlank() || !videoData.isNullOrBlank() || videoChunksCount > 0L

    val allProductFrames: List<MediaFrame>
        get() {
            val frames = mutableListOf(MediaFrame(imageKey = imageKey, imageData = imageData, imageUrl = imageUrl))
            val extraCount = maxOf(extraImageDatas.size, extraImageUrls.size)
            for (i in 0 until extraCount) {
                val data = extraImageDatas.getOrNull(i)
                val url = extraImageUrls.getOrNull(i)
                if (!data.isNullOrBlank() || !url.isNullOrBlank()) {
                    frames.add(MediaFrame(imageKey = imageKey, imageData = data, imageUrl = url))
                }
            }
            return frames
        }
}

data class MarketOrder(
    val id: String = "",
    val buyerUid: String = "",
    val buyerUsername: String = "",
    val sellerUid: String = "",
    val sellerStoreName: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val productImageKey: String = "img_post_coastal",
    val productImageUrl: String? = null,
    val productImageData: String? = null,
    val priceUsd: Double = 0.0,
    val shippingAddress: String = "",
    val status: String = "PLACED",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class ContentReport(
    val id: String = "",
    val targetType: String = "POST",
    val targetId: String = "",
    val targetAuthorUid: String = "",
    val targetAuthorUsername: String = "",
    val targetPreviewText: String = "",
    val reporterUid: String = "",
    val reporterUsername: String = "",
    val category: String = ModerationScopes.VIOLENCE_GRAPHIC,
    val reasonDetail: String = "",
    val status: String = "OPEN",
    val reviewedByUid: String? = null,
    val reviewedByUsername: String? = null,
    val reviewedByRole: String? = null,
    val resolutionNote: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class MomiPage(
    val id: String = "",
    val ownerUid: String = "",
    val ownerUsername: String = "",
    val name: String = "",
    val handle: String = "",
    val category: String = "Creator",
    val bio: String = "",
    val coverKey: String = "img_post_coastal",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val followersCount: Long = 0L,
    val isFollowing: Boolean = false,
    val createdAt: Timestamp? = null
)

data class MomiGroup(
    val id: String = "",
    val ownerUid: String = "",
    val ownerUsername: String = "",
    val name: String = "",
    val category: String = "Community",
    val description: String = "",
    val privacy: String = "PUBLIC",
    val coverKey: String = "img_post_architecture",
    val membersCount: Long = 1L,
    val isMember: Boolean = false,
    val createdAt: Timestamp? = null
)

data class ShowcaseAccount(
    val username: String = "",
    val displayName: String = "",
    val avatarKey: String = "avatar_elena",
    val bio: String = "",
    val category: String = "Creator",
    val location: String = "",
    val isAuthorized: Boolean = false,
    val updatedByEmail: String = ""
)

fun sortFeedWithBoostPriority(posts: List<Post>): List<Post> {
    if (posts.size <= 1) return posts
    return posts.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Post>> { (it.value.isBoosted || it.value.recommendedLabel.isNotBlank()) && !it.value.id.startsWith("showcase_") }
                .thenByDescending { it.value.boostTier == "OWNER_RECOMMENDED" }
                .thenByDescending { it.value.boostTier == "SUPER_BOOST" }
                .thenByDescending { it.value.boostTier == "SPOTLIGHT" }
                .thenBy { it.index }
        )
        .map { it.value }
}

