package com.example

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Post
import com.example.data.model.ShowcaseCatalog
import com.example.ui.theme.AppearancePrefs
import com.example.util.MediaAssets
import com.example.util.MomiAlertNotifier
import com.example.util.MomiDeepLink
import com.example.util.MomiLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MomiFeaturesTest {

    @Test
    fun extractHashtags_and_mentions_normalize_and_deduplicate() {
        val caption = "Sunset walk with @Aria.Studio and @kenji_vibes #Travel #Momi #travel"
        val tags = MediaAssets.extractHashtags(caption, listOf("#StreetStyle", "MOMI", "bad!tag"))
        val mentions = MediaAssets.extractMentions(caption, listOf("@Nord", "aria.studio"))

        assertEquals(listOf("streetstyle", "momi", "badtag", "travel"), tags)
        assertEquals(listOf("nord", "aria.studio", "kenji_vibes"), mentions)
    }

    @Test
    fun filterOptions_and_editMatrices_work_across_all_10_filters() {
        assertEquals(10, MediaAssets.postFilterOptions.size)
        assertNull(MediaAssets.colorFilterForTag("NONE"))
        assertNull(MediaAssets.colorFilterForEdit("NONE", brightness = 0f, contrast = 1f, warmth = 0f))

        MediaAssets.postFilterOptions
            .filter { it.tag != "NONE" }
            .forEach { option ->
                assertNotNull("Expected ColorFilter for ${option.tag}", MediaAssets.colorFilterForTag(option.tag))
            }

        assertNotNull(MediaAssets.colorFilterForEdit("NONE", brightness = 0.15f, contrast = 1.15f, warmth = -0.2f))
    }

    @Test
    fun post_aspectRatio_hashtags_and_taggedUsernames_resolve_accurately() {
        val portrait = Post(
            aspectRatio = "4:5",
            hashtags = listOf("ootd"),
            taggedUsernames = listOf("aria.studio"),
            caption = "Morning coffee #Coffee with @kenji"
        )
        val landscape = Post(aspectRatio = "16:9")
        val videoPost = Post(mediaType = "VIDEO", aspectRatio = "1:1")

        assertEquals(0.8f, portrait.numericAspectRatio, 0.001f)
        assertEquals(1.777f, landscape.numericAspectRatio, 0.01f)
        assertEquals(0.85f, videoPost.numericAspectRatio, 0.001f)
        assertEquals(listOf("ootd", "coffee"), portrait.allHashtags)
        assertEquals(listOf("aria.studio", "kenji"), portrait.allTaggedUsernames)
    }

    @Test
    fun alertNotifier_registers_high_importance_channel_and_persists_sound_pref() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MomiAlertNotifier.ensureChannel(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(MomiAlertNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        assertTrue(AppearancePrefs.isNotificationSoundsEnabled(context))
        AppearancePrefs.setNotificationSoundsEnabled(context, false)
        assertFalse(AppearancePrefs.isNotificationSoundsEnabled(context))
        AppearancePrefs.setNotificationSoundsEnabled(context, true)
        assertTrue(AppearancePrefs.isNotificationSoundsEnabled(context))
    }

    @Test
    fun momiLinks_builds_and_parses_canonical_post_and_profile_deep_links() {
        val postUrl = MomiLinks.buildPostShareUrl("post_abc_123")
        assertEquals("https://moosvi5.github.io/MOMI/?post=post_abc_123", postUrl)

        val parsedPost = MomiLinks.parseDeepLink(Uri.parse(postUrl))
        assertEquals(MomiDeepLink.PostTarget("post_abc_123"), parsedPost)

        val parsedHashPost = MomiLinks.parseDeepLink(Uri.parse("https://moosvi5.github.io/MOMI/#post-post_xyz_9"))
        assertEquals(MomiDeepLink.PostTarget("post_xyz_9"), parsedHashPost)

        val parsedSchemePost = MomiLinks.parseDeepLink(Uri.parse("momi://post/post_777"))
        assertEquals(MomiDeepLink.PostTarget("post_777"), parsedSchemePost)

        val profileUrl = MomiLinks.buildProfileShareUrl("@aria.studio")
        assertEquals("https://moosvi5.github.io/MOMI/?user=aria.studio", profileUrl)
        assertEquals(MomiDeepLink.UserTarget("aria.studio"), MomiLinks.parseDeepLink(Uri.parse(profileUrl)))

        val shareBody = MomiLinks.buildPostShareText(
            Post(id = "post_abc_123", authorUsername = "aria.studio", authorDisplayName = "Aria", caption = "Golden hour")
        )
        assertTrue(shareBody.contains(postUrl))
        assertTrue(shareBody.contains(MomiLinks.ANDROID_APK_URL))
    }

    @Test
    fun showcasePersonas_satisfy_filter_aspectRatio_and_schema_constraints() {
        val validFilterTags = MediaAssets.postFilterOptions.map { it.tag }.toSet()
        assertEquals(6, ShowcaseCatalog.PERSONAS.size)
        assertTrue(ShowcaseCatalog.COMMENTS.size >= 4)

        ShowcaseCatalog.PERSONAS.forEach { persona ->
            assertTrue(persona.username.isNotBlank())
            assertTrue(persona.displayName.isNotBlank())
            assertTrue(validFilterTags.contains(persona.filterTag))
            assertTrue(persona.aspectRatio in setOf("1:1", "4:5", "16:9"))
            assertTrue(persona.hashtags.isNotEmpty())
            assertTrue(persona.caption.isNotBlank())
        }
    }
}
