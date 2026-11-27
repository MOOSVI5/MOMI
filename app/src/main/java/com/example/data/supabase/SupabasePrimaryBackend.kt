package com.example.data.supabase

import android.content.Context
import com.example.data.model.AccountPerkItem
import com.example.data.model.AdMonetizationConfig
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.ContentReport
import com.example.data.model.CreditPackageOption
import com.example.data.model.CreditTransaction
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveFrame
import com.example.data.model.LiveGiftItem
import com.example.data.model.LiveStream
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.MessageThread
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.MomiGroup
import com.example.data.model.MomiPage
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PostBoostOption
import com.example.data.model.ShowcaseAccount
import com.example.data.model.ShowcaseCatalog
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.util.MediaAssets
import com.example.util.StudioMusicCatalog
import com.example.data.model.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.UUID

data class UploadedMediaUrls(
    val imageUrl: String? = null,
    val imageDataFallback: String? = null,
    val extraImageUrls: List<String> = emptyList(),
    val extraImageDatasFallback: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoDataFallback: String? = null,
    val videoChunksFallback: List<String> = emptyList(),
    val audioUrl: String? = null,
    val audioDataFallback: String? = null,
    val normalizedDurationMs: Long? = null,
    val failureReason: String? = null
)

class SupabasePrimaryBackend(
    val client: MomiSupabaseClient
) {
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun uploadImageToStorage(
        bucket: String = "momi-media",
        objectPath: String,
        localImagePath: String?
    ): String? {
        if (!client.isConfigured() || localImagePath.isNullOrBlank()) return null
        val bytes = MediaAssets.readCompressedImageBytes(localImagePath) ?: return null
        return client.uploadStorageBytes(
            bucket = bucket,
            objectPath = objectPath,
            bytes = bytes,
            contentType = "image/jpeg"
        ).getOrNull()
    }

    suspend fun prepareAndUploadPostMedia(
        context: Context,
        mediaId: String,
        localImagePath: String?,
        localExtraImagePaths: List<String>,
        mediaType: String,
        localVideoPath: String?,
        localAudioPath: String?,
        ownerUid: String = MomiSupabaseClient.activeSession?.userId.orEmpty(),
        onProgressStatus: ((String) -> Unit)? = null
    ): UploadedMediaUrls {
        val uidPrefix = ownerUid.trim().takeIf { it.isNotEmpty() }?.let { "$it/" } ?: ""
        val coverUrl = uploadImageToStorage("momi-media", "${uidPrefix}posts/${mediaId}_cover.jpg", localImagePath)
        val coverFallback = if (coverUrl == null && !localImagePath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                MediaAssets.encodePrimaryPostImage(localImagePath, hasExtraSlides = localExtraImagePaths.isNotEmpty())
            }
        } else {
            null
        }

        val extraUrls = mutableListOf<String>()
        val extraDatas = mutableListOf<String>()
        if (mediaType == "IMAGE") {
            localExtraImagePaths.forEachIndexed { idx, path ->
                val url = uploadImageToStorage("momi-media", "${uidPrefix}posts/${mediaId}_slide_$idx.jpg", path)
                if (url != null) {
                    extraUrls.add(url)
                } else {
                    withContext(Dispatchers.IO) { MediaAssets.encodeCarouselImageToBase64(path) }
                        ?.let { extraDatas.add(it) }
                }
            }
        }

        var audioUrl: String? = null
        var audioFallback: String? = null
        if (!localAudioPath.isNullOrBlank()) {
            val rawAudio = StudioMusicCatalog.readAudioUploadBytes(localAudioPath)
            if (rawAudio != null) {
                audioUrl = client.uploadStorageBytes(
                    bucket = "momi-media",
                    objectPath = "${uidPrefix}audio/${mediaId}_audio.m4a",
                    bytes = rawAudio,
                    contentType = "audio/mp4"
                ).getOrNull()
            }
            if (audioUrl == null) {
                audioFallback = StudioMusicCatalog.encodeAudioForFirestore(context.applicationContext, localAudioPath)
            }
        }

        var videoUrl: String? = null
        var videoFallback: String? = null
        var videoChunksFallback: List<String> = emptyList()
        var normalizedDurationMs: Long? = null
        if (mediaType == "VIDEO" && !localVideoPath.isNullOrBlank()) {
            onProgressStatus?.invoke("Normalizing video...")
            val normalized = MediaAssets.normalizeVideoForUpload(
                context = context.applicationContext,
                localVideoPath = localVideoPath
            ) { progress ->
                val pct = (progress * 100f).toInt().coerceIn(0, 100)
                onProgressStatus?.invoke(
                    if (pct < 100) "Normalizing video $pct%..." else "Uploading video..."
                )
            }
            normalizedDurationMs = normalized.durationMs.takeIf { it > 0L }
            try {
                val videoFile = File(normalized.uploadFilePath)
                var storageErr: String? = null
                val videoSizeBytes = if (videoFile.exists()) videoFile.length() else 0L
                val ext = if (normalized.wasTranscoded) {
                    "mp4"
                } else {
                    videoFile.extension.trim().lowercase().takeIf { it in setOf("mp4", "mov", "webm", "mkv", "3gp") } ?: "mp4"
                }
                val contentType = when (ext) {
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    else -> "video/mp4"
                }
                if (videoSizeBytes > 0L) {
                    onProgressStatus?.invoke("Uploading video...")
                    val uploadRes = client.uploadStorageFile(
                        bucket = "momi-videos",
                        objectPath = "${uidPrefix}videos/$mediaId.$ext",
                        file = videoFile,
                        contentType = contentType
                    )
                    videoUrl = uploadRes.getOrNull()
                    storageErr = uploadRes.exceptionOrNull()?.message
                }
                if (videoUrl == null) {
                    val isStorageAuthOrSizeError = storageErr?.let { msg ->
                        msg.contains("HTTP 401") || msg.contains("HTTP 403") || msg.contains("HTTP 413")
                    } == true
                    if (videoSizeBytes > 25_000_000L || isStorageAuthOrSizeError) {
                        return UploadedMediaUrls(
                            failureReason = storageErr?.takeIf { it.isNotBlank() }
                                ?: "Could not upload long video to Supabase Storage. Please check your connection and Storage policies."
                        )
                    }
                    onProgressStatus?.invoke("Preparing clip...")
                    val payload = MediaAssets.prepareVideoPayloadForFirestore(context.applicationContext, normalized.uploadFilePath)
                    if (!payload.isReady) {
                        return UploadedMediaUrls(
                            failureReason = storageErr?.takeIf { it.isNotBlank() }
                                ?: payload.failureReason
                                ?: "Could not upload or prepare video clip."
                        )
                    }
                    videoFallback = payload.inlineVideoData
                    videoChunksFallback = payload.videoChunks
                }
            } finally {
                if (normalized.isTemporaryTranscodeFile) {
                    runCatching { File(normalized.uploadFilePath).delete() }
                }
            }
        }

        return UploadedMediaUrls(
            imageUrl = coverUrl,
            imageDataFallback = coverFallback,
            extraImageUrls = extraUrls,
            extraImageDatasFallback = extraDatas,
            videoUrl = videoUrl,
            videoDataFallback = videoFallback,
            videoChunksFallback = videoChunksFallback,
            audioUrl = audioUrl,
            audioDataFallback = audioFallback,
            normalizedDurationMs = normalizedDurationMs
        )
    }

    suspend fun upsertProfile(profile: UserProfile): Result<Unit> {
        return client.upsertRows(
            "profiles",
            listOf(MomiSupabaseSchema.toProfileRow(profile)),
            onConflict = "user_id"
        ).map { Unit }
    }

    suspend fun createPost(profile: UserProfile, post: Post): Result<Unit> {
        client.ensureProfileExists(profile.userId, profile.username, profile.displayName, profile.avatarKey, profile.avatarUrl, profile.email, profile.phone)
        return client.upsertRows("posts", listOf(MomiSupabaseSchema.toPostRow(post)), onConflict = "id").map { Unit }
    }

    suspend fun deletePost(postId: String): Result<Unit> =
        client.deleteRows("posts", "id=eq.${urlEncode(postId)}")

    suspend fun updatePostCaption(postId: String, caption: String, hashtags: List<String>, taggedUsernames: List<String>): Result<Unit> =
        client.patchRows(
            "posts",
            "id=eq.${urlEncode(postId)}",
            linkedMapOf(
                "caption" to caption.take(1000),
                "hashtags" to hashtags,
                "tagged_usernames" to taggedUsernames
            )
        )

    suspend fun createStory(profile: UserProfile, story: Story): Result<Unit> {
        client.ensureProfileExists(profile.userId, profile.username, profile.displayName, profile.avatarKey, profile.avatarUrl, profile.email, profile.phone)
        return client.upsertRows("stories", listOf(MomiSupabaseSchema.toStoryRow(story)), onConflict = "id").map { Unit }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> =
        client.deleteRows("stories", "id=eq.${urlEncode(storyId)}")

    suspend fun setInteraction(
        userId: String,
        targetType: String,
        targetId: String,
        active: Boolean
    ): Result<Unit> {
        return if (active) {
            client.upsertRows(
                "user_interactions",
                listOf(
                    linkedMapOf(
                        "user_id" to userId,
                        "target_type" to targetType,
                        "target_id" to targetId
                    )
                ),
                onConflict = "user_id,target_type,target_id"
            ).map { Unit }
        } else {
            client.deleteRows(
                "user_interactions",
                "user_id=eq.${urlEncode(userId)}&target_type=eq.${urlEncode(targetType)}&target_id=eq.${urlEncode(targetId)}"
            )
        }
    }

    suspend fun updatePostCounts(postId: String, likesCount: Long? = null, commentsCount: Long? = null, viewsCount: Long? = null): Result<Unit> {
        val fields = linkedMapOf<String, Any?>()
        likesCount?.let { fields["likes_count"] = it.coerceAtLeast(0L) }
        commentsCount?.let { fields["comments_count"] = it.coerceAtLeast(0L) }
        viewsCount?.let { fields["views_count"] = it.coerceAtLeast(0L) }
        if (fields.isEmpty()) return Result.success(Unit)
        return client.patchRows("posts", "id=eq.${urlEncode(postId)}", fields)
    }

    suspend fun addComment(postId: String, actorProfile: UserProfile, comment: Comment, newCommentsCount: Long): Result<Unit> {
        client.ensureProfileExists(actorProfile.userId, actorProfile.username, actorProfile.displayName, actorProfile.avatarKey, actorProfile.avatarUrl)
        return client.upsertRows("comments", listOf(MomiSupabaseSchema.toCommentRow(postId, comment)), onConflict = "id")
            .mapCatching {
                updatePostCounts(postId, commentsCount = newCommentsCount)
            }
    }

    suspend fun setCommentLikes(commentId: String, likesCount: Long): Result<Unit> =
        client.patchRows("comments", "id=eq.${urlEncode(commentId)}", mapOf("likes_count" to likesCount.coerceAtLeast(0L)))

    suspend fun toggleFollow(
        follower: UserProfile,
        targetUid: String,
        targetUsername: String,
        currentlyFollowing: Boolean
    ): Result<Unit> {
        client.ensureProfileExists(follower.userId, follower.username, follower.displayName, follower.avatarKey, follower.avatarUrl)
        client.ensureProfileExists(targetUid, targetUsername)
        val edgeId = "${follower.userId}_$targetUid"
        return if (currentlyFollowing) {
            client.deleteRows("follows", "id=eq.${urlEncode(edgeId)}")
        } else {
            val edge = FollowEdge(
                id = edgeId,
                followerUid = follower.userId,
                targetUid = targetUid,
                followerUsername = follower.username,
                targetUsername = targetUsername,
                createdAt = Timestamp.now()
            )
            client.upsertRows("follows", listOf(MomiSupabaseSchema.toFollowRow(edge)), onConflict = "id").map { Unit }
        }
    }

    suspend fun sendNotification(item: NotificationItem): Result<Unit> {
        if (item.userId.isBlank() || item.userId == item.actorUid) return Result.success(Unit)
        client.ensureProfileExists(item.userId, "user.${item.userId.take(6)}")
        return client.upsertRows("notifications", listOf(MomiSupabaseSchema.toNotificationRow(item)), onConflict = "id").map { Unit }
    }

    suspend fun markNotificationsRead(userId: String): Result<Unit> =
        client.patchRows("notifications", "user_id=eq.${urlEncode(userId)}&is_unread=eq.true", mapOf("is_unread" to false))

    suspend fun upsertThread(thread: MessageThread): Result<Unit> =
        client.upsertRows("threads", listOf(MomiSupabaseSchema.toThreadRow(thread)), onConflict = "id").map { Unit }

    suspend fun sendChatMessage(thread: MessageThread, message: ChatMessage): Result<Unit> {
        upsertThread(thread)
        return client.upsertRows("chat_messages", listOf(MomiSupabaseSchema.toChatMessageRow(thread.id, message)), onConflict = "id").map { Unit }
    }

    suspend fun startLiveStream(stream: LiveStream, initialFrame: LiveFrame): Result<Unit> {
        client.ensureProfileExists(stream.userId, stream.username, stream.displayName, stream.avatarKey, stream.avatarUrl)
        val sRes = client.upsertRows("live_streams", listOf(MomiSupabaseSchema.toLiveStreamRow(stream)), onConflict = "id")
        if (sRes.isFailure) return sRes.map { Unit }
        return client.upsertRows("live_frames", listOf(MomiSupabaseSchema.toLiveFrameRow(initialFrame)), onConflict = "stream_id").map { Unit }
    }

    suspend fun publishLiveFrame(frame: LiveFrame): Result<Unit> =
        client.upsertRows("live_frames", listOf(MomiSupabaseSchema.toLiveFrameRow(frame)), onConflict = "stream_id").map { Unit }

    suspend fun endLiveStream(streamId: String): Result<Unit> {
        val r = client.patchRows("live_streams", "id=eq.${urlEncode(streamId)}", mapOf("is_live" to false))
        client.deleteRows("live_frames", "stream_id=eq.${urlEncode(streamId)}")
        return r
    }

    suspend fun updateLiveCounters(streamId: String, viewerCount: Long? = null, likesCount: Long? = null, giftsCredits: Long? = null): Result<Unit> {
        val fields = linkedMapOf<String, Any?>()
        viewerCount?.let { fields["viewer_count"] = it.coerceAtLeast(0L) }
        likesCount?.let { fields["likes_count"] = it.coerceAtLeast(0L) }
        giftsCredits?.let { fields["gifts_credits"] = it.coerceAtLeast(0L) }
        if (fields.isEmpty()) return Result.success(Unit)
        return client.patchRows("live_streams", "id=eq.${urlEncode(streamId)}", fields)
    }

    suspend fun sendLiveComment(streamId: String, comment: LiveComment): Result<Unit> =
        client.upsertRows("live_comments", listOf(MomiSupabaseSchema.toLiveCommentRow(streamId, comment)), onConflict = "id").map { Unit }

    suspend fun upsertMerchant(merchant: MerchantAccount): Result<Unit> {
        client.ensureProfileExists(merchant.userId, merchant.ownerUsername, merchant.storeName, merchant.avatarKey, merchant.avatarUrl)
        return client.upsertRows("merchants", listOf(MomiSupabaseSchema.toMerchantRow(merchant)), onConflict = "user_id").map { Unit }
    }

    suspend fun upsertProduct(product: MarketProduct): Result<Unit> {
        client.ensureProfileExists(product.sellerUid, product.sellerUsername, product.storeName)
        return client.upsertRows("market_products", listOf(MomiSupabaseSchema.toMarketProductRow(product)), onConflict = "id").map { Unit }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> =
        client.deleteRows("market_products", "id=eq.${urlEncode(productId)}")

    suspend fun upsertOrder(order: MarketOrder): Result<Unit> =
        client.upsertRows("market_orders", listOf(MomiSupabaseSchema.toMarketOrderRow(order)), onConflict = "id").map { Unit }

    suspend fun upsertReport(report: ContentReport): Result<Unit> =
        client.upsertRows("content_reports", listOf(MomiSupabaseSchema.toContentReportRow(report)), onConflict = "id").map { Unit }

    suspend fun upsertWalletAndTx(wallet: UserWallet, tx: CreditTransaction?): Result<Unit> {
        client.ensureProfileExists(wallet.userId.ifBlank { wallet.id }, "momi.user")
        val wRes = client.upsertRows("wallets", listOf(MomiSupabaseSchema.toWalletRow(wallet)), onConflict = "user_id")
        if (wRes.isFailure) return wRes.map { Unit }
        if (tx != null) {
            return client.upsertRows("credit_transactions", listOf(MomiSupabaseSchema.toCreditTransactionRow(tx)), onConflict = "tx_id").map { Unit }
        }
        return Result.success(Unit)
    }

    suspend fun saveAppConfig(config: AdMonetizationConfig): Result<Unit> =
        client.upsertRows("app_config", listOf(MomiSupabaseSchema.toAppConfigRow(config)), onConflict = "id").map { Unit }

    suspend fun publishShowcaseBurst(
        adminUid: String = "",
        accounts: List<ShowcaseAccount> = emptyList()
    ): Result<Int> {
        val now = Timestamp.now()
        val stamp = System.currentTimeMillis()
        val sourceList = accounts.ifEmpty {
            ShowcaseCatalog.PERSONAS.map { p ->
                ShowcaseAccount(
                    username = p.username,
                    displayName = p.displayName,
                    avatarKey = p.avatarKey,
                    bio = "Featured Momi creator in ${p.location}",
                    category = p.category,
                    location = p.location,
                    isVerified = true,
                    isAuthorized = true
                )
            }
        }
        val selected = sourceList.shuffled().take(4)

        if (adminUid.isBlank()) {
            val personaProfiles = selected.map { persona ->
                val uid = "showcase_${persona.username}"
                UserProfile(
                    id = uid,
                    userId = uid,
                    username = persona.username,
                    displayName = persona.displayName,
                    category = persona.category,
                    bio = persona.bio.ifBlank { "Featured Momi creator in ${persona.location}" },
                    avatarKey = persona.avatarKey,
                    avatarUrl = persona.avatarUrl,
                    isVerified = persona.isVerified,
                    createdAt = now
                )
            }
            client.upsertRows(
                "profiles",
                personaProfiles.map(MomiSupabaseSchema::toProfileRow),
                onConflict = "user_id",
                ignoreDuplicates = true
            )
        }

        val posts = mutableListOf<Post>()
        val commentRows = mutableListOf<Map<String, Any?>>()
        val stories = mutableListOf<Story>()

        selected.forEachIndexed { idx, acct ->
            val preset = ShowcaseCatalog.PERSONAS.firstOrNull { it.username.equals(acct.username, ignoreCase = true) }
                ?: ShowcaseCatalog.PERSONAS[idx % ShowcaseCatalog.PERSONAS.size]
            val uid = adminUid.ifBlank { "showcase_${acct.username}" }
            val postId = "showcase_post_${stamp}_$idx"
            val commenter = ShowcaseCatalog.PERSONAS[(idx + 2) % ShowcaseCatalog.PERSONAS.size]
            val commentText = ShowcaseCatalog.COMMENTS[(idx + stamp.toInt()) % ShowcaseCatalog.COMMENTS.size]
            posts.add(
                Post(
                    id = postId,
                    userId = uid,
                    authorUsername = acct.username,
                    authorDisplayName = acct.displayName,
                    authorAvatarKey = acct.avatarKey,
                    authorAvatarUrl = acct.avatarUrl,
                    authorVerified = acct.isVerified,
                    location = acct.location.ifBlank { preset.location },
                    imageKey = preset.imageKey,
                    mediaType = "IMAGE",
                    filterTag = preset.filterTag,
                    aspectRatio = preset.aspectRatio,
                    hashtags = preset.hashtags,
                    caption = preset.caption,
                    likesCount = (18 + idx * 7).toLong(),
                    viewsCount = (120 + idx * 45).toLong(),
                    commentsCount = 1L,
                    category = acct.category.ifBlank { preset.category },
                    createdAt = now
                )
            )
            commentRows.add(
                MomiSupabaseSchema.toCommentRow(
                    postId = postId,
                    comment = Comment(
                        id = "showcase_cmt_${stamp}_$idx",
                        userId = uid,
                        authorUsername = commenter.username,
                        authorAvatarKey = commenter.avatarKey,
                        text = commentText,
                        likesCount = 3L,
                        createdAt = now
                    )
                )
            )
            if (idx < 3) {
                stories.add(
                    Story(
                        id = "showcase_story_${stamp}_$idx",
                        userId = uid,
                        username = acct.username,
                        avatarKey = acct.avatarKey,
                        avatarUrl = acct.avatarUrl,
                        imageKey = preset.imageKey,
                        mediaType = "IMAGE",
                        caption = acct.location.ifBlank { preset.location },
                        createdAt = now
                    )
                )
            }
        }

        val res = client.upsertRows("posts", posts.map(MomiSupabaseSchema::toPostRow), onConflict = "id")
        if (res.isFailure) return Result.failure(res.exceptionOrNull() ?: IllegalStateException("Showcase post failed"))
        if (commentRows.isNotEmpty()) {
            client.upsertRows("comments", commentRows, onConflict = "id")
        }
        if (stories.isNotEmpty()) {
            client.upsertRows("stories", stories.map(MomiSupabaseSchema::toStoryRow), onConflict = "id")
        }
        return Result.success(posts.size)
    }

    suspend fun removeAllShowcase(): Result<Unit> {
        client.deleteRows("comments", "id=like.showcase_*")
        client.deleteRows("posts", "id=like.showcase_*")
        client.deleteRows("stories", "id=like.showcase_*")
        return Result.success(Unit)
    }

    suspend fun upsertPage(page: MomiPage): Result<Unit> {
        client.ensureProfileExists(page.ownerUid, page.ownerUsername)
        return client.upsertRows("pages", listOf(MomiSupabaseSchema.toPageRow(page)), onConflict = "id").map { Unit }
    }

    suspend fun toggleFollowPage(
        page: MomiPage,
        userId: String,
        username: String,
        currentlyFollowing: Boolean
    ): Result<Long> {
        val edgeId = "${userId}_${page.id}"
        val nextCount = (page.followersCount + if (currentlyFollowing) -1L else 1L).coerceAtLeast(0L)
        val res = if (currentlyFollowing) {
            client.deleteRows("page_follows", "id=eq.${urlEncode(edgeId)}")
        } else {
            client.upsertRows(
                "page_follows",
                listOf(
                    linkedMapOf(
                        "id" to edgeId,
                        "page_id" to page.id,
                        "user_id" to userId,
                        "username" to username.ifBlank { "momi.user" }.take(50)
                    )
                ),
                onConflict = "id"
            ).map { Unit }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull() ?: IllegalStateException("Page follow update failed"))
        client.patchRows("pages", "id=eq.${urlEncode(page.id)}", mapOf("followers_count" to nextCount))
        return Result.success(nextCount)
    }

    suspend fun upsertGroup(group: MomiGroup, creatorUsername: String): Result<Unit> {
        client.ensureProfileExists(group.ownerUid, creatorUsername)
        val res = client.upsertRows("groups", listOf(MomiSupabaseSchema.toGroupRow(group)), onConflict = "id")
        if (res.isFailure) return res.map { Unit }
        client.upsertRows(
            "group_members",
            listOf(
                linkedMapOf(
                    "id" to "${group.ownerUid}_${group.id}",
                    "group_id" to group.id,
                    "user_id" to group.ownerUid,
                    "username" to creatorUsername.ifBlank { "momi.creator" }.take(50),
                    "role" to "ADMIN"
                )
            ),
            onConflict = "id"
        )
        return Result.success(Unit)
    }

    suspend fun toggleJoinGroup(
        group: MomiGroup,
        userId: String,
        username: String,
        currentlyMember: Boolean
    ): Result<Long> {
        val edgeId = "${userId}_${group.id}"
        val nextCount = (group.membersCount + if (currentlyMember) -1L else 1L).coerceAtLeast(0L)
        val res = if (currentlyMember) {
            client.deleteRows("group_members", "id=eq.${urlEncode(edgeId)}")
        } else {
            client.upsertRows(
                "group_members",
                listOf(
                    linkedMapOf(
                        "id" to edgeId,
                        "group_id" to group.id,
                        "user_id" to userId,
                        "username" to username.ifBlank { "momi.user" }.take(50),
                        "role" to "MEMBER"
                    )
                ),
                onConflict = "id"
            ).map { Unit }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull() ?: IllegalStateException("Group membership update failed"))
        client.patchRows("groups", "id=eq.${urlEncode(group.id)}", mapOf("members_count" to nextCount))
        return Result.success(nextCount)
    }

    suspend fun upsertShowcaseAccount(account: ShowcaseAccount): Result<Unit> {
        return client.upsertRows(
            "showcase_accounts",
            listOf(MomiSupabaseSchema.toShowcaseAccountRow(account)),
            onConflict = "username"
        ).map { Unit }
    }

    suspend fun deleteShowcaseAccount(username: String): Result<Unit> =
        client.deleteRows("showcase_accounts", "username=eq.${urlEncode(username)}")

    suspend fun publishCustomShowcasePost(account: ShowcaseAccount, post: Post): Result<Unit> {
        val ownerUid = post.userId.ifBlank { "showcase_${account.username}" }
        if (ownerUid.startsWith("showcase_")) {
            client.ensureProfileExists(
                userId = ownerUid,
                username = account.username,
                displayName = account.displayName,
                avatarKey = account.avatarKey,
                avatarUrl = account.avatarUrl
            )
        }
        return client.upsertRows("posts", listOf(MomiSupabaseSchema.toPostRow(post)), onConflict = "id").map { Unit }
    }

    suspend fun publishCustomShowcaseStory(account: ShowcaseAccount, story: Story): Result<Unit> {
        val ownerUid = story.userId.ifBlank { "showcase_${account.username}" }
        if (ownerUid.startsWith("showcase_")) {
            client.ensureProfileExists(
                userId = ownerUid,
                username = account.username,
                displayName = account.displayName,
                avatarKey = account.avatarKey,
                avatarUrl = account.avatarUrl
            )
        }
        return client.upsertRows("stories", listOf(MomiSupabaseSchema.toStoryRow(story)), onConflict = "id").map { Unit }
    }

    suspend fun purgeNonWhitelistAccountsAndData(adminUid: String): Result<PurgeAccountsSummary> {
        return runCatching {
            val allProfiles = client.fetchAllProfilesRaw()
            val toRemove = allProfiles.filterNot { MomiSupabaseClient.isWhitelistedAccountEmail(it.email) }
            val keptCount = allProfiles.size - toRemove.size

            if (adminUid.isNotBlank()) {
                client.patchRows("posts", "id=like.showcase_*&user_id=neq.${urlEncode(adminUid)}", mapOf("user_id" to adminUid))
                client.patchRows("stories", "id=like.showcase_*&user_id=neq.${urlEncode(adminUid)}", mapOf("user_id" to adminUid))
                client.patchRows("comments", "id=like.showcase_*&user_id=neq.${urlEncode(adminUid)}", mapOf("user_id" to adminUid))
            }

            client.deleteRows("posts", "id=like.seed_post_*")
            client.deleteRows("stories", "id=like.seed_story_*")
            client.deleteRows("market_products", "id=like.seed_prod_*")

            toRemove.forEach { target ->
                val encUid = urlEncode(target.userId)
                client.deleteRows("chat_messages", "sender_uid=eq.$encUid")
                client.deleteRows("threads", "or=(user_a_id.eq.$encUid,user_b_id.eq.$encUid)")
                client.deleteRows("pages", "owner_uid=eq.$encUid")
                client.deleteRows("groups", "owner_uid=eq.$encUid")
                client.deleteRows("market_orders", "or=(buyer_uid.eq.$encUid,seller_uid.eq.$encUid)")
                client.deleteRows("credit_transactions", "or=(sender_uid.eq.$encUid,recipient_uid.eq.$encUid)")
                client.deleteRows("content_reports", "or=(reporter_uid.eq.$encUid,target_uid.eq.$encUid)")
                client.deleteRows("profiles", "user_id=eq.$encUid").getOrThrow()
            }

            PurgeAccountsSummary(removedProfilesCount = toRemove.size, keptProfilesCount = keptCount)
        }
    }
}

data class PurgeAccountsSummary(
    val removedProfilesCount: Int,
    val keptProfilesCount: Int
)
