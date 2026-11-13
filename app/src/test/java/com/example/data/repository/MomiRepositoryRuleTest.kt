package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MomiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun seedAndObserveProfileAndPosts_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = MomiFirestoreRepository(firestore)

        val seedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserSeeded("Alice Momi", "alice@momi.photo")
        }
        assertTrue(seedResult.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals(uid, profile?.userId)

        val posts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAllPosts(uid).first { it.size >= 7 }
        }
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.all { it.id.isNotBlank() })
    }

    @Test
    fun createPost_toggleLikeBookmarkAndAddComment_succeeds() = runBlocking {
        val uid = signInTestUser("alice_actions_${UUID.randomUUID()}@test.com")
        val repository = MomiFirestoreRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserSeeded("Aria Vance", "aria@momi.photo").getOrThrow()
        }
        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null }
        }!!

        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPost(
                profile = profile,
                imageKey = "img_post_coastal",
                localImagePath = null,
                filterTag = "BLUE",
                caption = "Test Silk Blue frame",
                location = "Positano",
                category = "Travel",
                isReel = false
            ).getOrThrow()
        }
        assertTrue(postId.isNotBlank())

        val createdPost = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePost(uid, postId).first { it != null }
        }!!
        assertEquals(1L, createdPost.likesCount)
        assertTrue(createdPost.isLiked)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleLike(createdPost).getOrThrow()
            repository.toggleBookmark(createdPost).getOrThrow()
        }

        val updatedPost = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePost(uid, postId).first { it != null && !it.isLiked && it.isBookmarked }
        }!!
        assertFalse(updatedPost.isLiked)
        assertTrue(updatedPost.isBookmarked)

        val commentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addComment(updatedPost, profile.username, profile.avatarKey, "Gorgeous light!").getOrThrow()
        }
        assertTrue(commentId.isNotBlank())

        val comments = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeComments(uid, postId).first { list -> list.any { it.id == commentId } }
        }
        assertEquals(1, comments.size)
        assertEquals("Gorgeous light!", comments.first().text)
    }

    @Test
    fun crossUserRead_bobAccessingAlicePost_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
        val aliceRepo = MomiFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserSeeded("Alice", "alice@momi.photo").getOrThrow()
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
        val bobError = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("posts").get().await()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(bobError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, bobError?.code)
    }

    @Test
    fun observeAllPosts_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MomiFirestoreRepository(firestore)

        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAllPosts("alice_unauth").first()
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

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
