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

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
