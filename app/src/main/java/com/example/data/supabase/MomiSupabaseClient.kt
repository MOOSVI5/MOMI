package com.example.data.supabase

import com.example.BuildConfig
import com.example.data.model.AdMonetizationConfig
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.ContentReport
import com.example.data.model.CreditTransaction
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveFrame
import com.example.data.model.LiveStream
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.MessageThread
import com.example.data.model.MomiGroup
import com.example.data.model.MomiPage
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.ShowcaseAccount
import com.example.data.model.Story
import com.example.data.model.Timestamp
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.util.StoryOverlayCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.asRequestBody

data class SupabaseHealthResult(
    val configured: Boolean,
    val connected: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class SupabaseAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val phone: String? = null
)

data class SupabaseSignUpResult(
    val session: SupabaseAuthSession? = null,
    val confirmationRequired: Boolean = false,
    val email: String = ""
)

data class SupabaseFeedBundle(
    val profiles: List<UserProfile> = emptyList(),
    val posts: List<Post> = emptyList(),
    val stories: List<Story> = emptyList(),
    val products: List<MarketProduct> = emptyList(),
    val merchants: List<MerchantAccount> = emptyList(),
    val liveStreams: List<LiveStream> = emptyList(),
    val follows: List<FollowEdge> = emptyList(),
    val likedPostIds: Set<String> = emptySet(),
    val viewedPostIds: Set<String> = emptySet(),
    val bookmarkedPostIds: Set<String> = emptySet(),
    val seenStoryIds: Set<String> = emptySet(),
    val likedCommentIds: Set<String> = emptySet(),
    val postLikersByPostId: Map<String, List<String>> = emptyMap(),
    val postViewersByPostId: Map<String, List<String>> = emptyMap(),
    val notifications: List<NotificationItem> = emptyList(),
    val threads: List<MessageThread> = emptyList(),
    val orders: List<MarketOrder> = emptyList(),
    val reports: List<ContentReport> = emptyList(),
    val wallet: UserWallet? = null,
    val transactions: List<CreditTransaction> = emptyList(),
    val monetizationConfig: AdMonetizationConfig? = null,
    val pages: List<MomiPage> = emptyList(),
    val pageFollowedIds: Set<String> = emptySet(),
    val groups: List<MomiGroup> = emptyList(),
    val groupJoinedIds: Set<String> = emptySet(),
    val showcaseAccounts: List<ShowcaseAccount> = emptyList(),
    val failedTables: Set<String> = emptySet()
)

object MomiSupabaseSchema {
    const val STORY_TTL_MS = 86_400_000L
    const val MAX_VIDEO_DURATION_MS = 4_800_000L // 80 minutes
    const val POSTS_LIMIT = 20L
    const val FOLLOWS_LIMIT = 100L
    const val MARKERS_LIMIT = 80L
    const val TRANSACTIONS_LIMIT = 25L

    private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")
    private val E164_REGEX = Regex("""^\+[1-9]\d{6,14}$""")

    fun normalizeEmail(raw: String?): String =
        raw?.trim()?.lowercase(Locale.US).orEmpty()

    fun isValidEmailAddress(raw: String?): Boolean {
        val clean = normalizeEmail(raw)
        return clean.length in 5..160 && EMAIL_REGEX.matches(clean)
    }

    fun normalizePhoneE164(raw: String?): String {
        val stripped = raw?.trim().orEmpty().replace(Regex("""[\s\-().]"""), "")
        if (stripped.isEmpty()) return ""
        val withPlus = when {
            stripped.startsWith("+") -> stripped
            stripped.startsWith("00") -> "+${stripped.drop(2)}"
            else -> "+$stripped"
        }
        return if (E164_REGEX.matches(withPlus)) withPlus else ""
    }

    fun isValidPhoneE164(raw: String?): Boolean = normalizePhoneE164(raw).isNotEmpty()

    val TABLES: Set<String> = setOf(
        "profiles",
        "posts",
        "comments",
        "stories",
        "follows",
        "user_interactions",
        "notifications",
        "threads",
        "chat_messages",
        "live_streams",
        "live_frames",
        "live_comments",
        "merchants",
        "market_products",
        "market_orders",
        "content_reports",
        "wallets",
        "credit_transactions",
        "app_config",
        "pages",
        "page_follows",
        "groups",
        "group_members",
        "showcase_accounts"
    )

    val STORAGE_BUCKETS: Set<String> = setOf(
        "momi-media",
        "momi-avatars",
        "momi-videos"
    )

    fun normalizeSupabaseBaseUrl(raw: String?): String {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return ""
        if (!trimmed.startsWith("https://")) return ""
        if (trimmed.any { it.isWhitespace() }) return ""
        return runCatching {
            val uri = URI(trimmed)
            if (!uri.scheme.equals("https", ignoreCase = true)) return ""
            if (uri.userInfo != null || !uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) {
                return ""
            }
            val host = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return ""
            if (host.isEmpty() || "." !in host) return ""
            if (isDisallowedPrivateHost(host)) return ""
            val portPart = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
            "https://$host$portPart"
        }.getOrDefault("")
    }

    private fun isDisallowedPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return true
        val octets = host.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val a = octets[0].toInt()
            val b = octets[1].toInt()
            if (a == 10 || a == 127 || a == 0) return true
            if (a == 169 && b == 254) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
        }
        return false
    }

    fun isSafeClientAnonKey(rawKey: String?): Boolean {
        val clean = rawKey?.trim() ?: return false
        if (clean.length < 16 || clean.any { it.isWhitespace() }) return false
        if (clean.startsWith("sb_secret_", ignoreCase = true)) return false
        return true
    }

    fun isLegacyJwtKey(rawKey: String?): Boolean {
        val clean = rawKey?.trim().orEmpty()
        return clean.startsWith("eyJ") && clean.count { it == '.' } >= 2
    }

    fun buildAuthHeaders(anonKey: String, accessToken: String? = null): Map<String, String> {
        val cleanAnon = anonKey.trim()
        val cleanAccess = accessToken?.trim().orEmpty()
        val headers = linkedMapOf("apikey" to cleanAnon)
        val bearer = when {
            cleanAccess.isNotEmpty() -> cleanAccess
            isLegacyJwtKey(cleanAnon) -> cleanAnon
            else -> ""
        }
        if (bearer.isNotEmpty()) {
            headers["Authorization"] = "Bearer $bearer"
        }
        return headers
    }

    private fun decodeBase64UrlToUtf8(segment: String): String? {
        if (segment.isBlank()) return null
        val clean = segment.replace('-', '+').replace('_', '/')
        val padLen = (4 - clean.length % 4) % 4
        val padded = clean + "=".repeat(padLen)
        val out = ByteArray((padded.length * 3) / 4)
        var outIdx = 0
        var i = 0
        fun valForChar(c: Char): Int = when (c) {
            in 'A'..'Z' -> c.code - 'A'.code
            in 'a'..'z' -> c.code - 'a'.code + 26
            in '0'..'9' -> c.code - '0'.code + 52
            '+' -> 62
            '/' -> 63
            '=' -> -2
            else -> -1
        }
        while (i + 3 < padded.length) {
            val c0 = valForChar(padded[i++])
            val c1 = valForChar(padded[i++])
            val c2 = valForChar(padded[i++])
            val c3 = valForChar(padded[i++])
            if (c0 < 0 || c1 < 0 || c2 == -1 || c3 == -1) return null
            out[outIdx++] = ((c0 shl 2) or (c1 shr 4)).toByte()
            if (c2 >= 0) {
                out[outIdx++] = (((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte()
                if (c3 >= 0) {
                    out[outIdx++] = (((c2 and 0x03) shl 6) or c3).toByte()
                }
            }
        }
        return runCatching { String(out, 0, outIdx, Charsets.UTF_8) }.getOrNull()
    }

    fun extractJwtExpiryEpochMs(jwt: String?): Long? {
        val parts = jwt?.trim()?.split('.') ?: return null
        if (parts.size < 2) return null
        val payloadJson = decodeBase64UrlToUtf8(parts[1]) ?: return null
        val expSec = Regex(""""exp"\s*:\s*(\d+)""")
            .find(payloadJson)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null
        return expSec * 1000L
    }

    fun isJwtExpiredOrExpiringSoon(
        jwt: String?,
        nowMs: Long = System.currentTimeMillis(),
        skewMs: Long = 60_000L
    ): Boolean {
        val clean = jwt?.trim().orEmpty()
        if (clean.isEmpty()) return false
        val expMs = extractJwtExpiryEpochMs(clean) ?: return false
        return nowMs + skewMs >= expMs
    }

    fun isConfigured(baseUrl: String?, anonKey: String?): Boolean =
        normalizeSupabaseBaseUrl(baseUrl).isNotEmpty() && isSafeClientAnonKey(anonKey)

    fun formatTimestampIso(ts: Timestamp?): String {
        val millis = ts?.toDate()?.time ?: System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun toProfileRow(profile: UserProfile): Map<String, Any?> {
        val uid = profile.userId.ifBlank { profile.id }
        return linkedMapOf(
            "user_id" to uid,
            "username" to profile.username.ifBlank { "momi.${uid.take(6)}" }.take(50),
            "email" to normalizeEmail(profile.email).take(160),
            "phone" to normalizePhoneE164(profile.phone).take(32),
            "display_name" to profile.displayName.ifBlank { "Momi Creator" }.take(80),
            "category" to profile.category.take(80),
            "bio" to profile.bio.take(300),
            "website" to profile.website.take(200),
            "avatar_key" to profile.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "avatar_url" to profile.avatarUrl,
            "avatar_data" to profile.avatarData,
            "followers_count" to profile.followersCount.coerceAtLeast(0L),
            "following_count" to profile.followingCount.coerceAtLeast(0L),
            "is_verified" to profile.isVerified,
            "role" to profile.role.ifBlank { "USER" },
            "moderation_scopes" to profile.moderationScopes,
            "assigned_by_email" to profile.assignedByEmail,
            "partner_consent" to profile.partnerConsent,
            "consent_updated_at" to profile.consentUpdatedAt?.let { formatTimestampIso(it) },
            "created_at" to formatTimestampIso(profile.createdAt)
        )
    }

    fun toPostRow(post: Post): Map<String, Any?> = linkedMapOf(
        "id" to post.id,
        "user_id" to post.userId,
        "author_username" to post.authorUsername.ifBlank { "momi.creator" }.take(50),
        "author_display_name" to post.authorDisplayName.ifBlank { "Momi Creator" }.take(80),
        "author_avatar_key" to post.authorAvatarKey.ifBlank { "avatar_aria" }.take(64),
        "author_verified" to post.authorVerified,
        "location" to post.location.take(120),
        "image_key" to post.imageKey.ifBlank { "img_post_coastal" }.take(64),
        "image_url" to post.imageUrl,
        "image_data" to post.imageData,
        "extra_image_urls" to post.extraImageUrls,
        "extra_image_datas" to post.extraImageDatas,
        "media_type" to post.mediaType.ifBlank { "IMAGE" },
        "video_url" to post.videoUrl,
        "video_data" to post.videoData,
        "video_duration_ms" to post.videoDurationMs.coerceIn(0L, MAX_VIDEO_DURATION_MS),
        "filter_tag" to post.filterTag.ifBlank { "NONE" },
        "aspect_ratio" to post.aspectRatio.ifBlank { "1:1" },
        "tagged_usernames" to post.taggedUsernames,
        "hashtags" to post.hashtags,
        "caption" to post.caption.take(1000),
        "likes_count" to post.likesCount.coerceAtLeast(0L),
        "views_count" to post.viewsCount.coerceAtLeast(0L),
        "comments_count" to post.commentsCount.coerceAtLeast(0L),
        "category" to post.category.ifBlank { "For You" }.take(40),
        "is_reel" to post.isReel,
        "audio_title" to post.audioTitle.take(120),
        "audio_url" to post.audioUrl,
        "audio_data" to post.audioData,
        "is_boosted" to post.isBoosted,
        "boost_tier" to post.boostTier,
        "recommended_label" to post.recommendedLabel.take(40),
        "boosted_by_uid" to post.boostedByUid,
        "boosted_at" to post.boostedAt?.let { formatTimestampIso(it) },
        "page_id" to post.pageId,
        "group_id" to post.groupId,
        "community_name" to post.communityName.take(80),
        "created_at" to formatTimestampIso(post.createdAt)
    )

    fun toCommentRow(postId: String, comment: Comment): Map<String, Any?> = linkedMapOf(
        "id" to comment.id,
        "post_id" to postId,
        "user_id" to comment.userId,
        "author_username" to comment.authorUsername.ifBlank { "momi.creator" }.take(50),
        "author_avatar_key" to comment.authorAvatarKey.ifBlank { "avatar_aria" }.take(64),
        "text" to comment.text.take(500),
        "likes_count" to comment.likesCount.coerceAtLeast(0L),
        "created_at" to formatTimestampIso(comment.createdAt)
    )

    fun toStoryRow(story: Story): Map<String, Any?> = linkedMapOf(
        "id" to story.id,
        "user_id" to story.userId,
        "username" to story.username.ifBlank { "momi.creator" }.take(50),
        "avatar_key" to story.avatarKey.ifBlank { "avatar_aria" }.take(64),
        "image_key" to StoryOverlayCodec.encodeImageKey(story.imageKey, story.backgroundColorKey),
        "image_url" to story.imageUrl,
        "image_data" to story.imageData,
        "media_type" to story.mediaType.ifBlank { "IMAGE" },
        "video_url" to story.videoUrl,
        "video_data" to story.videoData,
        "video_duration_ms" to story.videoDurationMs.coerceIn(0L, MAX_VIDEO_DURATION_MS),
        "audio_title" to story.audioTitle.take(120),
        "audio_url" to story.audioUrl,
        "audio_data" to story.audioData,
        "caption" to StoryOverlayCodec.encodeCaptionWithOverlay(
            caption = story.caption,
            textOverlay = story.textOverlay,
            styleCode = story.textOverlayStyle,
            positionY = story.textOverlayPositionY
        ),
        "created_at" to formatTimestampIso(story.createdAt)
    )

    fun toFollowRow(edge: FollowEdge): Map<String, Any?> = linkedMapOf(
        "id" to edge.id.ifBlank { "${edge.followerUid}_${edge.targetUid}" },
        "follower_uid" to edge.followerUid,
        "target_uid" to edge.targetUid,
        "follower_username" to edge.followerUsername.ifBlank { "momi.creator" }.take(50),
        "target_username" to edge.targetUsername.ifBlank { "momi.creator" }.take(50),
        "created_at" to formatTimestampIso(edge.createdAt)
    )

    fun toNotificationRow(item: NotificationItem): Map<String, Any?> = linkedMapOf(
        "id" to item.id,
        "user_id" to item.userId,
        "actor_uid" to item.actorUid,
        "actor_username" to item.actorUsername.ifBlank { "momi.creator" }.take(50),
        "actor_avatar_key" to item.actorAvatarKey.ifBlank { "avatar_aria" }.take(64),
        "message" to item.message.take(300),
        "time_label" to item.timeLabel.ifBlank { "Just now" }.take(30),
        "section" to item.section.ifBlank { "Today" }.take(30),
        "preview_image_key" to item.previewImageKey,
        "is_follow_action" to item.isFollowAction,
        "is_following_back" to item.isFollowingBack,
        "is_unread" to item.isUnread,
        "created_at" to formatTimestampIso(item.createdAt)
    )

    fun toThreadRow(thread: MessageThread): Map<String, Any?> = linkedMapOf(
        "id" to thread.id,
        "participant_uids" to thread.participantUids,
        "user_a_id" to thread.userAId,
        "user_a_username" to thread.userAUsername.ifBlank { "momi.user" }.take(50),
        "user_a_display_name" to thread.userADisplayName.ifBlank { "Momi User" }.take(80),
        "user_a_avatar_key" to thread.userAAvatarKey.ifBlank { "avatar_aria" }.take(64),
        "user_b_id" to thread.userBId,
        "user_b_username" to thread.userBUsername.ifBlank { "momi.user" }.take(50),
        "user_b_display_name" to thread.userBDisplayName.ifBlank { "Momi User" }.take(80),
        "user_b_avatar_key" to thread.userBAvatarKey.ifBlank { "avatar_aria" }.take(64),
        "last_message" to thread.lastMessage.take(500),
        "last_sender_uid" to thread.lastSenderUid,
        "unread_uid" to thread.unreadUid,
        "time_label" to thread.timeLabel.ifBlank { "Now" }.take(30),
        "unread_count" to thread.unreadCount.coerceAtLeast(0L),
        "is_online" to thread.isOnline,
        "note_text" to thread.noteText,
        "created_at" to formatTimestampIso(thread.createdAt)
    )

    fun toChatMessageRow(threadId: String, msg: ChatMessage): Map<String, Any?> = linkedMapOf(
        "id" to msg.id,
        "thread_id" to threadId,
        "participant_uids" to msg.participantUids,
        "sender_uid" to msg.senderUid,
        "sender_username" to msg.senderUsername.ifBlank { "momi.user" }.take(50),
        "text" to msg.text.ifBlank { " " }.take(1000),
        "message_type" to msg.messageType.ifBlank { "TEXT" },
        "media_url" to msg.mediaUrl,
        "image_data" to msg.imageData,
        "video_data" to msg.videoData,
        "video_duration_ms" to msg.videoDurationMs.coerceAtLeast(0L),
        "audio_data" to msg.audioData,
        "audio_duration_ms" to msg.audioDurationMs.coerceAtLeast(0L),
        "created_at" to formatTimestampIso(msg.createdAt)
    )

    fun toLiveStreamRow(stream: LiveStream): Map<String, Any?> {
        val uid = stream.userId.ifBlank { stream.id }
        return linkedMapOf(
            "id" to uid,
            "user_id" to uid,
            "username" to stream.username.ifBlank { "momi.creator" }.take(50),
            "display_name" to stream.displayName.ifBlank { "Momi Creator" }.take(80),
            "avatar_key" to stream.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "author_verified" to stream.authorVerified,
            "title" to stream.title.take(140),
            "category" to stream.category.ifBlank { "Live" }.take(40),
            "is_live" to stream.isLive,
            "viewer_count" to stream.viewerCount.coerceAtLeast(0L),
            "likes_count" to stream.likesCount.coerceAtLeast(0L),
            "gifts_credits" to stream.giftsCredits.coerceAtLeast(0L),
            "created_at" to formatTimestampIso(stream.createdAt),
            "updated_at" to formatTimestampIso(stream.updatedAt ?: Timestamp.now())
        )
    }

    fun toLiveFrameRow(frame: LiveFrame): Map<String, Any?> {
        val uid = frame.userId.ifBlank { frame.id }
        return linkedMapOf(
            "stream_id" to uid,
            "user_id" to uid,
            "seq" to frame.seq.coerceAtLeast(0L),
            "frame_data" to frame.frameData,
            "camera_enabled" to frame.cameraEnabled,
            "mic_muted" to frame.micMuted,
            "updated_at" to formatTimestampIso(frame.updatedAt ?: Timestamp.now())
        )
    }

    fun toLiveCommentRow(streamId: String, comment: LiveComment): Map<String, Any?> = linkedMapOf(
        "id" to comment.id,
        "stream_id" to streamId,
        "user_id" to comment.userId,
        "username" to comment.username.ifBlank { "momi.creator" }.take(50),
        "avatar_key" to comment.avatarKey.ifBlank { "avatar_aria" }.take(64),
        "text" to comment.text.take(300),
        "created_at" to formatTimestampIso(comment.createdAt)
    )

    fun toMerchantRow(merchant: MerchantAccount): Map<String, Any?> {
        val uid = merchant.userId.ifBlank { merchant.id }
        return linkedMapOf(
            "user_id" to uid,
            "owner_username" to merchant.ownerUsername.ifBlank { "momi.seller" }.take(50),
            "store_name" to merchant.storeName.ifBlank { "Momi Store" }.take(80),
            "tagline" to merchant.tagline.take(160),
            "category" to merchant.category.ifBlank { "Collectibles" }.take(40),
            "location" to merchant.location.take(120),
            "avatar_key" to merchant.avatarKey.ifBlank { "avatar_aria" }.take(64),
            "avatar_url" to merchant.avatarUrl,
            "avatar_data" to merchant.avatarData,
            "is_verified" to merchant.isVerified,
            "verified_by_email" to merchant.verifiedByEmail,
            "total_sales" to merchant.totalSales.coerceAtLeast(0L),
            "created_at" to formatTimestampIso(merchant.createdAt)
        )
    }

    fun toMarketProductRow(product: MarketProduct): Map<String, Any?> = linkedMapOf(
        "id" to product.id,
        "seller_uid" to product.sellerUid,
        "seller_username" to product.sellerUsername.ifBlank { "momi.seller" }.take(50),
        "store_name" to product.storeName.ifBlank { "Momi Store" }.take(80),
        "title" to product.title.ifBlank { "Momi Item" }.take(120),
        "description" to product.description.take(1000),
        "category" to product.category.ifBlank { "Collectibles" }.take(40),
        "price_usd" to product.priceUsd.coerceAtLeast(0.01),
        "original_price_usd" to product.originalPriceUsd.coerceAtLeast(0.0),
        "discount_percent" to product.discountPercent.coerceIn(0L, 95L),
        "free_shipping" to product.freeShipping,
        "image_key" to product.imageKey.ifBlank { "img_post_coastal" }.take(64),
        "image_url" to product.imageUrl,
        "image_data" to product.imageData,
        "extra_image_urls" to product.extraImageUrls,
        "extra_image_datas" to product.extraImageDatas,
        "video_url" to product.videoUrl,
        "video_data" to product.videoData,
        "video_duration_ms" to product.videoDurationMs.coerceIn(0L, MAX_VIDEO_DURATION_MS),
        "stock_count" to product.stockCount.coerceIn(0L, 99999L),
        "is_featured" to product.isFeatured,
        "created_at" to formatTimestampIso(product.createdAt)
    )

    fun toMarketOrderRow(order: MarketOrder): Map<String, Any?> = linkedMapOf(
        "id" to order.id,
        "buyer_uid" to order.buyerUid,
        "buyer_username" to order.buyerUsername.ifBlank { "momi.buyer" }.take(50),
        "seller_uid" to order.sellerUid,
        "seller_store_name" to order.sellerStoreName.ifBlank { "Momi Store" }.take(80),
        "product_id" to order.productId,
        "product_title" to order.productTitle.ifBlank { "Momi Item" }.take(120),
        "product_image_key" to order.productImageKey.ifBlank { "img_post_coastal" }.take(64),
        "product_image_data" to order.productImageData,
        "price_usd" to order.priceUsd.coerceAtLeast(0.01),
        "shipping_address" to order.shippingAddress.ifBlank { "Verified Address" }.take(300),
        "status" to order.status.ifBlank { "PLACED" },
        "created_at" to formatTimestampIso(order.createdAt)
    )

    fun toContentReportRow(report: ContentReport): Map<String, Any?> = linkedMapOf(
        "id" to report.id,
        "reporter_uid" to report.reporterUid,
        "reporter_username" to report.reporterUsername.ifBlank { "momi.user" }.take(50),
        "target_type" to report.targetType.ifBlank { "POST" },
        "target_id" to report.targetId,
        "target_author_uid" to report.targetAuthorUid,
        "target_author_username" to report.targetAuthorUsername,
        "target_preview_text" to report.targetPreviewText.take(240),
        "category" to report.category.ifBlank { "HARASSMENT_SPAM" },
        "reason_detail" to report.reasonDetail.take(400),
        "status" to report.status.ifBlank { "OPEN" },
        "reviewed_by_uid" to report.reviewedByUid,
        "reviewed_by_username" to report.reviewedByUsername,
        "reviewed_by_role" to report.reviewedByRole,
        "resolution_note" to report.resolutionNote,
        "created_at" to formatTimestampIso(report.createdAt)
    )

    fun toWalletRow(wallet: UserWallet): Map<String, Any?> = linkedMapOf(
        "user_id" to wallet.userId.ifBlank { wallet.id },
        "balance" to wallet.balance.coerceAtLeast(0L),
        "total_earned" to wallet.totalEarned.coerceAtLeast(0L),
        "total_spent" to wallet.totalSpent.coerceAtLeast(0L),
        "total_purchased" to wallet.totalPurchased.coerceAtLeast(0L),
        "account_tier" to wallet.accountTier,
        "verification_tier" to wallet.verificationTier,
        "unlocked_perks" to wallet.unlockedPerks,
        "last_tx_id" to wallet.lastTxId,
        "created_at" to formatTimestampIso(wallet.createdAt)
    )

    fun toCreditTransactionRow(tx: CreditTransaction): Map<String, Any?> = linkedMapOf(
        "tx_id" to tx.txId.ifBlank { tx.id },
        "type" to tx.type,
        "sender_uid" to tx.senderUid,
        "sender_username" to tx.senderUsername.ifBlank { "momi.user" }.take(50),
        "recipient_uid" to tx.recipientUid,
        "recipient_username" to tx.recipientUsername.ifBlank { "momi.creator" }.take(50),
        "credits_amount" to tx.creditsAmount.coerceIn(0L, 5000L),
        "price_usd" to tx.priceUsd.coerceAtLeast(0.0),
        "payment_method" to tx.paymentMethod,
        "payment_ref" to tx.paymentRef,
        "gift_key" to tx.giftKey,
        "gift_name" to tx.giftName,
        "stream_id" to tx.streamId,
        "perk_key" to tx.perkKey,
        "post_id" to tx.postId,
        "recipient_claimed" to tx.recipientClaimed,
        "status" to tx.status.ifBlank { "COMPLETED" },
        "created_at" to formatTimestampIso(tx.createdAt)
    )

    fun toAppConfigRow(config: AdMonetizationConfig): Map<String, Any?> = linkedMapOf(
        "id" to "monetization",
        "admob_enabled" to config.admobEnabled,
        "admob_banner_unit_id" to config.admobBannerUnitId.take(120),
        "admob_mrec_unit_id" to config.admobMrecUnitId.take(120),
        "admob_interstitial_unit_id" to config.admobInterstitialUnitId.take(120),
        "start_io_enabled" to config.startIoEnabled,
        "partner_promo_title" to config.partnerPromoTitle.take(80),
        "partner_promo_subtitle" to config.partnerPromoSubtitle.take(180),
        "partner_promo_cta" to config.partnerPromoCta.take(40),
        "announcement_id" to config.announcementId.take(80),
        "announcement_title" to config.announcementTitle.take(120),
        "announcement_body" to config.announcementBody.take(500),
        "latest_version_name" to config.latestVersionName.take(24),
        "release_notes" to config.releaseNotes.take(400),
        "updated_by_email" to config.updatedByEmail.take(120)
    )

    fun toPageRow(page: MomiPage): Map<String, Any?> = linkedMapOf(
        "id" to page.id,
        "owner_uid" to page.ownerUid,
        "owner_username" to page.ownerUsername.ifBlank { "momi.creator" }.take(50),
        "name" to page.name.take(80),
        "handle" to page.handle.take(50),
        "category" to page.category.ifBlank { "Creator" }.take(40),
        "bio" to page.bio.take(300),
        "cover_key" to page.coverKey.ifBlank { "img_post_coastal" }.take(64),
        "avatar_url" to page.avatarUrl,
        "is_verified" to page.isVerified,
        "followers_count" to page.followersCount.coerceAtLeast(0L),
        "created_at" to formatTimestampIso(page.createdAt)
    )

    fun toGroupRow(group: MomiGroup): Map<String, Any?> = linkedMapOf(
        "id" to group.id,
        "owner_uid" to group.ownerUid,
        "owner_username" to group.ownerUsername.ifBlank { "momi.creator" }.take(50),
        "name" to group.name.take(80),
        "category" to group.category.ifBlank { "Community" }.take(40),
        "description" to group.description.take(300),
        "privacy" to if (group.privacy.equals("PRIVATE", ignoreCase = true)) "PRIVATE" else "PUBLIC",
        "cover_key" to group.coverKey.ifBlank { "img_post_architecture" }.take(64),
        "members_count" to group.membersCount.coerceAtLeast(1L),
        "created_at" to formatTimestampIso(group.createdAt)
    )

    fun toShowcaseAccountRow(account: ShowcaseAccount): Map<String, Any?> = linkedMapOf(
        "username" to account.username.take(50),
        "display_name" to account.displayName.take(80),
        "avatar_key" to account.avatarKey.ifBlank { "avatar_elena" }.take(64),
        "avatar_url" to account.avatarUrl,
        "bio" to account.bio.take(240),
        "category" to account.category.ifBlank { "Creator" }.take(40),
        "location" to account.location.take(120),
        "is_verified" to account.isVerified,
        "is_authorized" to account.isAuthorized,
        "updated_by_email" to account.updatedByEmail.take(120)
    )

    fun buildMigrationBundleMap(
        profiles: List<UserProfile>,
        posts: List<Post>,
        stories: List<Story>,
        merchants: List<MerchantAccount>,
        products: List<MarketProduct>,
        wallet: UserWallet?,
        monetizationConfig: AdMonetizationConfig
    ): Map<String, Any?> {
        val realPosts = posts.filterNot { it.id.startsWith("showcase_") }
        val realStories = stories.filterNot { it.id.startsWith("showcase_") }
        return linkedMapOf(
            "schemaVersion" to "momi_supabase_v1",
            "exportedAtUtc" to formatTimestampIso(Timestamp.now()),
            "appId" to BuildConfig.APPLICATION_ID,
            "tables" to linkedMapOf(
                "profiles" to profiles.map(::toProfileRow),
                "posts" to realPosts.map(::toPostRow),
                "stories" to realStories.map(::toStoryRow),
                "merchants" to merchants.map(::toMerchantRow),
                "market_products" to products.map(::toMarketProductRow),
                "wallets" to if (wallet != null) listOf(toWalletRow(wallet)) else emptyList(),
                "app_config" to listOf(toAppConfigRow(monetizationConfig))
            )
        )
    }

    fun buildMigrationBundleJson(
        profiles: List<UserProfile>,
        posts: List<Post>,
        stories: List<Story>,
        merchants: List<MerchantAccount>,
        products: List<MarketProduct>,
        wallet: UserWallet?,
        monetizationConfig: AdMonetizationConfig
    ): String = toJsonString(
        buildMigrationBundleMap(
            profiles = profiles,
            posts = posts,
            stories = stories,
            merchants = merchants,
            products = products,
            wallet = wallet,
            monetizationConfig = monetizationConfig
        ),
        indent = 0
    )

    fun toJsonString(value: Any?, indent: Int = 0): String {
        val sb = StringBuilder()
        appendJsonValue(sb, value, indent)
        return sb.toString()
    }

    private fun appendJsonValue(sb: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean, is Int, is Long -> sb.append(value.toString())
            is Double -> if (value.isFinite()) sb.append(value.toString()) else sb.append("0")
            is Float -> if (value.isFinite()) sb.append(value.toString()) else sb.append("0")
            is String -> appendQuotedString(sb, value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val entries = value.entries.toList()
                entries.forEachIndexed { idx, (k, v) ->
                    repeat(indent + 2) { sb.append(' ') }
                    appendQuotedString(sb, k.toString())
                    sb.append(": ")
                    appendJsonValue(sb, v, indent + 2)
                    if (idx < entries.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                repeat(indent) { sb.append(' ') }
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                items.forEachIndexed { idx, item ->
                    repeat(indent + 2) { sb.append(' ') }
                    appendJsonValue(sb, item, indent + 2)
                    if (idx < items.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                repeat(indent) { sb.append(' ') }
                sb.append(']')
            }
            else -> appendQuotedString(sb, value.toString())
        }
    }

    private fun appendQuotedString(sb: StringBuilder, raw: String) {
        sb.append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u%04x".format(Locale.US, ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }
}

class MomiSupabaseClient(
    rawBaseUrl: String = BuildConfig.SUPABASE_URL,
    rawAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = defaultHttpClient
) {
    val baseUrl: String = MomiSupabaseSchema.normalizeSupabaseBaseUrl(rawBaseUrl)
    private val anonKey: String = rawAnonKey.trim().takeIf { MomiSupabaseSchema.isSafeClientAnonKey(it) }.orEmpty()

    fun isConfigured(): Boolean = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    private fun resolvedAccessToken(explicit: String?): String? =
        explicit?.trim()?.takeIf { it.isNotEmpty() } ?: activeSession?.accessToken?.takeIf { it.isNotBlank() }

    suspend fun ensureFreshAccessToken(
        explicit: String? = null,
        forceRefresh: Boolean = false
    ): String? {
        if (!explicit.isNullOrBlank()) return explicit.trim()
        val current = activeSession ?: return null
        val needsRefresh = forceRefresh || MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(current.accessToken)
        if (!needsRefresh) return current.accessToken

        return refreshMutex.withLock {
            val latest = activeSession ?: return@withLock null
            val stillNeedsRefresh = forceRefresh || MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(latest.accessToken)
            if (!stillNeedsRefresh) {
                return@withLock latest.accessToken
            }
            if (forceRefresh && latest.accessToken != current.accessToken &&
                !MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(latest.accessToken)
            ) {
                return@withLock latest.accessToken
            }
            val rt = latest.refreshToken
            if (rt.isBlank()) {
                return@withLock if (MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(latest.accessToken, skewMs = 0L)) {
                    null
                } else {
                    latest.accessToken
                }
            }
            val refreshed = refreshSession(rt)
            refreshed.getOrNull()?.let { fresh ->
                return@withLock fresh.accessToken
            }
            val errMsg = refreshed.exceptionOrNull()?.message.orEmpty()
            if (errMsg.startsWith("AUTH_EXPIRED:")) {
                setActiveSessionAndPersist(null)
                return@withLock null
            }
            if (MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(latest.accessToken, skewMs = 0L)) {
                null
            } else {
                latest.accessToken
            }
        }
    }

    private fun isJwtExpiredHttpResponse(statusCode: Int, responseBody: String): Boolean {
        if (statusCode != 401) return false
        return responseBody.contains("JWT expired", ignoreCase = true) ||
            responseBody.contains("PGRST303", ignoreCase = true) ||
            responseBody.contains("invalid JWT", ignoreCase = true)
    }

    suspend fun checkConnection(): SupabaseHealthResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext SupabaseHealthResult(
                configured = false,
                connected = false,
                message = "Waiting for SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets."
            )
        }
        val startMs = System.currentTimeMillis()
        var token = ensureFreshAccessToken()

        fun buildHealthRequest(bearerToken: String?): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/rest/v1/app_config?select=id&limit=1")
                .header("Accept", "application/json")
                .get()
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        runCatching {
            var code = 0
            var rawBody = ""
            var succeeded = false
            httpClient.newCall(buildHealthRequest(token)).execute().use { response ->
                code = response.code
                rawBody = response.body?.string().orEmpty()
                succeeded = response.isSuccessful
            }
            if (!succeeded && isJwtExpiredHttpResponse(code, rawBody)) {
                token = ensureFreshAccessToken(forceRefresh = true)
                httpClient.newCall(buildHealthRequest(token)).execute().use { retryResponse ->
                    code = retryResponse.code
                    rawBody = retryResponse.body?.string().orEmpty()
                    succeeded = retryResponse.isSuccessful
                }
            }
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (succeeded) {
                SupabaseHealthResult(
                    configured = true,
                    connected = true,
                    statusCode = code,
                    latencyMs = latency,
                    message = "Connected to Supabase ($latency ms) — Cloud Sync & Storage active."
                )
            } else {
                val hint = when {
                    isJwtExpiredHttpResponse(code, rawBody) ->
                        "Session token expired — sign out and sign back in to refresh your Supabase session."
                    code == 404 ->
                        "Connected to project, but run supabase/schema.sql + supabase/enable_sync.sql in SQL Editor."
                    code == 401 || code == 403 ->
                        "Supabase rejected key/permissions — run supabase/enable_sync.sql in SQL Editor."
                    else -> "Supabase responded HTTP $code."
                }
                SupabaseHealthResult(
                    configured = true,
                    connected = false,
                    statusCode = code,
                    latencyMs = latency,
                    message = hint
                )
            }
        }.getOrElse { error ->
            SupabaseHealthResult(
                configured = true,
                connected = false,
                message = "Network error reaching Supabase: ${error.localizedMessage ?: "unknown error"}"
            )
        }
    }

    private fun extractSupabaseAuthError(rawBody: String, fallback: String): String {
        val msg = runCatching {
            val o = JSONObject(rawBody)
            o.optString("msg").takeIf { it.isNotBlank() }
                ?: o.optString("error_description").takeIf { it.isNotBlank() }
                ?: o.optString("message").takeIf { it.isNotBlank() }
                ?: o.optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
        return msg ?: fallback
    }

    private fun parseAuthSession(
        json: JSONObject,
        displayNameHint: String? = null,
        avatarUrlHint: String? = null,
        emailHint: String? = null,
        phoneHint: String? = null
    ): SupabaseAuthSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userObj = json.optJSONObject("user")
        val userId = userObj?.optString("id").orEmpty()
        val email = userObj?.optString("email")?.takeIf { it.isNotBlank() }
            ?: emailHint?.let { MomiSupabaseSchema.normalizeEmail(it) }?.takeIf { it.isNotBlank() }
        val meta = userObj?.optJSONObject("user_metadata")
        val rawPhone = userObj?.optString("phone")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("phone")?.takeIf { it.isNotBlank() }
            ?: phoneHint?.takeIf { it.isNotBlank() }
        val phone = rawPhone?.let { MomiSupabaseSchema.normalizePhoneE164(it).ifBlank { it } }
        val displayName = meta?.optString("full_name")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("name")?.takeIf { it.isNotBlank() }
            ?: displayNameHint?.takeIf { it.isNotBlank() }
        val avatarUrl = meta?.optString("avatar_url")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("picture")?.takeIf { it.isNotBlank() }
            ?: avatarUrlHint?.takeIf { it.isNotBlank() }
        require(access.isNotBlank() && userId.isNotBlank()) {
            "Invalid Supabase auth session response"
        }
        return SupabaseAuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = email,
            displayName = displayName,
            avatarUrl = avatarUrl,
            phone = phone
        )
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        displayNameHint: String? = null,
        avatarUrlHint: String? = null
    ): Result<SupabaseAuthSession> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
            }
            if (idToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Missing Google ID token."))
            }
            val payload = MomiSupabaseSchema.toJsonString(
                linkedMapOf("provider" to "google", "id_token" to idToken)
            )
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=id_token")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = extractSupabaseAuthError(rawBody, "HTTP ${response.code}")
                        val hint = if (detail.contains("not enabled", ignoreCase = true) ||
                            detail.contains("audience", ignoreCase = true)
                        ) {
                            "Google login needs to be enabled in Supabase Authentication -> Providers -> Google (or sign in with Email below). [$detail]"
                        } else {
                            "Supabase Google sign-in failed: $detail"
                        }
                        error(hint)
                    }
                    val session = parseAuthSession(JSONObject(rawBody), displayNameHint, avatarUrlHint)
                    setActiveSessionAndPersist(session)
                    session
                }
            }
        }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<SupabaseAuthSession> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
            }
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || password.length < 6) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid email and a password of at least 6 characters."))
            }
            val payload = MomiSupabaseSchema.toJsonString(
                linkedMapOf("email" to cleanEmail, "password" to password)
            )
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = extractSupabaseAuthError(rawBody, "Invalid email or password (HTTP ${response.code})")
                        error(detail)
                    }
                    val session = parseAuthSession(JSONObject(rawBody))
                    setActiveSessionAndPersist(session)
                    session
                }
            }
        }

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String? = null
    ): Result<SupabaseSignUpResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        if (!MomiSupabaseSchema.isValidEmailAddress(cleanEmail) || password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid email and password (6+ characters)."))
        }
        // Enforce 1 email = 1 Momi account before issuing signup request.
        val existingByEmail = fetchProfileByEmail(cleanEmail)
        if (existingByEmail != null) {
            return@withContext Result.failure(
                IllegalStateException(
                    "An account already exists for $cleanEmail. One email can only have one Momi account — please sign in with Email or Google."
                )
            )
        }
        val bodyMap = linkedMapOf<String, Any?>(
            "email" to cleanEmail,
            "password" to password
        )
        val cleanName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanName != null) {
            bodyMap["data"] = linkedMapOf("full_name" to cleanName)
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(MomiSupabaseSchema.toJsonString(bodyMap).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractSupabaseAuthError(rawBody, "Sign up failed (HTTP ${response.code})")
                    error(detail)
                }
                val json = JSONObject(rawBody)
                val access = json.optString("access_token")
                val identities = json.optJSONObject("user")?.optJSONArray("identities")
                if (access.isBlank() && identities != null && identities.length() == 0) {
                    error("An account already exists for $cleanEmail. One email can only have one Momi account — please sign in with Email or Google.")
                }
                if (access.isNotBlank()) {
                    val session = parseAuthSession(json, displayNameHint = cleanName, emailHint = cleanEmail)
                    setActiveSessionAndPersist(session)
                    SupabaseSignUpResult(session = session, confirmationRequired = false, email = cleanEmail)
                } else {
                    SupabaseSignUpResult(session = null, confirmationRequired = true, email = cleanEmail)
                }
            }
        }
    }

    suspend fun verifyEmailSignupOtp(
        email: String,
        otpCode: String,
        displayName: String? = null
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        val cleanCode = otpCode.trim().filter { it.isDigit() || it.isLetter() }
        if (cleanEmail.isBlank() || cleanCode.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Enter the 6-digit verification code sent to $cleanEmail."))
        }
        val cleanName = displayName?.trim()?.takeIf { it.isNotEmpty() }

        fun sendVerify(otpType: String): Pair<Int, String> {
            val payload = MomiSupabaseSchema.toJsonString(
                linkedMapOf(
                    "type" to otpType,
                    "email" to cleanEmail,
                    "token" to cleanCode
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/verify")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                return response.code to response.body?.string().orEmpty()
            }
        }

        runCatching {
            var (code, rawBody) = sendVerify("signup")
            if (code !in 200..299) {
                val fallback = sendVerify("email")
                if (fallback.first in 200..299) {
                    code = fallback.first
                    rawBody = fallback.second
                }
            }
            if (code !in 200..299) {
                val detail = extractSupabaseAuthError(rawBody, "Invalid or expired verification code (HTTP $code)")
                error(detail)
            }
            val session = parseAuthSession(JSONObject(rawBody), displayNameHint = cleanName, emailHint = cleanEmail)
            setActiveSessionAndPersist(session)
            session
        }
    }

    suspend fun resendEmailSignupOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your email address first."))
        }
        val payload = MomiSupabaseSchema.toJsonString(
            linkedMapOf("type" to "signup", "email" to cleanEmail)
        )
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/resend")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error(extractSupabaseAuthError(rawBody, "Could not resend code (HTTP ${response.code})"))
                }
                Unit
            }
        }
    }

    suspend fun sendWhatsAppOtp(
        phone: String,
        displayName: String? = null,
        email: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        val cleanPhone = MomiSupabaseSchema.normalizePhoneE164(phone)
        if (cleanPhone.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a valid WhatsApp number in international E.164 format (e.g. +923001234567).")
            )
        }
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        if (cleanEmail.isNotEmpty()) {
            if (!MomiSupabaseSchema.isValidEmailAddress(cleanEmail)) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid email address or leave it blank."))
            }
            val existingByEmail = fetchProfileByEmail(cleanEmail)
            val existingPhone = MomiSupabaseSchema.normalizePhoneE164(existingByEmail?.phone)
            if (existingByEmail != null && existingPhone != cleanPhone) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "An account already exists for $cleanEmail. One email can only have one Momi account — please sign in with Email or Google."
                    )
                )
            }
        }

        val bodyMap = linkedMapOf<String, Any?>(
            "phone" to cleanPhone,
            "channel" to "whatsapp",
            "create_user" to true
        )
        val metaData = linkedMapOf<String, Any?>("phone" to cleanPhone)
        displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { metaData["full_name"] = it }
        if (cleanEmail.isNotEmpty()) metaData["email"] = cleanEmail
        bodyMap["data"] = metaData

        val request = Request.Builder()
            .url("$baseUrl/auth/v1/otp")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(MomiSupabaseSchema.toJsonString(bodyMap).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractSupabaseAuthError(rawBody, "Failed to send WhatsApp OTP (HTTP ${response.code})")
                    val hint = if (detail.contains("provider", ignoreCase = true) ||
                        detail.contains("whatsapp", ignoreCase = true) ||
                        detail.contains("sms", ignoreCase = true) ||
                        detail.contains("not enabled", ignoreCase = true) ||
                        detail.contains("twilio", ignoreCase = true)
                    ) {
                        "WhatsApp OTP requires Phone/WhatsApp (Twilio/Meta) to be enabled in Supabase Authentication -> Providers -> Phone. [$detail]"
                    } else {
                        detail
                    }
                    error(hint)
                }
                cleanPhone
            }
        }
    }

    suspend fun verifyWhatsAppOtp(
        phone: String,
        otpCode: String,
        displayName: String? = null,
        email: String? = null
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        val cleanPhone = MomiSupabaseSchema.normalizePhoneE164(phone)
        if (cleanPhone.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a valid WhatsApp number in international format (e.g. +923001234567).")
            )
        }
        val cleanCode = otpCode.trim().filter { it.isDigit() }
        if (cleanCode.length !in 4..8) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter the 6-digit WhatsApp OTP code sent to $cleanPhone.")
            )
        }
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        if (cleanEmail.isNotEmpty()) {
            if (!MomiSupabaseSchema.isValidEmailAddress(cleanEmail)) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid email address or leave it blank."))
            }
            val existingByEmail = fetchProfileByEmail(cleanEmail)
            val existingPhone = MomiSupabaseSchema.normalizePhoneE164(existingByEmail?.phone)
            if (existingByEmail != null && existingPhone != cleanPhone) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "An account already exists for $cleanEmail. One email can only have one Momi account — please sign in with Email or Google."
                    )
                )
            }
        }

        val payload = MomiSupabaseSchema.toJsonString(
            linkedMapOf(
                "type" to "sms",
                "phone" to cleanPhone,
                "token" to cleanCode
            )
        )
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/verify")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractSupabaseAuthError(rawBody, "Invalid or expired WhatsApp OTP (HTTP ${response.code})")
                    error(detail)
                }
                val cleanName = displayName?.trim()?.takeIf { it.isNotEmpty() }
                val session = parseAuthSession(
                    JSONObject(rawBody),
                    displayNameHint = cleanName,
                    emailHint = cleanEmail.takeIf { it.isNotEmpty() },
                    phoneHint = cleanPhone
                )
                setActiveSessionAndPersist(session)
                ensureProfileExists(
                    userId = session.userId,
                    username = cleanName ?: "wa_${cleanPhone.takeLast(6)}",
                    displayName = cleanName ?: session.displayName ?: "Momi Creator",
                    avatarUrl = session.avatarUrl,
                    email = session.email.orEmpty().ifBlank { cleanEmail },
                    phone = session.phone.orEmpty().ifBlank { cleanPhone }
                )
                session
            }
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<SupabaseAuthSession> =
        withContext(Dispatchers.IO) {
            if (!isConfigured() || refreshToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Cannot refresh Supabase session."))
            }
            val payload = MomiSupabaseSchema.toJsonString(linkedMapOf("refresh_token" to refreshToken))
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = extractSupabaseAuthError(rawBody, "Session expired (HTTP ${response.code})")
                        error("AUTH_EXPIRED:${response.code}:$detail")
                    }
                    val session = parseAuthSession(JSONObject(rawBody))
                    setActiveSessionAndPersist(session)
                    session
                }
            }
        }

    suspend fun signOutSession(accessToken: String? = null) = withContext(Dispatchers.IO) {
        val token = resolvedAccessToken(accessToken)
        setActiveSessionAndPersist(null)
        if (!isConfigured() || token.isNullOrBlank()) return@withContext
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/logout")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
    }

    suspend fun upsertRows(
        table: String,
        rows: List<Map<String, Any?>>,
        accessToken: String? = null,
        onConflict: String? = null,
        ignoreDuplicates: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (table !in MomiSupabaseSchema.TABLES) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported Supabase table: $table"))
        }
        if (rows.isEmpty()) return@withContext Result.success(0)

        val conflictQuery = onConflict?.trim()?.takeIf { it.isNotEmpty() }?.let { col ->
            require(col.matches(Regex("^[a-z0-9_,]+$"))) { "Invalid conflict column spec" }
            "?on_conflict=${URLEncoder.encode(col, "UTF-8")}"
        }.orEmpty()

        val preferHeader = if (ignoreDuplicates) {
            "resolution=ignore-duplicates,return=minimal"
        } else {
            "resolution=merge-duplicates,return=minimal"
        }

        var workingRows = rows
        var token = ensureFreshAccessToken(explicit = accessToken)

        fun buildUpsertReq(bearerToken: String?, payloadJson: String): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/rest/v1/$table$conflictQuery")
                .header("Content-Type", "application/json")
                .header("Prefer", preferHeader)
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        runCatching {
            var code = 0
            var bodyString = ""
            var ok = false
            var attempts = 0
            while (attempts < 4) {
                attempts++
                val jsonBody = MomiSupabaseSchema.toJsonString(workingRows)
                httpClient.newCall(buildUpsertReq(token, jsonBody)).execute().use { response ->
                    code = response.code
                    bodyString = response.body?.string().orEmpty()
                    ok = response.isSuccessful
                }
                if (!ok && accessToken.isNullOrBlank() && code == 401) {
                    token = ensureFreshAccessToken(forceRefresh = true)
                    httpClient.newCall(buildUpsertReq(token, jsonBody)).execute().use { retry ->
                        code = retry.code
                        bodyString = retry.body?.string().orEmpty()
                        ok = retry.isSuccessful
                    }
                }
                if (ok) break
                if (code == 400 && bodyString.contains("PGRST204", ignoreCase = true)) {
                    val missingCol = Regex("Could not find the '([^']+)' column of")
                        .find(bodyString)?.groupValues?.getOrNull(1)
                    if (!missingCol.isNullOrBlank() &&
                        missingCol != "id" &&
                        missingCol != "user_id" &&
                        workingRows.any { it.containsKey(missingCol) }
                    ) {
                        android.util.Log.w("MomiSupabase", "Stripping missing column '$missingCol' from $table upsert and retrying")
                        workingRows = workingRows.map { row -> row - missingCol }
                        continue
                    }
                }
                break
            }
            if (!ok) {
                android.util.Log.e("MomiSupabase", "Upsert into $table failed HTTP $code: $bodyString")
                val detail = bodyString.take(160).takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                error("Upsert into $table failed (HTTP $code)$detail")
            }
            workingRows.size
        }
    }

    suspend fun patchRows(
        table: String,
        filterQuery: String,
        fields: Map<String, Any?>,
        accessToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (table !in MomiSupabaseSchema.TABLES) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported Supabase table: $table"))
        }
        val cleanQuery = filterQuery.trim().trimStart('?')
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Filter query required for patch"))
        }
        var workingFields = fields
        var token = ensureFreshAccessToken(explicit = accessToken)

        fun buildPatchReq(bearerToken: String?, payloadJson: String): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/rest/v1/$table?$cleanQuery")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        runCatching {
            var code = 0
            var bodyString = ""
            var ok = false
            var attempts = 0
            while (attempts < 4 && workingFields.isNotEmpty()) {
                attempts++
                val jsonBody = MomiSupabaseSchema.toJsonString(workingFields)
                httpClient.newCall(buildPatchReq(token, jsonBody)).execute().use { response ->
                    code = response.code
                    bodyString = response.body?.string().orEmpty()
                    ok = response.isSuccessful
                }
                if (!ok && accessToken.isNullOrBlank() && code == 401) {
                    token = ensureFreshAccessToken(forceRefresh = true)
                    httpClient.newCall(buildPatchReq(token, jsonBody)).execute().use { retry ->
                        code = retry.code
                        bodyString = retry.body?.string().orEmpty()
                        ok = retry.isSuccessful
                    }
                }
                if (ok) break
                if (code == 400 && bodyString.contains("PGRST204", ignoreCase = true)) {
                    val missingCol = Regex("Could not find the '([^']+)' column of")
                        .find(bodyString)?.groupValues?.getOrNull(1)
                    if (!missingCol.isNullOrBlank() && workingFields.containsKey(missingCol)) {
                        android.util.Log.w("MomiSupabase", "Stripping missing column '$missingCol' from $table patch and retrying")
                        workingFields = workingFields - missingCol
                        if (workingFields.isEmpty()) {
                            ok = true
                            break
                        }
                        continue
                    }
                }
                break
            }
            if (!ok) {
                android.util.Log.e("MomiSupabase", "Patch $table failed HTTP $code: $bodyString")
                error("Update $table failed (HTTP $code)")
            }
            Unit
        }
    }

    suspend fun deleteRows(
        table: String,
        filterQuery: String,
        accessToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (table !in MomiSupabaseSchema.TABLES) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported Supabase table: $table"))
        }
        val cleanQuery = filterQuery.trim().trimStart('?')
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Filter query required for delete"))
        }
        var token = ensureFreshAccessToken(explicit = accessToken)

        fun buildDeleteReq(bearerToken: String?): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/rest/v1/$table?$cleanQuery")
                .header("Prefer", "return=minimal")
                .delete()
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        runCatching {
            var code = 0
            var bodyString = ""
            var ok = false
            httpClient.newCall(buildDeleteReq(token)).execute().use { response ->
                code = response.code
                bodyString = response.body?.string().orEmpty()
                ok = response.isSuccessful
            }
            if (!ok && accessToken.isNullOrBlank() && code == 401) {
                token = ensureFreshAccessToken(forceRefresh = true)
                httpClient.newCall(buildDeleteReq(token)).execute().use { retry ->
                    code = retry.code
                    bodyString = retry.body?.string().orEmpty()
                    ok = retry.isSuccessful
                }
            }
            if (!ok) {
                android.util.Log.e("MomiSupabase", "Delete $table failed HTTP $code: $bodyString")
                error("Delete $table failed (HTTP $code)")
            }
            Unit
        }
    }

    suspend fun uploadStorageBytes(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String,
        accessToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (bucket !in MomiSupabaseSchema.STORAGE_BUCKETS) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported storage bucket: $bucket"))
        }
        val safePath = objectPath.trim().trimStart('/').split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
        if (safePath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid storage object path"))
        }
        if (bytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Media payload is empty"))
        }

        val mediaType = contentType.toMediaType()
        var token = ensureFreshAccessToken(explicit = accessToken)

        fun buildUploadReq(bearerToken: String?): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/storage/v1/object/$bucket/$safePath")
                .header("x-upsert", "true")
                .post(bytes.toRequestBody(mediaType))
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        runCatching {
            var code = 0
            var errBody = ""
            var ok = false
            httpClient.newCall(buildUploadReq(token)).execute().use { response ->
                code = response.code
                errBody = response.body?.string().orEmpty()
                ok = response.isSuccessful
            }
            if (!ok && accessToken.isNullOrBlank() && code == 401) {
                token = ensureFreshAccessToken(forceRefresh = true)
                httpClient.newCall(buildUploadReq(token)).execute().use { retry ->
                    code = retry.code
                    errBody = retry.body?.string().orEmpty()
                    ok = retry.isSuccessful
                }
            }
            if (!ok) {
                android.util.Log.e("MomiSupabase", "Storage upload $bucket/$safePath failed HTTP $code: $errBody")
                error("Storage upload failed (HTTP $code)")
            }
            "$baseUrl/storage/v1/object/public/$bucket/$safePath"
        }
    }

    suspend fun uploadStorageFile(
        bucket: String,
        objectPath: String,
        file: File,
        contentType: String,
        accessToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (bucket !in MomiSupabaseSchema.STORAGE_BUCKETS) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported storage bucket: $bucket"))
        }
        val safePath = objectPath.trim().trimStart('/').split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
        if (safePath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid storage object path"))
        }
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Media file could not be read"))
        }
        // Use chunked TUS resumable upload directly for medium/long videos (> 6 MB) to avoid timeouts / 413s
        if (file.length() > 6_000_000L) {
            return@withContext uploadStorageFileResumableTus(bucket, safePath, file, contentType, accessToken)
        }

        val mediaType = contentType.toMediaType()
        var token = ensureFreshAccessToken(explicit = accessToken)

        fun buildStreamUploadReq(bearerToken: String?): Request {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/storage/v1/object/$bucket/$safePath")
                .header("x-upsert", "true")
                .post(file.asRequestBody(mediaType))
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        var code = 0
        var errBody = ""
        var ok = false
        val directResult = runCatching {
            uploadHttpClient.newCall(buildStreamUploadReq(token)).execute().use { response ->
                code = response.code
                errBody = response.body?.string().orEmpty()
                ok = response.isSuccessful
            }
            if (!ok && accessToken.isNullOrBlank() && code == 401) {
                token = ensureFreshAccessToken(forceRefresh = true)
                uploadHttpClient.newCall(buildStreamUploadReq(token)).execute().use { retry ->
                    code = retry.code
                    errBody = retry.body?.string().orEmpty()
                    ok = retry.isSuccessful
                }
            }
            if (!ok) {
                android.util.Log.e("MomiSupabase", "Streaming upload $bucket/$safePath failed HTTP $code: $errBody")
                val detail = errBody.trim().take(160)
                error(if (detail.isNotEmpty()) "Storage upload failed (HTTP $code): $detail" else "Storage upload failed (HTTP $code)")
            }
            "$baseUrl/storage/v1/object/public/$bucket/$safePath"
        }
        if (directResult.isSuccess) return@withContext directResult
        val directEx = directResult.exceptionOrNull()
        if (code in setOf(408, 413, 429, 500, 502, 503, 504) || directEx is IOException) {
            return@withContext uploadStorageFileResumableTus(bucket, safePath, file, contentType, accessToken)
        }
        directResult
    }

    private fun encodeTusMetadata(entries: Map<String, String>): String {
        return entries.entries.joinToString(",") { (k, v) ->
            val b64 = android.util.Base64.encodeToString(v.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            "$k $b64"
        }
    }

    private suspend fun uploadStorageFileResumableTus(
        bucket: String,
        safePath: String,
        file: File,
        contentType: String,
        accessToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val totalBytes = file.length()
        if (totalBytes <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Media file is empty"))
        }
        var token = ensureFreshAccessToken(explicit = accessToken)
        val metadataHeader = encodeTusMetadata(
            linkedMapOf(
                "bucketName" to bucket,
                "objectName" to safePath,
                "contentType" to contentType,
                "cacheControl" to "3600"
            )
        )

        fun buildCreateReq(bearerToken: String?): Request {
            val builder = Request.Builder()
                .url("$baseUrl/storage/v1/upload/resumable")
                .header("Tus-Resumable", "1.0.0")
                .header("Upload-Length", totalBytes.toString())
                .header("Upload-Metadata", metadataHeader)
                .header("x-upsert", "true")
                .post(ByteArray(0).toRequestBody(null))
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                builder.header(k, v)
            }
            return builder.build()
        }

        fun buildHeadReq(uploadUrl: String, bearerToken: String?): Request {
            val builder = Request.Builder()
                .url(uploadUrl)
                .header("Tus-Resumable", "1.0.0")
                .head()
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                builder.header(k, v)
            }
            return builder.build()
        }

        runCatching {
            var locationHeader: String? = null
            var createCode = 0
            var createBody = ""
            for (attempt in 1..3) {
                try {
                    uploadHttpClient.newCall(buildCreateReq(token)).execute().use { response ->
                        createCode = response.code
                        createBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            locationHeader = response.header("Location")
                        }
                    }
                    if (!locationHeader.isNullOrBlank()) break
                    if (accessToken.isNullOrBlank() && createCode == 401) {
                        token = ensureFreshAccessToken(forceRefresh = true)
                    } else if (createCode !in setOf(408, 429, 500, 502, 503, 504)) {
                        break
                    }
                } catch (io: IOException) {
                    createBody = io.localizedMessage ?: "network error"
                    if (attempt == 3) throw io
                }
                delay(500L * attempt)
            }
            val rawLoc = locationHeader?.trim().orEmpty()
            if (rawLoc.isEmpty()) {
                error("Resumable video upload initialization failed (HTTP $createCode: ${createBody.take(140)})")
            }
            val uploadUrl = when {
                rawLoc.startsWith("https://", ignoreCase = true) || rawLoc.startsWith("http://", ignoreCase = true) -> rawLoc
                rawLoc.startsWith("/") -> "$baseUrl$rawLoc"
                else -> "$baseUrl/storage/v1/upload/resumable/$rawLoc"
            }

            val chunkSize = 5 * 1024 * 1024 // 5 MB chunks
            val buffer = ByteArray(chunkSize)
            RandomAccessFile(file, "r").use { raf ->
                var offset = 0L
                while (offset < totalBytes) {
                    raf.seek(offset)
                    val read = raf.read(buffer)
                    if (read <= 0) break
                    val chunkBytes = if (read == buffer.size) buffer else buffer.copyOf(read)

                    fun buildPatchReq(bearerToken: String?, chunkOffset: Long): Request {
                        val builder = Request.Builder()
                            .url(uploadUrl)
                            .header("Tus-Resumable", "1.0.0")
                            .header("Upload-Offset", chunkOffset.toString())
                            .header("Content-Type", "application/offset+octet-stream")
                            .patch(chunkBytes.toRequestBody(TUS_OFFSET_MEDIA_TYPE))
                        MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                            builder.header(k, v)
                        }
                        return builder.build()
                    }

                    var patchOk = false
                    var patchCode = 0
                    var patchErr = ""
                    var nextOffsetHeader: String? = null
                    for (attempt in 1..3) {
                        token = ensureFreshAccessToken(explicit = accessToken, forceRefresh = (patchCode == 401))
                        try {
                            uploadHttpClient.newCall(buildPatchReq(token, offset)).execute().use { response ->
                                patchCode = response.code
                                patchOk = response.isSuccessful
                                nextOffsetHeader = response.header("Upload-Offset")
                                if (!patchOk) patchErr = response.body?.string().orEmpty()
                            }
                            if (patchOk) break
                            if (patchCode == 409) {
                                // Query server offset in case previous chunk partially succeeded
                                runCatching {
                                    uploadHttpClient.newCall(buildHeadReq(uploadUrl, token)).execute().use { headRes ->
                                        val serverOff = headRes.header("Upload-Offset")?.toLongOrNull()
                                        if (headRes.isSuccessful && serverOff != null && serverOff > offset) {
                                            nextOffsetHeader = serverOff.toString()
                                            patchOk = true
                                        }
                                    }
                                }
                                if (patchOk) break
                            }
                            if (accessToken.isNullOrBlank() && patchCode == 401) {
                                token = ensureFreshAccessToken(forceRefresh = true)
                            } else if (patchCode !in setOf(408, 429, 500, 502, 503, 504)) {
                                break
                            }
                        } catch (io: IOException) {
                            patchErr = io.localizedMessage ?: "network interruption"
                            if (attempt == 3) throw io
                        }
                        delay(700L * attempt)
                    }
                    if (!patchOk) {
                        error("Resumable video upload chunk failed at offset $offset (HTTP $patchCode: ${patchErr.take(140)})")
                    }
                    offset = nextOffsetHeader?.toLongOrNull() ?: (offset + read)
                }
            }
            "$baseUrl/storage/v1/object/public/$bucket/$safePath"
        }
    }

    suspend fun fetchProfileByUserId(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        if (!isConfigured() || userId.isBlank()) return@withContext null
        val encUid = URLEncoder.encode(userId.trim(), "UTF-8")
        runCatching {
            parseProfiles(fetchJsonArray("$baseUrl/rest/v1/profiles?select=*&user_id=eq.$encUid&limit=1")).firstOrNull()
        }.getOrNull()
    }

    suspend fun fetchProfileByEmail(email: String): UserProfile? = withContext(Dispatchers.IO) {
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        if (!isConfigured() || cleanEmail.isBlank()) return@withContext null
        val encEmail = URLEncoder.encode(cleanEmail, "UTF-8")
        runCatching {
            parseProfiles(fetchJsonArray("$baseUrl/rest/v1/profiles?select=*&email=ilike.$encEmail&limit=1")).firstOrNull()
        }.getOrNull()
    }

    suspend fun fetchProfileByPhone(phone: String): UserProfile? = withContext(Dispatchers.IO) {
        val cleanPhone = MomiSupabaseSchema.normalizePhoneE164(phone)
        if (!isConfigured() || cleanPhone.isBlank()) return@withContext null
        val encPhone = URLEncoder.encode(cleanPhone, "UTF-8")
        runCatching {
            parseProfiles(fetchJsonArray("$baseUrl/rest/v1/profiles?select=*&phone=eq.$encPhone&limit=1")).firstOrNull()
        }.getOrNull()
    }

    suspend fun ensureProfileExists(
        userId: String,
        username: String,
        displayName: String = username,
        avatarKey: String = "avatar_aria",
        avatarUrl: String? = null,
        email: String = "",
        phone: String = ""
    ): Result<Unit> {
        if (!isConfigured() || userId.isBlank()) return Result.success(Unit)
        val cleanEmail = MomiSupabaseSchema.normalizeEmail(email)
        val cleanPhone = MomiSupabaseSchema.normalizePhoneE164(phone)

        // Critical: never overwrite an already-customized remote profile with fallback defaults!
        val existing = fetchProfileByUserId(userId)
        if (existing != null) {
            val alignPatch = linkedMapOf<String, Any?>()
            if (cleanEmail.isNotBlank() && existing.email.isBlank()) {
                val ownerByEmail = fetchProfileByEmail(cleanEmail)
                if (ownerByEmail == null || ownerByEmail.userId == userId) {
                    alignPatch["email"] = cleanEmail
                }
            }
            if (cleanPhone.isNotBlank() && existing.phone.isBlank()) {
                val ownerByPhone = fetchProfileByPhone(cleanPhone)
                if (ownerByPhone == null || ownerByPhone.userId == userId) {
                    alignPatch["phone"] = cleanPhone
                }
            }
            if (alignPatch.isNotEmpty()) {
                patchRows("profiles", "user_id=eq.${URLEncoder.encode(userId, "UTF-8")}", alignPatch)
            }
            return Result.success(Unit)
        }
        if (cleanEmail.isNotBlank()) {
            val existingByEmail = fetchProfileByEmail(cleanEmail)
            if (existingByEmail != null) {
                if (existingByEmail.userId != userId) {
                    val migratedTarget = existingByEmail.copy(
                        id = userId,
                        userId = userId,
                        email = cleanEmail,
                        phone = cleanPhone.ifBlank { existingByEmail.phone },
                        avatarUrl = avatarUrl ?: existingByEmail.avatarUrl
                    )
                    mergeLegacyProfileIntoUser(existingByEmail.userId, migratedTarget)
                }
                return Result.success(Unit)
            }
        }
        if (cleanPhone.isNotBlank()) {
            val existingByPhone = fetchProfileByPhone(cleanPhone)
            if (existingByPhone != null) {
                if (existingByPhone.userId != userId) {
                    val migratedTarget = existingByPhone.copy(
                        id = userId,
                        userId = userId,
                        email = cleanEmail.ifBlank { existingByPhone.email },
                        phone = cleanPhone,
                        avatarUrl = avatarUrl ?: existingByPhone.avatarUrl
                    )
                    mergeLegacyProfileIntoUser(existingByPhone.userId, migratedTarget)
                }
                return Result.success(Unit)
            }
        }
        val stub = UserProfile(
            id = userId,
            userId = userId,
            username = username.ifBlank { "momi.${userId.take(6)}" },
            email = cleanEmail,
            phone = cleanPhone,
            displayName = displayName.ifBlank { "Momi Creator" },
            avatarKey = avatarKey.ifBlank { "avatar_aria" },
            avatarUrl = avatarUrl
        )
        return upsertRows(
            "profiles",
            listOf(MomiSupabaseSchema.toProfileRow(stub)),
            onConflict = "user_id",
            ignoreDuplicates = true
        ).map { Unit }
    }

    suspend fun mergeLegacyProfileIntoUser(
        sourceUserId: String,
        targetProfile: UserProfile,
        accessToken: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanSource = sourceUserId.trim()
        val cleanTarget = targetProfile.userId.trim()
        if (!isConfigured() || cleanSource.isBlank() || cleanTarget.isBlank() || cleanSource == cleanTarget) {
            return@withContext Result.success(0)
        }
        val token = resolvedAccessToken(accessToken)
        val encSource = URLEncoder.encode(cleanSource, "UTF-8")
        // 1. Ensure target profile exists first
        upsertRows("profiles", listOf(MomiSupabaseSchema.toProfileRow(targetProfile)), accessToken = token, onConflict = "user_id")
            .onFailure { return@withContext Result.failure(it) }

        var movedPostsCount = 0
        runCatching {
            val sourcePosts = parsePosts(fetchJsonArray("$baseUrl/rest/v1/posts?select=id&user_id=eq.$encSource"))
            movedPostsCount = sourcePosts.size
        }

        // 2. Re-assign authored posts, stories, comments, pages, groups, marketplace & notifications
        patchRows(
            "posts",
            "user_id=eq.$encSource",
            mapOf(
                "user_id" to cleanTarget,
                "author_username" to targetProfile.username.ifBlank { "momi.user" },
                "author_display_name" to targetProfile.displayName.ifBlank { targetProfile.username },
                "author_avatar_key" to targetProfile.avatarKey.ifBlank { "avatar_aria" },
                "author_avatar_url" to targetProfile.avatarUrl,
                "author_verified" to targetProfile.isVerified
            ),
            accessToken = token
        )
        patchRows(
            "stories",
            "user_id=eq.$encSource",
            mapOf(
                "user_id" to cleanTarget,
                "username" to targetProfile.username.ifBlank { "momi.user" },
                "avatar_key" to targetProfile.avatarKey.ifBlank { "avatar_aria" },
                "avatar_url" to targetProfile.avatarUrl
            ),
            accessToken = token
        )
        patchRows(
            "comments",
            "user_id=eq.$encSource",
            mapOf(
                "user_id" to cleanTarget,
                "author_username" to targetProfile.username.ifBlank { "momi.user" },
                "author_avatar_key" to targetProfile.avatarKey.ifBlank { "avatar_aria" }
            ),
            accessToken = token
        )
        runCatching {
            patchRows(
                "pages",
                "owner_uid=eq.$encSource",
                mapOf("owner_uid" to cleanTarget, "owner_username" to targetProfile.username),
                accessToken = token
            )
            patchRows(
                "groups",
                "owner_uid=eq.$encSource",
                mapOf("owner_uid" to cleanTarget, "owner_username" to targetProfile.username),
                accessToken = token
            )
            patchRows(
                "market_products",
                "seller_uid=eq.$encSource",
                mapOf("seller_uid" to cleanTarget, "seller_username" to targetProfile.username),
                accessToken = token
            )
            patchRows(
                "merchants",
                "user_id=eq.$encSource",
                mapOf("user_id" to cleanTarget, "owner_username" to targetProfile.username),
                accessToken = token
            )
            patchRows("notifications", "user_id=eq.$encSource", mapOf("user_id" to cleanTarget), accessToken = token)
        }

        // 3. Migrate followers & following edges respecting chk_follow_id_match + chk_no_self_follow
        runCatching {
            val edges = parseFollows(
                fetchJsonArray(
                    "$baseUrl/rest/v1/follows?select=*&or=(follower_uid.eq.$encSource,target_uid.eq.$encSource)"
                )
            )
            val newEdges = edges.mapNotNull { edge ->
                val newFollower = if (edge.followerUid == cleanSource) cleanTarget else edge.followerUid
                val newTarget = if (edge.targetUid == cleanSource) cleanTarget else edge.targetUid
                if (newFollower.isBlank() || newTarget.isBlank() || newFollower == newTarget) return@mapNotNull null
                val followerUsername = if (edge.followerUid == cleanSource) targetProfile.username else edge.followerUsername
                val targetUsername = if (edge.targetUid == cleanSource) targetProfile.username else edge.targetUsername
                MomiSupabaseSchema.toFollowRow(
                    FollowEdge(
                        id = "${newFollower}_$newTarget",
                        followerUid = newFollower,
                        targetUid = newTarget,
                        followerUsername = followerUsername.ifBlank { "momi.user" },
                        targetUsername = targetUsername.ifBlank { "momi.user" },
                        createdAt = edge.createdAt
                    )
                )
            }
            if (newEdges.isNotEmpty()) {
                upsertRows("follows", newEdges, accessToken = token, onConflict = "id", ignoreDuplicates = true)
            }
        }

        // 4. Remove the legacy duplicate profile row (ON DELETE CASCADE cleans up stale edges/records)
        deleteRows("profiles", "user_id=eq.$encSource", accessToken = token)
        Result.success(movedPostsCount)
    }

    suspend fun syncLiveSnapshot(
        profiles: List<UserProfile>,
        posts: List<Post>,
        stories: List<Story>,
        merchants: List<MerchantAccount>,
        products: List<MarketProduct>,
        monetizationConfig: AdMonetizationConfig,
        accessToken: String? = null
    ): Result<String> {
        val health = checkConnection()
        if (!health.configured) {
            return Result.failure(IllegalStateException(health.message))
        }
        if (!health.connected) {
            return Result.failure(IllegalStateException(health.message))
        }
        val resolvedToken = resolvedAccessToken(accessToken)
        var totalUpserted = 0
        val errors = mutableListOf<String>()

        val seenUids = mutableSetOf<String>()
        val seenUsernames = mutableSetOf<String>()
        val profileMap = linkedMapOf<String, Map<String, Any?>>()

        fun addProfileEntry(profile: UserProfile) {
            val row = MomiSupabaseSchema.toProfileRow(profile)
            val uid = (row["user_id"] as? String).orEmpty()
            var uname = (row["username"] as? String).orEmpty().ifBlank { "momi.${uid.take(6)}" }
            if (uid.isNotBlank() && seenUids.add(uid)) {
                val lowered = uname.lowercase(Locale.US)
                if (!seenUsernames.add(lowered)) {
                    uname = "${uname.take(40)}.${uid.take(6)}".take(50)
                    seenUsernames.add(uname.lowercase(Locale.US))
                    val mutableRow = row.toMutableMap()
                    mutableRow["username"] = uname
                    profileMap[uid] = mutableRow
                } else {
                    profileMap[uid] = row
                }
            }
        }

        profiles.forEach(::addProfileEntry)

        // Ensure no real post/story/merchant/product is ever dropped due to missing author profile FK
        val realPosts = posts.filterNot { it.id.startsWith("showcase_") }
        realPosts.forEach { p ->
            if (p.userId.isNotBlank() && p.userId !in profileMap) {
                addProfileEntry(
                    UserProfile(
                        id = p.userId,
                        userId = p.userId,
                        username = p.authorUsername.ifBlank { "momi.${p.userId.take(6)}" },
                        displayName = p.authorDisplayName.ifBlank { "Momi Creator" },
                        avatarKey = p.authorAvatarKey.ifBlank { "avatar_aria" },
                        avatarUrl = p.authorAvatarUrl,
                        isVerified = p.authorVerified
                    )
                )
            }
        }
        val realStories = stories.filterNot { it.id.startsWith("showcase_") }
        realStories.forEach { s ->
            if (s.userId.isNotBlank() && s.userId !in profileMap) {
                addProfileEntry(
                    UserProfile(
                        id = s.userId,
                        userId = s.userId,
                        username = s.username.ifBlank { "momi.${s.userId.take(6)}" },
                        displayName = s.username.ifBlank { "Momi Creator" },
                        avatarKey = s.avatarKey.ifBlank { "avatar_aria" },
                        avatarUrl = s.avatarUrl
                    )
                )
            }
        }
        merchants.forEach { m ->
            val mUid = m.userId.ifBlank { m.id }
            if (mUid.isNotBlank() && mUid !in profileMap) {
                addProfileEntry(
                    UserProfile(
                        id = mUid,
                        userId = mUid,
                        username = m.ownerUsername.ifBlank { "momi.${mUid.take(6)}" },
                        displayName = m.storeName.ifBlank { "Momi Store" },
                        avatarKey = m.avatarKey.ifBlank { "avatar_aria" },
                        avatarUrl = m.avatarUrl,
                        isVerified = m.isVerified
                    )
                )
            }
        }
        products.forEach { prod ->
            if (prod.sellerUid.isNotBlank() && prod.sellerUid !in profileMap) {
                addProfileEntry(
                    UserProfile(
                        id = prod.sellerUid,
                        userId = prod.sellerUid,
                        username = prod.sellerUsername.ifBlank { "momi.${prod.sellerUid.take(6)}" },
                        displayName = prod.storeName.ifBlank { "Momi Seller" },
                        isVerified = prod.sellerVerified
                    )
                )
            }
        }

        val uniqueProfiles = profileMap.values.toList()
        if (uniqueProfiles.isNotEmpty()) {
            upsertRows("profiles", uniqueProfiles, accessToken = resolvedToken, onConflict = "user_id")
                .onSuccess { totalUpserted += it }
                .onFailure { errors.add("profiles: ${it.localizedMessage}") }
        }
        if (realPosts.isNotEmpty()) {
            upsertRows("posts", realPosts.map(MomiSupabaseSchema::toPostRow), accessToken = resolvedToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { errors.add("posts: ${it.localizedMessage}") }
        }
        if (realStories.isNotEmpty()) {
            upsertRows("stories", realStories.map(MomiSupabaseSchema::toStoryRow), accessToken = resolvedToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { errors.add("stories: ${it.localizedMessage}") }
        }
        if (merchants.isNotEmpty()) {
            upsertRows("merchants", merchants.map(MomiSupabaseSchema::toMerchantRow), accessToken = resolvedToken, onConflict = "user_id")
                .onSuccess { totalUpserted += it }
                .onFailure { errors.add("merchants: ${it.localizedMessage}") }
        }
        if (products.isNotEmpty()) {
            upsertRows("market_products", products.map(MomiSupabaseSchema::toMarketProductRow), accessToken = resolvedToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { errors.add("market_products: ${it.localizedMessage}") }
        }
        upsertRows("app_config", listOf(MomiSupabaseSchema.toAppConfigRow(monetizationConfig)), accessToken = resolvedToken, onConflict = "id")
            .onSuccess { totalUpserted += it }
            .onFailure { errors.add("app_config: ${it.localizedMessage}") }

        if (errors.isNotEmpty()) {
            return Result.failure(IllegalStateException("Supabase write blocked: ${errors.first()}"))
        }

        return Result.success("Synced $totalUpserted records to Supabase (${health.latencyMs} ms)!")
    }

    suspend fun fetchFeedBundle(currentUserId: String = ""): Result<SupabaseFeedBundle> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        runCatching {
            val storyCutoffIso = MomiSupabaseSchema.formatTimestampIso(
                Timestamp(Date(System.currentTimeMillis() - MomiSupabaseSchema.STORY_TTL_MS))
            )
            val encStoryCutoff = URLEncoder.encode(storyCutoffIso, "UTF-8")
            val uid = currentUserId.trim()
            val encUid = if (uid.isNotEmpty()) URLEncoder.encode(uid, "UTF-8") else ""

            val failedTables = mutableSetOf<String>()
            val (
                profilesJson,
                postsJson,
                storiesJson,
                productsJson,
                merchantsJson,
                liveStreamsJson,
                followsJson,
                configJson,
                pagesJson,
                groupsJson,
                showcaseJson,
                publicEngagementsJson,
                interJson,
                notifJson,
                threadsJson,
                ordersJson,
                walletJson,
                txJson,
                reportsJson,
                pageFollowsJson,
                groupMembersJson
            ) = coroutineScope {
                suspend fun fetchTracked(tableName: String, url: String, strict: Boolean = false): JSONArray {
                    val res = fetchJsonArrayWithStatus(url, strict = strict)
                    if (!res.succeeded) failedTables.add(tableName)
                    return res.array
                }

                val profilesDef = async { fetchTracked("profiles", "$baseUrl/rest/v1/profiles?select=*&limit=100", strict = true) }
                val postsDef = async { fetchTracked("posts", "$baseUrl/rest/v1/posts?select=*&order=created_at.desc&limit=120", strict = true) }
                val storiesDef = async { fetchTracked("stories", "$baseUrl/rest/v1/stories?select=*&created_at=gte.$encStoryCutoff&order=created_at.desc&limit=50") }
                val productsDef = async { fetchTracked("market_products", "$baseUrl/rest/v1/market_products?select=*&order=created_at.desc&limit=80") }
                val merchantsDef = async { fetchTracked("merchants", "$baseUrl/rest/v1/merchants?select=*&limit=80") }
                val liveStreamsDef = async { fetchTracked("live_streams", "$baseUrl/rest/v1/live_streams?select=*&is_live=eq.true&order=updated_at.desc&limit=30") }
                val followsDef = async { fetchTracked("follows", "$baseUrl/rest/v1/follows?select=*&limit=200") }
                val configDef = async { fetchTracked("app_config", "$baseUrl/rest/v1/app_config?select=*&id=eq.monetization&limit=1") }
                val pagesDef = async { fetchTracked("pages", "$baseUrl/rest/v1/pages?select=*&order=created_at.desc&limit=60") }
                val groupsDef = async { fetchTracked("groups", "$baseUrl/rest/v1/groups?select=*&order=created_at.desc&limit=60") }
                val showcaseDef = async { fetchTracked("showcase_accounts", "$baseUrl/rest/v1/showcase_accounts?select=*&limit=50") }
                val pubEngDef = async {
                    fetchTracked("public_engagements", "$baseUrl/rest/v1/user_interactions?select=user_id,target_type,target_id&target_type=in.(LIKED_POST,VIEWED_POST)&limit=1000")
                }

                val interDef = if (uid.isNotEmpty()) async { fetchTracked("user_interactions", "$baseUrl/rest/v1/user_interactions?select=*&user_id=eq.$encUid&limit=400") } else null
                val notifDef = if (uid.isNotEmpty()) async { fetchTracked("notifications", "$baseUrl/rest/v1/notifications?select=*&user_id=eq.$encUid&order=created_at.desc&limit=50") } else null
                val threadsDef = if (uid.isNotEmpty()) async { fetchTracked("threads", "$baseUrl/rest/v1/threads?select=*&or=(user_a_id.eq.$encUid,user_b_id.eq.$encUid)&order=updated_at.desc&limit=40") } else null
                val ordersDef = if (uid.isNotEmpty()) async { fetchTracked("market_orders", "$baseUrl/rest/v1/market_orders?select=*&or=(buyer_uid.eq.$encUid,seller_uid.eq.$encUid)&order=created_at.desc&limit=50") } else null
                val walletDef = if (uid.isNotEmpty()) async { fetchTracked("wallets", "$baseUrl/rest/v1/wallets?select=*&user_id=eq.$encUid&limit=1") } else null
                val txDef = if (uid.isNotEmpty()) async { fetchTracked("credit_transactions", "$baseUrl/rest/v1/credit_transactions?select=*&or=(sender_uid.eq.$encUid,recipient_uid.eq.$encUid)&order=created_at.desc&limit=40") } else null
                val reportsDef = if (uid.isNotEmpty()) async { fetchTracked("content_reports", "$baseUrl/rest/v1/content_reports?select=*&order=created_at.desc&limit=60") } else null
                val pageFollowsDef = if (uid.isNotEmpty()) async { fetchTracked("page_follows", "$baseUrl/rest/v1/page_follows?select=page_id&user_id=eq.$encUid&limit=120") } else null
                val groupMembersDef = if (uid.isNotEmpty()) async { fetchTracked("group_members", "$baseUrl/rest/v1/group_members?select=group_id&user_id=eq.$encUid&limit=120") } else null

                FetchBundleArrays(
                    profiles = profilesDef.await(),
                    posts = postsDef.await(),
                    stories = storiesDef.await(),
                    products = productsDef.await(),
                    merchants = merchantsDef.await(),
                    liveStreams = liveStreamsDef.await(),
                    follows = followsDef.await(),
                    config = configDef.await(),
                    pages = pagesDef.await(),
                    groups = groupsDef.await(),
                    showcase = showcaseDef.await(),
                    publicEngagements = pubEngDef.await(),
                    interactions = interDef?.await() ?: JSONArray(),
                    notifications = notifDef?.await() ?: JSONArray(),
                    threads = threadsDef?.await() ?: JSONArray(),
                    orders = ordersDef?.await() ?: JSONArray(),
                    wallet = walletDef?.await() ?: JSONArray(),
                    tx = txDef?.await() ?: JSONArray(),
                    reports = reportsDef?.await() ?: JSONArray(),
                    pageFollows = pageFollowsDef?.await() ?: JSONArray(),
                    groupMembers = groupMembersDef?.await() ?: JSONArray()
                )
            }

            val likersMap = linkedMapOf<String, LinkedHashSet<String>>()
            val viewersMap = linkedMapOf<String, LinkedHashSet<String>>()
            val likedP = mutableSetOf<String>()
            val viewedP = mutableSetOf<String>()
            for (i in 0 until publicEngagementsJson.length()) {
                val o = publicEngagementsJson.optJSONObject(i) ?: continue
                val actorUid = o.optString("user_id")
                val tType = o.optString("target_type")
                val tId = o.optString("target_id")
                if (actorUid.isBlank() || tId.isBlank()) continue
                when (tType) {
                    "LIKED_POST" -> {
                        likersMap.getOrPut(tId) { LinkedHashSet() }.add(actorUid)
                        if (uid.isNotEmpty() && actorUid == uid) likedP.add(tId)
                    }
                    "VIEWED_POST" -> {
                        viewersMap.getOrPut(tId) { LinkedHashSet() }.add(actorUid)
                        if (uid.isNotEmpty() && actorUid == uid) viewedP.add(tId)
                    }
                }
            }

            var likedPosts = likedP.toSet()
            var viewedPosts = viewedP.toSet()
            var bookmarkedPosts = emptySet<String>()
            var seenStories = emptySet<String>()
            var likedComments = emptySet<String>()
            var notifications = emptyList<NotificationItem>()
            var threads = emptyList<MessageThread>()
            var orders = emptyList<MarketOrder>()
            var reports = emptyList<ContentReport>()
            var wallet: UserWallet? = null
            var transactions = emptyList<CreditTransaction>()
            var pageFollowedIds = emptySet<String>()
            var groupJoinedIds = emptySet<String>()

            if (uid.isNotEmpty()) {
                val bookmarkedP = mutableSetOf<String>()
                val seenS = mutableSetOf<String>()
                val likedC = mutableSetOf<String>()
                for (i in 0 until interJson.length()) {
                    val o = interJson.optJSONObject(i) ?: continue
                    val tType = o.optString("target_type")
                    val tId = o.optString("target_id")
                    if (tId.isBlank()) continue
                    when (tType) {
                        "LIKED_POST" -> {
                            likedP.add(tId)
                            likersMap.getOrPut(tId) { LinkedHashSet() }.add(uid)
                        }
                        "VIEWED_POST" -> {
                            viewedP.add(tId)
                            viewersMap.getOrPut(tId) { LinkedHashSet() }.add(uid)
                        }
                        "BOOKMARK" -> bookmarkedP.add(tId)
                        "SEEN_STORY" -> seenS.add(tId)
                        "LIKED_COMMENT" -> likedC.add(tId)
                    }
                }
                likedPosts = likedP
                viewedPosts = viewedP
                bookmarkedPosts = bookmarkedP
                seenStories = seenS
                likedComments = likedC

                notifications = parseNotifications(notifJson)
                threads = parseThreads(threadsJson)
                orders = parseMarketOrders(ordersJson)
                wallet = parseWallet(walletJson)
                transactions = parseCreditTransactions(txJson)
                reports = parseContentReports(reportsJson)
                pageFollowedIds = buildSet {
                    for (i in 0 until pageFollowsJson.length()) {
                        pageFollowsJson.optJSONObject(i)?.optString("page_id")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                groupJoinedIds = buildSet {
                    for (i in 0 until groupMembersJson.length()) {
                        groupMembersJson.optJSONObject(i)?.optString("group_id")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }

            val parsedProfiles = parseProfiles(profilesJson).toMutableList()
            if (uid.isNotEmpty() && parsedProfiles.none { it.userId == uid }) {
                fetchProfileByUserId(uid)?.let { parsedProfiles.add(0, it) }
            }

            SupabaseFeedBundle(
                profiles = parsedProfiles,
                posts = parsePosts(postsJson),
                stories = parseStories(storiesJson),
                products = parseProducts(productsJson),
                merchants = parseMerchants(merchantsJson),
                liveStreams = parseLiveStreams(liveStreamsJson),
                follows = parseFollows(followsJson),
                likedPostIds = likedPosts,
                viewedPostIds = viewedPosts,
                bookmarkedPostIds = bookmarkedPosts,
                seenStoryIds = seenStories,
                likedCommentIds = likedComments,
                postLikersByPostId = likersMap.mapValues { it.value.toList() },
                postViewersByPostId = viewersMap.mapValues { it.value.toList() },
                notifications = notifications,
                threads = threads,
                orders = orders,
                reports = reports,
                wallet = wallet,
                transactions = transactions,
                monetizationConfig = parseAppConfig(configJson),
                pages = parsePages(pagesJson),
                pageFollowedIds = pageFollowedIds,
                groups = parseGroups(groupsJson),
                groupJoinedIds = groupJoinedIds,
                showcaseAccounts = parseShowcaseAccounts(showcaseJson),
                failedTables = failedTables.toSet()
            )
        }
    }

    suspend fun fetchComments(postId: String): Result<List<Comment>> = withContext(Dispatchers.IO) {
        if (!isConfigured() || postId.isBlank()) return@withContext Result.success(emptyList())
        val encId = URLEncoder.encode(postId, "UTF-8")
        runCatching {
            val arr = fetchJsonArray("$baseUrl/rest/v1/comments?select=*&post_id=eq.$encId&order=created_at.asc&limit=100")
            parseComments(arr)
        }
    }

    suspend fun fetchChatMessages(threadId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        if (!isConfigured() || threadId.isBlank()) return@withContext Result.success(emptyList())
        val encId = URLEncoder.encode(threadId, "UTF-8")
        runCatching {
            val arr = fetchJsonArray("$baseUrl/rest/v1/chat_messages?select=*&thread_id=eq.$encId&order=created_at.asc&limit=120")
            parseChatMessages(arr)
        }
    }

    suspend fun fetchLiveRoomState(
        streamId: String
    ): Result<Triple<LiveStream?, LiveFrame?, List<LiveComment>>> = withContext(Dispatchers.IO) {
        if (!isConfigured() || streamId.isBlank()) {
            return@withContext Result.success(Triple(null, null, emptyList()))
        }
        val encId = URLEncoder.encode(streamId, "UTF-8")
        runCatching {
            val streamArr = fetchJsonArray("$baseUrl/rest/v1/live_streams?select=*&id=eq.$encId&limit=1")
            val frameArr = fetchJsonArray("$baseUrl/rest/v1/live_frames?select=*&stream_id=eq.$encId&limit=1")
            val commentsArr = fetchJsonArray("$baseUrl/rest/v1/live_comments?select=*&stream_id=eq.$encId&order=created_at.asc&limit=60")
            Triple(
                parseLiveStreams(streamArr).firstOrNull(),
                parseLiveFrames(frameArr).firstOrNull(),
                parseLiveComments(commentsArr)
            )
        }
    }

    private data class FetchJsonResult(val array: JSONArray, val succeeded: Boolean)

    private suspend fun fetchJsonArrayWithStatus(url: String, strict: Boolean = false): FetchJsonResult {
        var token = ensureFreshAccessToken()

        fun buildGet(bearerToken: String?): Request {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
            MomiSupabaseSchema.buildAuthHeaders(anonKey, bearerToken).forEach { (k, v) ->
                requestBuilder.header(k, v)
            }
            return requestBuilder.build()
        }

        return try {
            var code = 0
            var text = ""
            var ok = false
            httpClient.newCall(buildGet(token)).execute().use { response ->
                code = response.code
                text = response.body?.string().orEmpty()
                ok = response.isSuccessful
            }
            if (!ok && code == 401) {
                token = ensureFreshAccessToken(forceRefresh = true)
                httpClient.newCall(buildGet(token)).execute().use { retry ->
                    code = retry.code
                    text = retry.body?.string().orEmpty()
                    ok = retry.isSuccessful
                }
            }
            if (!ok) {
                if (strict) {
                    error("Supabase read failed HTTP $code: ${text.take(120)}")
                }
                FetchJsonResult(JSONArray(), succeeded = false)
            } else {
                val arr = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()
                FetchJsonResult(arr, succeeded = true)
            }
        } catch (err: Exception) {
            if (strict) throw err
            FetchJsonResult(JSONArray(), succeeded = false)
        }
    }

    private suspend fun fetchJsonArray(url: String, strict: Boolean = false): JSONArray =
        fetchJsonArrayWithStatus(url, strict = strict).array

    private fun parseIsoTimestamp(raw: String?): Timestamp? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (fmt in formats) {
            runCatching {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(raw) ?: return@runCatching null
                return Timestamp(d)
            }
        }
        return null
    }

    private fun JSONObject.optCleanString(key: String): String? {
        val raw = optString(key)
        return raw.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank() && item != "null") add(item)
            }
        }
    }

    internal fun parseProfileRecord(o: JSONObject): UserProfile? {
        val uid = o.optString("user_id")
        if (uid.isBlank()) return null
        return UserProfile(
            id = uid,
            userId = uid,
            username = o.optString("username", "momi.creator"),
            email = o.optString("email"),
            phone = o.optString("phone"),
            displayName = o.optString("display_name", "Momi Creator"),
            category = o.optString("category"),
            bio = o.optString("bio"),
            website = o.optString("website"),
            avatarKey = o.optString("avatar_key", "avatar_aria"),
            avatarUrl = o.optCleanString("avatar_url"),
            avatarData = o.optCleanString("avatar_data"),
            followersCount = o.optLong("followers_count", 0L),
            followingCount = o.optLong("following_count", 0L),
            isVerified = o.optBoolean("is_verified", false),
            role = o.optString("role", "USER"),
            moderationScopes = o.optStringList("moderation_scopes"),
            assignedByEmail = o.optCleanString("assigned_by_email"),
            partnerConsent = o.optBoolean("partner_consent", false),
            createdAt = parseIsoTimestamp(o.optString("created_at"))
        )
    }

    private fun parseProfiles(arr: JSONArray): List<UserProfile> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseProfileRecord(o)?.let(::add)
        }
    }

    internal fun parsePostRecord(o: JSONObject): Post? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return Post(
            id = id,
            userId = o.optString("user_id"),
            authorUsername = o.optString("author_username", "momi.creator"),
            authorDisplayName = o.optString("author_display_name", "Momi Creator"),
            authorAvatarKey = o.optString("author_avatar_key", "avatar_aria"),
            authorVerified = o.optBoolean("author_verified", false),
            location = o.optString("location"),
            imageKey = o.optString("image_key", "img_post_coastal"),
            imageUrl = o.optCleanString("image_url"),
            imageData = o.optCleanString("image_data"),
            extraImageUrls = o.optStringList("extra_image_urls"),
            extraImageDatas = o.optStringList("extra_image_datas"),
            mediaType = o.optString("media_type", "IMAGE"),
            videoUrl = o.optCleanString("video_url"),
            videoData = o.optCleanString("video_data"),
            videoDurationMs = o.optLong("video_duration_ms", 0L),
            filterTag = o.optString("filter_tag", "NONE"),
            aspectRatio = o.optString("aspect_ratio", "1:1"),
            taggedUsernames = o.optStringList("tagged_usernames"),
            hashtags = o.optStringList("hashtags"),
            caption = o.optString("caption"),
            likesCount = o.optLong("likes_count", 0L),
            viewsCount = o.optLong("views_count", 0L),
            commentsCount = o.optLong("comments_count", 0L),
            category = o.optString("category", "For You"),
            isReel = o.optBoolean("is_reel", false),
            audioTitle = o.optString("audio_title"),
            audioUrl = o.optCleanString("audio_url"),
            audioData = o.optCleanString("audio_data"),
            isBoosted = o.optBoolean("is_boosted", false),
            boostTier = o.optString("boost_tier"),
            recommendedLabel = o.optString("recommended_label"),
            pageId = o.optCleanString("page_id"),
            groupId = o.optCleanString("group_id"),
            communityName = o.optString("community_name"),
            createdAt = parseIsoTimestamp(o.optString("created_at"))
        )
    }

    private fun parsePosts(arr: JSONArray): List<Post> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parsePostRecord(o)?.let { add(it) }
        }
    }

    internal fun parseCommentRecord(o: JSONObject): Comment? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return Comment(
            id = id,
            userId = o.optString("user_id"),
            authorUsername = o.optString("author_username", "momi.creator"),
            authorAvatarKey = o.optString("author_avatar_key", "avatar_aria"),
            text = o.optString("text"),
            likesCount = o.optLong("likes_count", 0L),
            createdAt = parseIsoTimestamp(o.optString("created_at"))
        )
    }

    private fun parseComments(arr: JSONArray): List<Comment> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseCommentRecord(o)?.let { add(it) }
        }
    }

    internal fun parseStoryRecord(
        o: JSONObject,
        cutoffMs: Long = System.currentTimeMillis() - MomiSupabaseSchema.STORY_TTL_MS
    ): Story? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        val createdAt = parseIsoTimestamp(o.optString("created_at"))
        if (createdAt != null && createdAt.toDate().time < cutoffMs) return null
        val rawImageKey = o.optString("image_key", "img_post_coastal")
        val bgColorKey = StoryOverlayCodec.decodeBackgroundColorKey(rawImageKey)
        val decodedOverlay = StoryOverlayCodec.decodeCaptionWithOverlay(o.optString("caption"))
        return Story(
            id = id,
            userId = o.optString("user_id"),
            username = o.optString("username", "momi.creator"),
            avatarKey = o.optString("avatar_key", "avatar_aria"),
            imageKey = rawImageKey,
            imageUrl = o.optCleanString("image_url"),
            imageData = o.optCleanString("image_data"),
            mediaType = o.optString("media_type", "IMAGE"),
            videoUrl = o.optCleanString("video_url"),
            videoData = o.optCleanString("video_data"),
            videoDurationMs = o.optLong("video_duration_ms", 0L),
            audioTitle = o.optString("audio_title"),
            audioUrl = o.optCleanString("audio_url"),
            audioData = o.optCleanString("audio_data"),
            caption = decodedOverlay.caption,
            backgroundColorKey = bgColorKey,
            textOverlay = decodedOverlay.textOverlay,
            textOverlayStyle = decodedOverlay.styleCode,
            textOverlayPositionY = decodedOverlay.positionY,
            createdAt = createdAt
        )
    }

    private fun parseStories(arr: JSONArray): List<Story> = buildList {
        val cutoffMs = System.currentTimeMillis() - MomiSupabaseSchema.STORY_TTL_MS
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseStoryRecord(o, cutoffMs)?.let(::add)
        }
    }

    private fun parseFollows(arr: JSONArray): List<FollowEdge> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val follower = o.optString("follower_uid")
            val target = o.optString("target_uid")
            if (follower.isBlank() || target.isBlank()) continue
            add(
                FollowEdge(
                    id = o.optString("id").ifBlank { "${follower}_$target" },
                    followerUid = follower,
                    targetUid = target,
                    followerUsername = o.optString("follower_username", "momi.creator"),
                    targetUsername = o.optString("target_username", "momi.creator"),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseNotifications(arr: JSONArray): List<NotificationItem> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                NotificationItem(
                    id = id,
                    userId = o.optString("user_id"),
                    actorUid = o.optString("actor_uid"),
                    actorUsername = o.optString("actor_username", "momi.creator"),
                    actorAvatarKey = o.optString("actor_avatar_key", "avatar_aria"),
                    message = o.optString("message"),
                    timeLabel = o.optString("time_label", "Just now"),
                    section = o.optString("section", "Today"),
                    previewImageKey = o.optCleanString("preview_image_key"),
                    isFollowAction = o.optBoolean("is_follow_action", false),
                    isFollowingBack = o.optBoolean("is_following_back", false),
                    isUnread = o.optBoolean("is_unread", true),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseThreads(arr: JSONArray): List<MessageThread> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                MessageThread(
                    id = id,
                    participantUids = o.optStringList("participant_uids"),
                    userAId = o.optString("user_a_id"),
                    userAUsername = o.optString("user_a_username"),
                    userADisplayName = o.optString("user_a_display_name"),
                    userAAvatarKey = o.optString("user_a_avatar_key", "avatar_aria"),
                    userBId = o.optString("user_b_id"),
                    userBUsername = o.optString("user_b_username"),
                    userBDisplayName = o.optString("user_b_display_name"),
                    userBAvatarKey = o.optString("user_b_avatar_key", "avatar_aria"),
                    lastMessage = o.optString("last_message"),
                    lastSenderUid = o.optString("last_sender_uid"),
                    unreadUid = o.optString("unread_uid"),
                    timeLabel = o.optString("time_label", "Now"),
                    unreadCount = o.optLong("unread_count", 0L),
                    isOnline = o.optBoolean("is_online", false),
                    noteText = o.optCleanString("note_text"),
                    createdAt = parseIsoTimestamp(o.optString("created_at")),
                    updatedAt = parseIsoTimestamp(o.optString("updated_at"))
                )
            )
        }
    }

    private fun parseChatMessages(arr: JSONArray): List<ChatMessage> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                ChatMessage(
                    id = id,
                    participantUids = o.optStringList("participant_uids"),
                    senderUid = o.optString("sender_uid"),
                    senderUsername = o.optString("sender_username", "momi.user"),
                    text = o.optString("text"),
                    messageType = o.optString("message_type", "TEXT"),
                    mediaUrl = o.optCleanString("media_url"),
                    imageData = o.optCleanString("image_data"),
                    videoData = o.optCleanString("video_data"),
                    videoDurationMs = o.optLong("video_duration_ms", 0L),
                    audioData = o.optCleanString("audio_data"),
                    audioDurationMs = o.optLong("audio_duration_ms", 0L),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseLiveStreams(arr: JSONArray): List<LiveStream> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { o.optString("user_id") }
            if (id.isBlank()) continue
            add(
                LiveStream(
                    id = id,
                    userId = o.optString("user_id").ifBlank { id },
                    username = o.optString("username", "momi.creator"),
                    displayName = o.optString("display_name", "Momi Creator"),
                    avatarKey = o.optString("avatar_key", "avatar_aria"),
                    authorVerified = o.optBoolean("author_verified", false),
                    title = o.optString("title", "Live on Momi"),
                    category = o.optString("category", "Live"),
                    isLive = o.optBoolean("is_live", true),
                    viewerCount = o.optLong("viewer_count", 0L),
                    likesCount = o.optLong("likes_count", 0L),
                    giftsCredits = o.optLong("gifts_credits", 0L),
                    createdAt = parseIsoTimestamp(o.optString("created_at")),
                    updatedAt = parseIsoTimestamp(o.optString("updated_at"))
                )
            )
        }
    }

    private fun parseLiveFrames(arr: JSONArray): List<LiveFrame> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sid = o.optString("stream_id").ifBlank { o.optString("user_id") }
            if (sid.isBlank()) continue
            add(
                LiveFrame(
                    id = sid,
                    userId = o.optString("user_id").ifBlank { sid },
                    seq = o.optLong("seq", 0L),
                    frameData = o.optString("frame_data"),
                    cameraEnabled = o.optBoolean("camera_enabled", true),
                    micMuted = o.optBoolean("mic_muted", false),
                    updatedAt = parseIsoTimestamp(o.optString("updated_at"))
                )
            )
        }
    }

    private fun parseLiveComments(arr: JSONArray): List<LiveComment> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                LiveComment(
                    id = id,
                    userId = o.optString("user_id"),
                    username = o.optString("username", "momi.creator"),
                    avatarKey = o.optString("avatar_key", "avatar_aria"),
                    text = o.optString("text"),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseMerchants(arr: JSONArray): List<MerchantAccount> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uid = o.optString("user_id")
            if (uid.isBlank()) continue
            add(
                MerchantAccount(
                    id = uid,
                    userId = uid,
                    ownerUsername = o.optString("owner_username", "momi.seller"),
                    storeName = o.optString("store_name", "Momi Store"),
                    tagline = o.optString("tagline"),
                    category = o.optString("category", "Collectibles"),
                    location = o.optString("location"),
                    avatarKey = o.optString("avatar_key", "avatar_aria"),
                    avatarUrl = o.optCleanString("avatar_url"),
                    avatarData = o.optCleanString("avatar_data"),
                    isVerified = o.optBoolean("is_verified", false),
                    verifiedByEmail = o.optCleanString("verified_by_email"),
                    totalSales = o.optLong("total_sales", 0L),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseProducts(arr: JSONArray): List<MarketProduct> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                MarketProduct(
                    id = id,
                    sellerUid = o.optString("seller_uid"),
                    sellerUsername = o.optString("seller_username", "momi.seller"),
                    storeName = o.optString("store_name", "Momi Store"),
                    title = o.optString("title", "Momi Item"),
                    description = o.optString("description"),
                    category = o.optString("category", "Collectibles"),
                    priceUsd = o.optDouble("price_usd", 0.0),
                    originalPriceUsd = o.optDouble("original_price_usd", 0.0),
                    discountPercent = o.optLong("discount_percent", 0L),
                    freeShipping = o.optBoolean("free_shipping", true),
                    imageKey = o.optString("image_key", "img_post_coastal"),
                    imageUrl = o.optCleanString("image_url"),
                    imageData = o.optCleanString("image_data"),
                    extraImageUrls = o.optStringList("extra_image_urls"),
                    extraImageDatas = o.optStringList("extra_image_datas"),
                    videoUrl = o.optCleanString("video_url"),
                    videoData = o.optCleanString("video_data"),
                    videoDurationMs = o.optLong("video_duration_ms", 0L),
                    stockCount = o.optLong("stock_count", 0L),
                    isFeatured = o.optBoolean("is_featured", false),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseMarketOrders(arr: JSONArray): List<MarketOrder> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                MarketOrder(
                    id = id,
                    buyerUid = o.optString("buyer_uid"),
                    buyerUsername = o.optString("buyer_username"),
                    sellerUid = o.optString("seller_uid"),
                    sellerStoreName = o.optString("seller_store_name"),
                    productId = o.optString("product_id"),
                    productTitle = o.optString("product_title"),
                    productImageKey = o.optString("product_image_key", "img_post_coastal"),
                    productImageData = o.optCleanString("product_image_data"),
                    priceUsd = o.optDouble("price_usd", 0.0),
                    shippingAddress = o.optString("shipping_address"),
                    status = o.optString("status", "PLACED"),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseContentReports(arr: JSONArray): List<ContentReport> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                ContentReport(
                    id = id,
                    targetType = o.optString("target_type", "POST"),
                    targetId = o.optString("target_id"),
                    targetAuthorUid = o.optString("target_author_uid"),
                    targetAuthorUsername = o.optString("target_author_username"),
                    targetPreviewText = o.optString("target_preview_text"),
                    reporterUid = o.optString("reporter_uid"),
                    reporterUsername = o.optString("reporter_username"),
                    category = o.optString("category", "HARASSMENT_SPAM"),
                    reasonDetail = o.optString("reason_detail"),
                    status = o.optString("status", "OPEN"),
                    reviewedByUid = o.optCleanString("reviewed_by_uid"),
                    reviewedByUsername = o.optCleanString("reviewed_by_username"),
                    reviewedByRole = o.optCleanString("reviewed_by_role"),
                    resolutionNote = o.optCleanString("resolution_note"),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseWallet(arr: JSONArray): UserWallet? {
        val o = arr.optJSONObject(0) ?: return null
        val uid = o.optString("user_id")
        if (uid.isBlank()) return null
        return UserWallet(
            id = uid,
            userId = uid,
            balance = o.optLong("balance", 0L),
            totalEarned = o.optLong("total_earned", 0L),
            totalSpent = o.optLong("total_spent", 0L),
            totalPurchased = o.optLong("total_purchased", 0L),
            accountTier = o.optString("account_tier", "STANDARD"),
            verificationTier = o.optString("verification_tier", "NONE"),
            unlockedPerks = o.optStringList("unlocked_perks"),
            lastTxId = o.optCleanString("last_tx_id"),
            createdAt = parseIsoTimestamp(o.optString("created_at"))
        )
    }

    private fun parseCreditTransactions(arr: JSONArray): List<CreditTransaction> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val txId = o.optString("tx_id")
            if (txId.isBlank()) continue
            add(
                CreditTransaction(
                    id = txId,
                    txId = txId,
                    type = o.optString("type", CreditTransaction.TYPE_TOP_UP),
                    senderUid = o.optString("sender_uid"),
                    senderUsername = o.optString("sender_username"),
                    recipientUid = o.optString("recipient_uid"),
                    recipientUsername = o.optString("recipient_username"),
                    creditsAmount = o.optLong("credits_amount", 0L),
                    priceUsd = o.optDouble("price_usd", 0.0),
                    paymentMethod = o.optString("payment_method", CreditTransaction.METHOD_GOOGLE_PLAY),
                    paymentRef = o.optCleanString("payment_ref"),
                    giftKey = o.optCleanString("gift_key"),
                    giftName = o.optCleanString("gift_name"),
                    streamId = o.optCleanString("stream_id"),
                    perkKey = o.optCleanString("perk_key"),
                    postId = o.optCleanString("post_id"),
                    recipientClaimed = o.optBoolean("recipient_claimed", true),
                    status = o.optString("status", "COMPLETED"),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseAppConfig(arr: JSONArray): AdMonetizationConfig? {
        val o = arr.optJSONObject(0) ?: return null
        return AdMonetizationConfig(
            admobEnabled = o.optBoolean("admob_enabled", true),
            admobBannerUnitId = o.optString("admob_banner_unit_id"),
            admobMrecUnitId = o.optString("admob_mrec_unit_id"),
            admobInterstitialUnitId = o.optString("admob_interstitial_unit_id"),
            startIoEnabled = o.optBoolean("start_io_enabled", true),
            partnerPromoTitle = o.optString("partner_promo_title"),
            partnerPromoSubtitle = o.optString("partner_promo_subtitle"),
            partnerPromoCta = o.optString("partner_promo_cta"),
            announcementId = o.optString("announcement_id"),
            announcementTitle = o.optString("announcement_title"),
            announcementBody = o.optString("announcement_body"),
            latestVersionName = o.optString("latest_version_name").ifBlank { "2.3" },
            releaseNotes = o.optString("release_notes"),
            updatedByEmail = o.optString("updated_by_email")
        )
    }

    private fun parsePages(arr: JSONArray): List<MomiPage> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                MomiPage(
                    id = id,
                    ownerUid = o.optString("owner_uid"),
                    ownerUsername = o.optString("owner_username", "momi.creator"),
                    name = o.optString("name", "Momi Page"),
                    handle = o.optString("handle"),
                    category = o.optString("category", "Creator"),
                    bio = o.optString("bio"),
                    coverKey = o.optString("cover_key", "img_post_coastal"),
                    avatarUrl = o.optCleanString("avatar_url"),
                    isVerified = o.optBoolean("is_verified", false),
                    followersCount = o.optLong("followers_count", 0L),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseGroups(arr: JSONArray): List<MomiGroup> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            add(
                MomiGroup(
                    id = id,
                    ownerUid = o.optString("owner_uid"),
                    ownerUsername = o.optString("owner_username", "momi.creator"),
                    name = o.optString("name", "Momi Group"),
                    category = o.optString("category", "Community"),
                    description = o.optString("description"),
                    privacy = o.optString("privacy", "PUBLIC"),
                    coverKey = o.optString("cover_key", "img_post_architecture"),
                    membersCount = o.optLong("members_count", 1L),
                    createdAt = parseIsoTimestamp(o.optString("created_at"))
                )
            )
        }
    }

    private fun parseShowcaseAccounts(arr: JSONArray): List<ShowcaseAccount> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val username = o.optString("username")
            if (username.isBlank()) continue
            add(
                ShowcaseAccount(
                    username = username,
                    displayName = o.optString("display_name", username),
                    avatarKey = o.optString("avatar_key", "avatar_elena"),
                    avatarUrl = o.optCleanString("avatar_url"),
                    bio = o.optString("bio"),
                    category = o.optString("category", "Creator"),
                    location = o.optString("location"),
                    isVerified = o.optBoolean("is_verified", true),
                    isAuthorized = o.optBoolean("is_authorized", false),
                    updatedByEmail = o.optString("updated_by_email")
                )
            )
        }
    }

    suspend fun fetchAllProfilesRaw(): List<UserProfile> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        runCatching {
            val arr = fetchJsonArray("$baseUrl/rest/v1/profiles?select=*&limit=250", strict = true)
            parseProfiles(arr)
        }.getOrDefault(emptyList())
    }

    private data class FetchBundleArrays(
        val profiles: JSONArray,
        val posts: JSONArray,
        val stories: JSONArray,
        val products: JSONArray,
        val merchants: JSONArray,
        val liveStreams: JSONArray,
        val follows: JSONArray,
        val config: JSONArray,
        val pages: JSONArray,
        val groups: JSONArray,
        val showcase: JSONArray,
        val publicEngagements: JSONArray,
        val interactions: JSONArray,
        val notifications: JSONArray,
        val threads: JSONArray,
        val orders: JSONArray,
        val wallet: JSONArray,
        val tx: JSONArray,
        val reports: JSONArray,
        val pageFollows: JSONArray,
        val groupMembers: JSONArray
    )

    companion object {
        val KEEP_WHITELIST_EMAILS = setOf(
            "syali6511@gmail.com",
            "hassan.ali62313@gmail.com"
        )

        fun isWhitelistedAccountEmail(email: String?): Boolean {
            val clean = email?.trim()?.lowercase().orEmpty()
            return clean.isNotEmpty() && clean in KEEP_WHITELIST_EMAILS
        }

        private val refreshMutex = Mutex()
        private val _sessionFlow = MutableStateFlow<SupabaseAuthSession?>(null)
        val sessionFlow: StateFlow<SupabaseAuthSession?> = _sessionFlow.asStateFlow()

        @Volatile
        var sessionPersister: ((SupabaseAuthSession?) -> Unit)? = null

        var activeSession: SupabaseAuthSession?
            get() = _sessionFlow.value
            set(value) {
                _sessionFlow.value = value
            }

        internal fun setActiveSessionAndPersist(session: SupabaseAuthSession?) {
            activeSession = session
            sessionPersister?.invoke(session)
        }

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TUS_OFFSET_MEDIA_TYPE = "application/offset+octet-stream".toMediaType()
        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .build()
        }
        private val uploadHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(15, TimeUnit.MINUTES)
                .callTimeout(25, TimeUnit.MINUTES)
                .build()
        }
    }
}
