package com.example.data.repository

import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.util.MediaAssets
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class MomiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun multiUserFeed_aliceCreatesPost_bobLikesCommentsFollowsAndMessagesAlice() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "bob_${UUID.randomUUID()}@momi.photo"

        // 1. Alice signs in and publishes a post with compressed imageData
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Momi", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!

        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                profile = aliceProfile,
                imageKey = "img_post_coastal",
                localImagePath = null,
                imageData = "ZmFrZV9iYXNlNjRfanBlZ19wYXlsb2Fk",
                mediaType = "VIDEO",
                localVideoPath = null,
                videoData = "ZmFrZV9iYXNlNjRfbXA0X3BheWxvYWQ=",
                videoDurationMs = 4500L,
                filterTag = "NONE",
                caption = "Multi-user sunset clip",
                location = "Positano",
                category = "Travel",
                isReel = true
            ).getOrThrow()
        }
        assertTrue(postId.isNotBlank())

        // 2. Bob signs in on a second device, sees Alice's video post, thumbnail imageData, and videoData in the shared feed
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserSeeded("Bob Lens", bobEmail).getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeProfile(bobUid).first { it != null }
        }!!

        val sharedPost = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeAllPosts(bobUid).first { posts -> posts.any { it.id == postId } }
        }.first { it.id == postId }
        assertEquals(aliceUid, sharedPost.userId)
        assertEquals("ZmFrZV9iYXNlNjRfanBlZ19wYXlsb2Fk", sharedPost.imageData)
        assertEquals("VIDEO", sharedPost.mediaType)
        assertEquals("ZmFrZV9iYXNlNjRfbXA0X3BheWxvYWQ=", sharedPost.videoData)
        assertEquals(4500L, sharedPost.videoDurationMs)
        assertTrue(sharedPost.isVideoPost)

        // 3. Bob likes, bookmarks, comments, follows Alice, and sends Alice a DM
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleLike(sharedPost, bobProfile).getOrThrow()
            bobRepo.toggleBookmark(sharedPost).getOrThrow()
            bobRepo.addComment(sharedPost, bobProfile, "Looks incredible!").getOrThrow()
            bobRepo.toggleFollow(bobProfile, aliceUid, aliceProfile.username, currentlyFollowing = false).getOrThrow()
        }

        val likedIds = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLikedPostIds(bobUid).first { it.contains(postId) }
        }
        assertTrue(likedIds.contains(postId))

        val postAfterBob = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePost(bobUid, postId).first { it != null && it.likesCount == 1L && it.commentsCount == 1L }
        }!!
        assertEquals(1L, postAfterBob.likesCount)
        assertEquals(1L, postAfterBob.commentsCount)

        val thread = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.openOrCreateThread(bobProfile, aliceProfile).getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(thread, bobProfile, "Hey Alice, great shot!").getOrThrow()
        }

        // 4. Alice signs back in and sees Bob's like/comment notifications, shared comments, follower, and unread DM
        signInTestUser(aliceEmail)
        val aliceLikes = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeLikedPostIds(aliceUid).first()
        }
        assertFalse(aliceLikes.contains(postId)) // Bob's like did NOT mark Alice as having liked it!

        val aliceNotifs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeNotifications(aliceUid).first { list -> list.size >= 3 }
        }
        assertTrue(aliceNotifs.any { it.actorUid == bobUid && it.isFollowAction })

        val aliceThreads = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessageThreads(aliceUid).first { list -> list.any { it.id == thread.id } }
        }
        val sharedThreadForAlice = aliceThreads.first { it.id == thread.id }
        assertTrue(sharedThreadForAlice.isUnreadFor(aliceUid))
        assertEquals("Hey Alice, great shot!", sharedThreadForAlice.lastMessage)

        val messagesForAlice = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChatMessages(aliceUid, thread.id).first { it.isNotEmpty() }
        }
        assertEquals("Hey Alice, great shot!", messagesForAlice.first().text)
    }

    @Test
    fun crossUserPrivateData_bobCannotReadAliceBookmarksOrDeleteAlicePost() = runBlocking {
        val aliceEmail = "alice_iso_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "bob_iso_${UUID.randomUUID()}@momi.photo"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Iso", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!
        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                profile = aliceProfile,
                imageKey = "img_post_coastal",
                localImagePath = null,
                imageData = null,
                filterTag = "NONE",
                caption = "Alice private bookmark check",
                location = "Amalfi",
                category = "Travel",
                isReel = false
            ).getOrThrow()
        }
        val alicePost = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePost(aliceUid, postId).first { it != null }
        }!!
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.toggleBookmark(alicePost).getOrThrow()
        }

        // Bob tries to read Alice's private bookmarks and delete Alice's post
        signInTestUser(bobEmail)
        val readBookmarksError = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("bookmarks").get().await()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(readBookmarksError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, readBookmarksError?.code)

        val deletePostError = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("posts").document(postId).delete().await()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(deletePostError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deletePostError?.code)
    }

    @Test
    fun observeAllPosts_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MomiFirestoreRepository(firestore)

        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAllPosts("unauth").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected unauthenticated observer to fail", thrown)
        val firestoreException = checkNotNull(thrown!!.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $thrown"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun userVerificationAndAvatar_ownerCanUpdateAvatar_nonAdminCannotSelfVerify() = runBlocking {
        val aliceEmail = "alice_avatar_${UUID.randomUUID()}@momi.photo"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Avatar", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updateProfile(
                current = aliceProfile,
                displayName = "Alice Updated",
                username = aliceProfile.username,
                category = "Photographer",
                bio = "New bio",
                website = aliceProfile.website,
                avatarData = "Q1VTVE9NX0FWQVRBUl9EQVRB"
            ).getOrThrow()
        }
        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it?.avatarData == "Q1VTVE9NX0FWQVRBUl9EQVRB" }
        }!!
        assertEquals("Alice Updated", updated.displayName)
        assertFalse(updated.isVerified)

        val selfVerifyError = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                aliceRepo.setUserVerified(aliceUid, true).getOrThrow()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(selfVerifyError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, selfVerifyError?.code)
    }

    @Test
    fun chunkedVideoSync_aliceUploadsMultiChunkVideo_bobFetchesAndReassemblesExactBytes() = runBlocking {
        val aliceEmail = "alice_vid_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "bob_vid_${UUID.randomUUID()}@momi.photo"

        val originalBytes = ByteArray(750_000) { idx -> ((idx * 31) and 0xFF).toByte() }
        val prepared = MediaAssets.splitVideoBytes(originalBytes)
        assertNotNull(prepared)
        assertEquals(3, prepared!!.chunksCount)

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Video", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!

        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                profile = aliceProfile,
                imageKey = "img_post_coastal",
                localImagePath = "/nonexistent/alice_device_only.mp4",
                imageData = "dGh1bWI=",
                mediaType = "VIDEO",
                localVideoPath = "/nonexistent/alice_device_only.mp4",
                videoData = null,
                videoChunks = prepared.videoChunks,
                videoDurationMs = 24_000L,
                filterTag = "NONE",
                caption = "Cross-device chunked video",
                location = "Amalfi",
                category = "Travel",
                isReel = true
            ).getOrThrow()
        }

        // Bob signs in on another device where Alice's localVideoPath does not exist
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserSeeded("Bob Viewer", bobEmail).getOrThrow()
        }

        val sharedPost = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeAllPosts(bobUid).first { list -> list.any { it.id == postId } }
        }.first { it.id == postId }
        assertTrue(sharedPost.isVideoPost)
        assertEquals(3L, sharedPost.videoChunksCount)

        val fetchedChunks = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.fetchVideoChunks("posts", postId, sharedPost.videoChunksCount)
        }
        assertEquals(3, fetchedChunks.size)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cachedUri = withTimeout(DEFAULT_TIMEOUT_MS) {
            MediaAssets.assembleChunksToCacheFile(context, postId, fetchedChunks)
        }
        assertNotNull(cachedUri)
        val assembledFile = File(cachedUri!!.path!!)
        assertTrue(assembledFile.exists())
        assertArrayEquals(originalBytes, assembledFile.readBytes())
    }

    @Test
    fun viewsAndLiveStreams_multiUserViewIncrementAndLiveChatRoomWorkEndToEnd() = runBlocking {
        val aliceEmail = "alice_live_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "bob_live_${UUID.randomUUID()}@momi.photo"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Live", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!

        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                profile = aliceProfile,
                imageKey = "img_post_coastal",
                localImagePath = null,
                imageData = null,
                filterTag = "NONE",
                caption = "Views test post",
                location = "Amalfi",
                category = "Travel",
                isReel = false
            ).getOrThrow()
        }

        // Alice goes live
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.startLiveStream(aliceProfile, "Morning sunset walk").getOrThrow()
        }

        // Bob views Alice's post and joins Alice's live stream
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserSeeded("Bob Live", bobEmail).getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeProfile(bobUid).first { it != null }
        }!!

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.incrementPostView(postId).getOrThrow()
        }
        val viewedPost = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePost(bobUid, postId).first { it?.viewsCount == 1L }
        }!!
        assertEquals(1L, viewedPost.viewsCount)

        val liveStreams = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeActiveLiveStreams(bobUid).first { list -> list.any { it.userId == aliceUid } }
        }
        assertTrue(liveStreams.any { it.title == "Morning sunset walk" && it.isLive })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinLiveStream(aliceUid).getOrThrow()
            bobRepo.sendLiveHeart(aliceUid).getOrThrow()
            bobRepo.sendLiveComment(aliceUid, bobProfile, "Awesome stream!").getOrThrow()
        }

        val updatedStream = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLiveStream(bobUid, aliceUid).first {
                it != null && it.viewerCount == 1L && it.likesCount == 1L
            }
        }!!
        assertEquals(1L, updatedStream.viewerCount)
        assertEquals(1L, updatedStream.likesCount)

        val comments = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLiveComments(bobUid, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("Awesome stream!", comments.first().text)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.leaveLiveStream(aliceUid).getOrThrow()
        }

        signInTestUser(aliceEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.endLiveStream(aliceUid).getOrThrow()
        }
        val endedStream = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeLiveStream(aliceUid, aliceUid).first { it != null && !it.isLive }
        }!!
        assertFalse(endedStream.isLive)
    }

    @Test
    fun carouselAudioAndRichMessages_syncAcrossDevices() = runBlocking {
        val aliceEmail = "alice_rich_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "bob_rich_${UUID.randomUUID()}@momi.photo"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice Rich", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeProfile(aliceUid).first { it != null }
        }!!

        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                profile = aliceProfile,
                imageKey = "img_post_coastal",
                localImagePath = "/tmp/img1.jpg",
                imageData = "SU1HMQ==",
                localExtraImagePaths = listOf("/tmp/img2.jpg", "/tmp/img3.jpg"),
                extraImageDatas = listOf("SU1HMg==", "SU1HMw=="),
                audioTitle = "Midnight Lo-Fi · Momi Studio",
                audioData = "QVVERElPX0I2NA==",
                filterTag = "NONE",
                caption = "3-photo carousel with soundtrack",
                location = "Lahore",
                category = "Travel",
                isReel = false
            ).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val bobRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserSeeded("Bob Rich", bobEmail).getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeProfile(bobUid).first { it != null }
        }!!

        val sharedCarousel = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePost(bobUid, postId).first { it != null }
        }!!
        assertEquals(3, sharedCarousel.allImageFrames.size)
        assertEquals("QVVERElPX0I2NA==", sharedCarousel.audioData)

        val thread = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.openOrCreateThread(bobProfile, aliceProfile).getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMediaMessage(
                thread = thread,
                senderProfile = bobProfile,
                messageType = "AUDIO",
                audioData = "Vk9JQ0VfQjY0",
                audioDurationMs = 3400L
            ).getOrThrow()
        }

        signInTestUser(aliceEmail)
        val aliceMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChatMessages(aliceUid, thread.id).first { it.isNotEmpty() }
        }
        assertEquals("AUDIO", aliceMsgs.first().messageType)
        assertEquals("Vk9JQ0VfQjY0", aliceMsgs.first().audioData)
        assertEquals(3400L, aliceMsgs.first().audioDurationMs)
    }

    @Test
    fun marketplace_merchantCreationProductPublishAndPrivateOrdersWorkEndToEnd() = runBlocking {
        val aliceEmail = "seller_${UUID.randomUUID()}@momi.photo"
        val bobEmail = "buyer_${UUID.randomUUID()}@momi.photo"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceProfileRepo = MomiFirestoreRepository(firestore)
        val aliceMarketRepo = MarketplaceRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceProfileRepo.ensureUserSeeded("Aria Store", aliceEmail).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceProfileRepo.observeProfile(aliceUid).first { it != null }
        }!!

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMarketRepo.createOrUpdateMerchant(
                profile = aliceProfile,
                storeName = "Aria Vintage Deals",
                tagline = "Free shipping collectibles",
                category = "Collectibles",
                location = "Karachi, 75500"
            ).getOrThrow()
        }

        val aliceMerchant = withTimeout(FLOW_TIMEOUT_MS) {
            aliceMarketRepo.observeMerchants(aliceUid).first { list -> list.any { it.userId == aliceUid } }
        }.first { it.userId == aliceUid }
        assertFalse(aliceMerchant.isVerified)

        // Non-admin cannot verify merchant
        val verifyErr = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                aliceMarketRepo.setMerchantVerified(aliceUid, true).getOrThrow()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(verifyErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, verifyErr?.code)

        val productId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMarketRepo.publishProduct(
                profile = aliceProfile,
                merchant = aliceMerchant,
                title = "Retro Film Camera",
                description = "Tested shutter and lens.",
                category = "Electronics",
                priceUsd = 42.48,
                originalPriceUsd = 72.00,
                freeShipping = true,
                imageData = "UFJPRF9JTUc=",
                extraImageDatas = listOf("RVhUUkEx"),
                stockCount = 5L
            ).getOrThrow()
        }
        assertTrue(productId.isNotBlank())

        // Bob signs in, views product, and places order
        val bobUid = signInTestUser(bobEmail)
        val bobProfileRepo = MomiFirestoreRepository(firestore)
        val bobMarketRepo = MarketplaceRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobProfileRepo.ensureUserSeeded("Bob Buyer", bobEmail).getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobProfileRepo.observeProfile(bobUid).first { it != null }
        }!!

        val product = withTimeout(FLOW_TIMEOUT_MS) {
            bobMarketRepo.observeProducts(bobUid).first { list -> list.any { it.id == productId } }
        }.first { it.id == productId }
        assertEquals(2, product.allProductFrames.size)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobMarketRepo.placeOrder(bobProfile, product, "44000 Islamabad, PK").getOrThrow()
        }
        val bobOrders = withTimeout(FLOW_TIMEOUT_MS) {
            bobMarketRepo.observeOrdersForUser(bobUid).first { list -> list.any { it.id == orderId } }
        }
        assertEquals("PLACED", bobOrders.first { it.id == orderId }.status)

        // Seller updates order status to SHIPPED
        signInTestUser(aliceEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMarketRepo.updateOrderStatus(orderId, "SHIPPED").getOrThrow()
        }
        val sellerOrders = withTimeout(FLOW_TIMEOUT_MS) {
            aliceMarketRepo.observeOrdersForUser(aliceUid).first { list ->
                list.any { it.id == orderId && it.status == "SHIPPED" }
            }
        }
        assertEquals("SHIPPED", sellerOrders.first { it.id == orderId }.status)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
