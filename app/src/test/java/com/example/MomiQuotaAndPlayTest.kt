package com.example

import com.example.data.repository.MomiFirestoreRepository
import com.example.data.repository.WalletRepository
import com.example.util.AppUpdateChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun semanticVersionComparisonOrdersNewerReleasesCorrectly() {
        assertTrue(AppUpdateChecker.compareSemanticVersions("2.0", "1.9") > 0)
        assertTrue(AppUpdateChecker.compareSemanticVersions("2.0.1", "2.0") > 0)
        assertEquals(0, AppUpdateChecker.compareSemanticVersions("2.0", "2.0.0"))
        assertTrue(AppUpdateChecker.compareSemanticVersions("1.8", "2.0") < 0)
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
    fun firestoreQueryLimitsStayWithinSparkFriendlyBounds() {
        assertTrue(MomiFirestoreRepository.POSTS_LIMIT <= 20L)
        assertTrue(MomiFirestoreRepository.FOLLOWS_LIMIT <= 100L)
        assertTrue(MomiFirestoreRepository.MARKERS_LIMIT <= 80L)
        assertTrue(WalletRepository.TRANSACTIONS_LIMIT <= 25L)
    }
}
