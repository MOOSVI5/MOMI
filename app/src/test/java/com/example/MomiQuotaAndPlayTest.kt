package com.example

import com.example.ai.GoogleAiHealthState
import com.example.ai.MomiAiAssistantRepository
import com.example.data.model.AdMonetizationConfig
import com.example.data.supabase.MomiSupabaseSchema
import com.example.util.AppUpdateChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomiQuotaAndPlayTest {

    @Test
    fun playStoreInstallSkipsExternalGithubApkUpdatePrompt() = runTest {
        val result = AppUpdateChecker.checkForUpdate(isPlayStoreInstall = true)
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(BuildConfig.VERSION_NAME, payload.currentVersionName)
        assertNull(payload.availableUpdate)
    }

    @Test
    fun semanticVersionAndBuildNumberComparisonOrdersNewerReleasesCorrectly() {
        assertTrue(AppUpdateChecker.compareSemanticVersions("2.0", "1.9") > 0)
        assertTrue(AppUpdateChecker.compareSemanticVersions("2.0.1", "2.0") > 0)
        assertEquals(0, AppUpdateChecker.compareSemanticVersions("2.0", "2.0.0"))
        assertTrue(AppUpdateChecker.compareSemanticVersions("1.8", "2.0") < 0)

        assertTrue(
            AppUpdateChecker.isCandidateNewer(
                candidateVersion = "2.2",
                currentVersion = "2.2",
                candidateVersionCode = 14,
                currentVersionCode = 13
            )
        )
        assertTrue(
            AppUpdateChecker.isCandidateNewer(
                candidateVersion = "2.2",
                currentVersion = "2.2",
                candidateBuildNumber = 15,
                currentBuildNumber = 14
            )
        )
        assertFalse(
            AppUpdateChecker.isCandidateNewer(
                candidateVersion = "2.2",
                currentVersion = "2.2",
                candidateVersionCode = 13,
                currentVersionCode = 13,
                candidateBuildNumber = 14,
                currentBuildNumber = 14
            )
        )
    }

    @Test
    fun supabaseConfigUpdateTriggersForNewerVersionOnlyOnNonPlayInstalls() {
        val newerCfg = AdMonetizationConfig(
            latestVersionName = "2.4",
            announcementTitle = "Momi v2.4 Live",
            releaseNotes = "Faster Live Rooms"
        )
        val nonPlayUpdate = AppUpdateChecker.evaluateSupabaseConfigUpdate(
            config = newerCfg,
            isPlayStoreInstall = false
        )
        assertNotNull(nonPlayUpdate)
        assertEquals("2.4", nonPlayUpdate?.latestVersionName)

        assertNull(
            AppUpdateChecker.evaluateSupabaseConfigUpdate(
                config = newerCfg,
                isPlayStoreInstall = true
            )
        )
        assertNull(
            AppUpdateChecker.evaluateSupabaseConfigUpdate(
                config = AdMonetizationConfig(latestVersionName = BuildConfig.VERSION_NAME),
                isPlayStoreInstall = false
            )
        )
    }

    @Test
    fun dismissedVersionIsSuppressedForAutoChecksUntilNewerOrManual() {
        assertFalse(
            AppUpdateChecker.shouldSurfaceUpdate(
                latestVersionName = "2.1",
                dismissedVersionName = "2.1",
                manual = false
            )
        )
        assertTrue(
            AppUpdateChecker.shouldSurfaceUpdate(
                latestVersionName = "2.2",
                dismissedVersionName = "2.1",
                manual = false
            )
        )
        assertTrue(
            AppUpdateChecker.shouldSurfaceUpdate(
                latestVersionName = "2.1",
                dismissedVersionName = "2.1",
                manual = true
            )
        )
    }

    @Test
    fun aiRepositoryConfigStatusReportsExplicitKeyState() {
        val status = MomiAiAssistantRepository().currentConfigStatus()
        val expectedModel = if (BuildConfig.GEMINI_API_KEY.isBlank() ||
            BuildConfig.GEMINI_API_KEY == "YOUR_GEMINI_API_KEY"
        ) {
            "momi-assistant-local"
        } else {
            "gemini-2.5-flash"
        }
        assertEquals(GoogleAiHealthState.READY, status.state)
        assertEquals(expectedModel, status.activeModel)
        assertTrue(status.label.isNotBlank())
        assertTrue(status.detail.isNotBlank())
    }

    @Test
    fun supabaseQueryLimitsStayWithinBoundedLimits() {
        assertTrue(MomiSupabaseSchema.POSTS_LIMIT <= 20L)
        assertTrue(MomiSupabaseSchema.FOLLOWS_LIMIT <= 100L)
        assertTrue(MomiSupabaseSchema.MARKERS_LIMIT <= 80L)
        assertTrue(MomiSupabaseSchema.TRANSACTIONS_LIMIT <= 25L)
    }
}
