package com.example.data.supabase

import com.example.BuildConfig
import com.example.data.model.AdMonetizationConfig
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
    val email: String?
)

object MomiSupabaseSchema {
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
        "app_config"
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
            "display_name" to profile.displayName.ifBlank { "Momi Creator" }.take(80),
            "category" to profile.category.take(80),
            "bio" to profile.bio.take(300),
            "website" to profile.website.take(200),
            "avatar_key" to profile.avatarKey.ifBlank { "avatar_aria" }.take(64),
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
        "image_data" to post.imageData,
        "extra_image_datas" to post.extraImageDatas,
        "media_type" to post.mediaType.ifBlank { "IMAGE" },
        "video_data" to post.videoData,
        "video_duration_ms" to post.videoDurationMs.coerceAtLeast(0L),
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
        "audio_data" to post.audioData,
        "is_boosted" to post.isBoosted,
        "boost_tier" to post.boostTier,
        "recommended_label" to post.recommendedLabel.take(40),
        "boosted_by_uid" to post.boostedByUid,
        "boosted_at" to post.boostedAt?.let { formatTimestampIso(it) },
        "created_at" to formatTimestampIso(post.createdAt)
    )

    fun toStoryRow(story: Story): Map<String, Any?> = linkedMapOf(
        "id" to story.id,
        "user_id" to story.userId,
        "username" to story.username.ifBlank { "momi.creator" }.take(50),
        "avatar_key" to story.avatarKey.ifBlank { "avatar_aria" }.take(64),
        "image_key" to story.imageKey.ifBlank { "img_post_coastal" }.take(64),
        "image_data" to story.imageData,
        "media_type" to story.mediaType.ifBlank { "IMAGE" },
        "video_data" to story.videoData,
        "video_duration_ms" to story.videoDurationMs.coerceAtLeast(0L),
        "audio_title" to story.audioTitle.take(120),
        "audio_data" to story.audioData,
        "caption" to story.caption.take(300),
        "created_at" to formatTimestampIso(story.createdAt)
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
        "image_data" to product.imageData,
        "extra_image_datas" to product.extraImageDatas,
        "video_data" to product.videoData,
        "video_duration_ms" to product.videoDurationMs.coerceAtLeast(0L),
        "stock_count" to product.stockCount.coerceIn(0L, 99999L),
        "is_featured" to product.isFeatured,
        "created_at" to formatTimestampIso(product.createdAt)
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

    fun toAppConfigRow(config: AdMonetizationConfig): Map<String, Any?> = linkedMapOf(
        "id" to "monetization",
        "admob_enabled" to config.admobEnabled,
        "admob_banner_unit_id" to config.admobBannerUnitId.take(120),
        "admob_mrec_unit_id" to config.admobMrecUnitId.take(120),
        "admob_interstitial_unit_id" to config.admobInterstitialUnitId.take(120),
        "start_io_enabled" to config.startIoEnabled,
        "monetag_enabled" to config.monetagEnabled,
        "monetag_direct_link_url" to config.monetagDirectLinkUrl.take(300),
        "monetag_web_zone_id" to config.monetagWebZoneId.take(40),
        "monetag_web_meta_tag" to config.monetagWebMetaTag.take(120),
        "partner_promo_title" to config.partnerPromoTitle.take(80),
        "partner_promo_subtitle" to config.partnerPromoSubtitle.take(180),
        "partner_promo_cta" to config.partnerPromoCta.take(40),
        "gamezop_enabled" to config.gamezopEnabled,
        "gamezop_portal_url" to config.gamezopPortalUrl.take(300),
        "gamezop_publisher_id" to config.gamezopPublisherId.take(40),
        "gamezop_banner_title" to config.gamezopBannerTitle.take(80),
        "updated_by_email" to config.updatedByEmail.take(120)
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

    suspend fun checkConnection(): SupabaseHealthResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext SupabaseHealthResult(
                configured = false,
                connected = false,
                message = "Waiting for SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets."
            )
        }
        val startMs = System.currentTimeMillis()
        val requestBuilder = Request.Builder()
            .url("$baseUrl/rest/v1/app_config?select=id&limit=1")
            .header("Accept", "application/json")
            .get()
        MomiSupabaseSchema.buildAuthHeaders(anonKey).forEach { (k, v) ->
            requestBuilder.header(k, v)
        }
        val request = requestBuilder.build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val code = response.code
                if (response.isSuccessful) {
                    SupabaseHealthResult(
                        configured = true,
                        connected = true,
                        statusCode = code,
                        latencyMs = latency,
                        message = "Connected to Supabase ($latency ms) — schema & RLS active."
                    )
                } else {
                    val hint = when (code) {
                        404 -> "Connected to project, but run supabase/schema.sql in SQL Editor to create app_config."
                        401, 403 -> "Supabase rejected key/permissions — verify anon key & RLS policies."
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
            }
        }.getOrElse { error ->
            SupabaseHealthResult(
                configured = true,
                connected = false,
                message = "Network error reaching Supabase: ${error.localizedMessage ?: "unknown error"}"
            )
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<SupabaseAuthSession> =
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
                        error("Supabase Google sign-in failed (HTTP ${response.code})")
                    }
                    val json = JSONObject(rawBody)
                    val access = json.optString("access_token")
                    val refresh = json.optString("refresh_token")
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id").orEmpty()
                    val email = userObj?.optString("email")?.takeIf { it.isNotBlank() }
                    require(access.isNotBlank() && userId.isNotBlank()) {
                        "Invalid Supabase auth session response"
                    }
                    SupabaseAuthSession(
                        accessToken = access,
                        refreshToken = refresh,
                        userId = userId,
                        email = email
                    )
                }
            }
        }

    suspend fun upsertRows(
        table: String,
        rows: List<Map<String, Any?>>,
        accessToken: String? = null,
        onConflict: String? = null
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

        val jsonBody = MomiSupabaseSchema.toJsonString(rows)
        val requestBuilder = Request.Builder()
            .url("$baseUrl/rest/v1/$table$conflictQuery")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        MomiSupabaseSchema.buildAuthHeaders(anonKey, accessToken).forEach { (k, v) ->
            requestBuilder.header(k, v)
        }
        val request = requestBuilder.build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Upsert into $table failed (HTTP ${response.code})")
                }
                rows.size
            }
        }
    }

    suspend fun uploadStorageBytes(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String,
        accessToken: String
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

        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Sign in via Supabase auth before uploading media."))
        }

        val mediaType = contentType.toMediaType()
        val requestBuilder = Request.Builder()
            .url("$baseUrl/storage/v1/object/$bucket/$safePath")
            .header("x-upsert", "true")
            .post(bytes.toRequestBody(mediaType))
        MomiSupabaseSchema.buildAuthHeaders(anonKey, accessToken).forEach { (k, v) ->
            requestBuilder.header(k, v)
        }
        val request = requestBuilder.build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Storage upload failed (HTTP ${response.code})")
                }
                "$baseUrl/storage/v1/object/public/$bucket/$safePath"
            }
        }
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
        var totalUpserted = 0
        var writeErrors = 0
        if (profiles.isNotEmpty()) {
            upsertRows("profiles", profiles.map(MomiSupabaseSchema::toProfileRow), accessToken = accessToken, onConflict = "user_id")
                .onSuccess { totalUpserted += it }
                .onFailure { writeErrors++ }
        }
        val realPosts = posts.filterNot { it.id.startsWith("showcase_") }
        if (realPosts.isNotEmpty()) {
            upsertRows("posts", realPosts.map(MomiSupabaseSchema::toPostRow), accessToken = accessToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { writeErrors++ }
        }
        val realStories = stories.filterNot { it.id.startsWith("showcase_") }
        if (realStories.isNotEmpty()) {
            upsertRows("stories", realStories.map(MomiSupabaseSchema::toStoryRow), accessToken = accessToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { writeErrors++ }
        }
        if (merchants.isNotEmpty()) {
            upsertRows("merchants", merchants.map(MomiSupabaseSchema::toMerchantRow), accessToken = accessToken, onConflict = "user_id")
                .onSuccess { totalUpserted += it }
                .onFailure { writeErrors++ }
        }
        if (products.isNotEmpty()) {
            upsertRows("market_products", products.map(MomiSupabaseSchema::toMarketProductRow), accessToken = accessToken, onConflict = "id")
                .onSuccess { totalUpserted += it }
                .onFailure { writeErrors++ }
        }
        upsertRows("app_config", listOf(MomiSupabaseSchema::toAppConfigRow.invoke(monetizationConfig)), accessToken = accessToken, onConflict = "id")
            .onSuccess { totalUpserted += it }
            .onFailure { writeErrors++ }

        if (totalUpserted > 0) {
            return Result.success("Synced $totalUpserted records to Supabase (${health.latencyMs} ms)!")
        }
        val realPostsCount = posts.count { !it.id.startsWith("showcase_") }
        val realStoriesCount = stories.count { !it.id.startsWith("showcase_") }
        if (writeErrors > 0) {
            return Result.success(
                "Connected (${health.latencyMs} ms)! Snapshot ready: ${profiles.size} profiles, $realPostsCount posts, $realStoriesCount stories. Run sync write policy in Supabase SQL Editor to allow push."
            )
        }
        return Result.success("Supabase already in sync (${health.latencyMs} ms).")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
