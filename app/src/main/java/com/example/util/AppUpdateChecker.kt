package com.example.util

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.data.model.AdMonetizationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AvailableAppUpdate(
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isApkDirectLink: Boolean,
    val isPlayStoreManaged: Boolean = false,
    val sourceLabel: String = "GitHub Release",
    val remoteBuildNumber: Int = 0
)

data class AppUpdateCheckResult(
    val currentVersionName: String,
    val availableUpdate: AvailableAppUpdate?
)

object AppUpdateChecker {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/MOOSVI5/MOMI/releases/latest"
    private const val RELEASES_FALLBACK_URL = "https://github.com/MOOSVI5/MOMI/releases/latest"
    private const val LATEST_APK_FALLBACK_URL = "https://github.com/MOOSVI5/MOMI/releases/latest/download/Momi-latest.apk"
    private const val WEB_VERSION_MANIFEST_URL = "https://moosvi5.github.io/MOMI/version.json"
    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun isInstalledFromGooglePlay(context: Context): Boolean {
        return runCatching {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        }.getOrDefault(false)
    }

    fun evaluateSupabaseConfigUpdate(
        config: AdMonetizationConfig,
        isPlayStoreInstall: Boolean
    ): AvailableAppUpdate? {
        if (isPlayStoreInstall) return null
        val remoteVer = config.latestVersionName.trim().removePrefix("v").removePrefix("V")
        if (remoteVer.isBlank()) return null
        if (!isCandidateNewer(candidateVersion = remoteVer, currentVersion = BuildConfig.VERSION_NAME)) {
            return null
        }
        val notes = config.releaseNotes.trim()
            .ifBlank { config.announcementBody.trim() }
            .ifBlank { "A newer Momi update (v$remoteVer) is live with fixes and new features." }
        val title = config.announcementTitle.trim().ifBlank { "Momi v$remoteVer" }
        return AvailableAppUpdate(
            latestVersionName = remoteVer,
            releaseTitle = title,
            releaseNotes = notes,
            downloadUrl = LATEST_APK_FALLBACK_URL,
            isApkDirectLink = true,
            isPlayStoreManaged = false,
            sourceLabel = "Supabase App Config"
        )
    }

    suspend fun checkForUpdate(
        isPlayStoreInstall: Boolean = false,
        supabaseConfig: AdMonetizationConfig? = null
    ): Result<AppUpdateCheckResult> =
        withContext(Dispatchers.IO) {
            try {
                if (isPlayStoreInstall) {
                    return@withContext Result.success(
                        AppUpdateCheckResult(
                            currentVersionName = BuildConfig.VERSION_NAME,
                            availableUpdate = null
                        )
                    )
                }

                val candidates = mutableListOf<AvailableAppUpdate>()
                var lastFailure: Exception? = null

                // 1) GitHub Releases API (picks up released signed APKs & CI Build #N)
                fetchGithubReleaseUpdate()
                    .onSuccess { it?.let(candidates::add) }
                    .onFailure { if (it is Exception) lastFailure = it }

                // 2) Live Web version.json (auto-stamped by GitHub Actions on EVERY push to main)
                fetchWebManifestUpdate()
                    .onSuccess { it?.let(candidates::add) }
                    .onFailure { if (it is Exception) lastFailure = it }

                // 3) Supabase app_config (Owner Console instant version bump)
                supabaseConfig?.let { cfg ->
                    evaluateSupabaseConfigUpdate(cfg, isPlayStoreInstall = false)?.let(candidates::add)
                }

                val bestUpdate = candidates.maxWithOrNull { a, b ->
                    val verCmp = compareSemanticVersions(a.latestVersionName, b.latestVersionName)
                    if (verCmp != 0) verCmp else a.remoteBuildNumber.compareTo(b.remoteBuildNumber)
                }

                if (bestUpdate == null && candidates.isEmpty() && lastFailure != null) {
                    // Still succeed cleanly if at least one remote check responded without a newer version;
                    // only fail when ALL network checks errored and no supabase update existed.
                    if (supabaseConfig == null) {
                        return@withContext Result.failure(lastFailure!!)
                    }
                }

                Result.success(
                    AppUpdateCheckResult(
                        currentVersionName = BuildConfig.VERSION_NAME,
                        availableUpdate = bestUpdate
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchGithubReleaseUpdate(): Result<AvailableAppUpdate?> = runCatching {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Momi-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GitHub release check returned HTTP ${response.code}")
            }
            val rawBody = response.body?.string().orEmpty()
            if (rawBody.isBlank()) return@runCatching null

            val json = JSONObject(rawBody)
            val rawTag = json.optString("tag_name", "").trim()
            val normalizedLatest = rawTag.removePrefix("v").removePrefix("V").trim()
            val releaseHtmlUrl = json.optString("html_url", RELEASES_FALLBACK_URL)
                .ifBlank { RELEASES_FALLBACK_URL }
            val releaseTitle = json.optString("name", "").trim()
                .ifBlank { "Momi v${normalizedLatest.ifBlank { rawTag }}" }
            val releaseNotes = json.optString("body", "").trim()
                .ifBlank { "A newer version of Momi is available with fixes and new features." }
            val remoteBuildNumber = Regex("CI Build #(\\d+)")
                .find(releaseNotes)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0

            var versionedApkUrl: String? = null
            var latestApkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val browserUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && browserUrl.isNotBlank()) {
                        if (name.equals("Momi-latest.apk", ignoreCase = true)) {
                            latestApkUrl = browserUrl
                        } else if (versionedApkUrl == null) {
                            versionedApkUrl = browserUrl
                        }
                    }
                }
            }
            val resolvedApkUrl = versionedApkUrl ?: latestApkUrl

            val isNewer = normalizedLatest.isNotBlank() && isCandidateNewer(
                candidateVersion = normalizedLatest,
                currentVersion = BuildConfig.VERSION_NAME,
                candidateBuildNumber = remoteBuildNumber,
                currentBuildNumber = BuildConfig.CI_BUILD_NUMBER
            )

            if (!isNewer) {
                null
            } else {
                AvailableAppUpdate(
                    latestVersionName = normalizedLatest,
                    releaseTitle = if (remoteBuildNumber > 0) "$releaseTitle (Build #$remoteBuildNumber)" else releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = resolvedApkUrl ?: releaseHtmlUrl,
                    isApkDirectLink = resolvedApkUrl != null,
                    isPlayStoreManaged = false,
                    sourceLabel = "GitHub Release",
                    remoteBuildNumber = remoteBuildNumber
                )
            }
        }
    }

    private fun fetchWebManifestUpdate(): Result<AvailableAppUpdate?> = runCatching {
        val request = Request.Builder()
            .url("$WEB_VERSION_MANIFEST_URL?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Momi-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Version manifest check returned HTTP ${response.code}")
            }
            val rawBody = response.body?.string().orEmpty()
            if (rawBody.isBlank()) return@runCatching null

            val json = JSONObject(rawBody)
            val remoteVersion = json.optString("versionName", "")
                .removePrefix("v")
                .removePrefix("V")
                .trim()
            val remoteCode = json.optInt("versionCode", 0)
            val remoteBuild = json.optInt("buildNumber", 0)
            val commitSha = json.optString("commitSha", "").trim()
            val title = json.optString("releaseTitle", "").trim()
                .ifBlank { "Momi v${remoteVersion.ifBlank { BuildConfig.VERSION_NAME }}" }
            val notes = json.optString("releaseNotes", "").trim()
                .ifBlank { "New pushed update available for Momi." }
            val apkUrl = json.optString("apkUrl", LATEST_APK_FALLBACK_URL)
                .ifBlank { LATEST_APK_FALLBACK_URL }

            val isNewer = isCandidateNewer(
                candidateVersion = remoteVersion.ifBlank { BuildConfig.VERSION_NAME },
                currentVersion = BuildConfig.VERSION_NAME,
                candidateVersionCode = remoteCode,
                currentVersionCode = BuildConfig.VERSION_CODE,
                candidateBuildNumber = remoteBuild,
                currentBuildNumber = BuildConfig.CI_BUILD_NUMBER
            )

            if (!isNewer) {
                null
            } else {
                val buildTag = if (remoteBuild > 0) "Build #$remoteBuild${if (commitSha.isNotBlank()) " · $commitSha" else ""}" else ""
                AvailableAppUpdate(
                    latestVersionName = remoteVersion.ifBlank { BuildConfig.VERSION_NAME },
                    releaseTitle = if (buildTag.isNotBlank()) "$title ($buildTag)" else title,
                    releaseNotes = notes,
                    downloadUrl = apkUrl,
                    isApkDirectLink = apkUrl.endsWith(".apk", ignoreCase = true),
                    isPlayStoreManaged = false,
                    sourceLabel = "Live Push Manifest",
                    remoteBuildNumber = remoteBuild
                )
            }
        }
    }

    private const val PREFS_NAME = "momi_update_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_update_version"
    private const val KEY_LAST_AUTO_CHECK_AT_MS = "last_auto_check_at_ms"
    internal const val AUTO_CHECK_COOLDOWN_MS = 1L * 60L * 60L * 1000L

    fun playStoreListingUrl(): String = PLAY_STORE_URL

    fun updateBadgeKey(versionName: String, buildNumber: Int = 0): String {
        val clean = versionName.trim()
        return if (buildNumber > 0) "$clean+$buildNumber" else clean
    }

    fun isVersionDismissed(context: Context, versionName: String, buildNumber: Int = 0): Boolean {
        val clean = versionName.trim()
        if (clean.isEmpty()) return false
        val dismissed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_VERSION, null)
            ?.trim()
            ?: return false
        val dismissedVer = dismissed.substringBefore("+")
        val dismissedBuild = dismissed.substringAfter("+", "0").toIntOrNull() ?: 0
        val cmp = compareSemanticVersions(clean, dismissedVer)
        if (cmp != 0) return cmp < 0
        return buildNumber <= dismissedBuild
    }

    fun dismissVersion(context: Context, versionName: String, buildNumber: Int = 0) {
        val clean = versionName.trim()
        if (clean.isEmpty()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_VERSION, updateBadgeKey(clean, buildNumber))
            .apply()
    }

    fun clearDismissedVersion(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DISMISSED_VERSION)
            .apply()
    }

    fun shouldRunAutoCheck(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTO_CHECK_AT_MS, 0L)
        return nowMs - last >= AUTO_CHECK_COOLDOWN_MS
    }

    fun recordAutoCheck(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_CHECK_AT_MS, nowMs)
            .apply()
    }

    internal fun isCandidateNewer(
        candidateVersion: String,
        currentVersion: String,
        candidateVersionCode: Int = 0,
        currentVersionCode: Int = 0,
        candidateBuildNumber: Int = 0,
        currentBuildNumber: Int = 0
    ): Boolean {
        val semCmp = compareSemanticVersions(candidateVersion, currentVersion)
        if (semCmp > 0) return true
        if (semCmp < 0) return false
        if (candidateVersionCode > 0 && currentVersionCode > 0 && candidateVersionCode > currentVersionCode) {
            return true
        }
        return currentBuildNumber > 0 && candidateBuildNumber > currentBuildNumber
    }

    internal fun shouldSurfaceUpdate(
        latestVersionName: String,
        dismissedVersionName: String?,
        manual: Boolean
    ): Boolean {
        if (manual) return true
        val cleanLatest = latestVersionName.trim()
        val cleanDismissed = dismissedVersionName?.trim().orEmpty()
        if (cleanDismissed.isEmpty()) return true
        return compareSemanticVersions(cleanLatest, cleanDismissed) > 0
    }

    internal fun compareSemanticVersions(candidate: String, current: String): Int {
        val leftParts = candidate.split(".", "-", "_").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val rightParts = current.split(".", "-", "_").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxLen) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }
}
