package com.example.util

import android.content.Context
import android.os.Build
import com.example.BuildConfig
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
    val isPlayStoreManaged: Boolean = false
)

data class AppUpdateCheckResult(
    val currentVersionName: String,
    val availableUpdate: AvailableAppUpdate?
)

object AppUpdateChecker {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/MOOSVI5/MOMI/releases/latest"
    private const val RELEASES_FALLBACK_URL = "https://github.com/MOOSVI5/MOMI/releases/latest"
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

    suspend fun checkForUpdate(isPlayStoreInstall: Boolean = false): Result<AppUpdateCheckResult> =
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
                    if (rawBody.isBlank()) {
                        return@withContext Result.success(
                            AppUpdateCheckResult(BuildConfig.VERSION_NAME, null)
                        )
                    }

                    val json = JSONObject(rawBody)
                    val rawTag = json.optString("tag_name", "").trim()
                    val normalizedLatest = rawTag.removePrefix("v").removePrefix("V").trim()
                    val releaseHtmlUrl = json.optString("html_url", RELEASES_FALLBACK_URL)
                        .ifBlank { RELEASES_FALLBACK_URL }
                    val releaseTitle = json.optString("name", "").trim()
                        .ifBlank { "Momi v${normalizedLatest.ifBlank { rawTag }}" }
                    val releaseNotes = json.optString("body", "").trim()
                        .ifBlank { "A newer version of Momi is available with fixes and new features." }

                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name", "")
                            val browserUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && browserUrl.isNotBlank()) {
                                apkUrl = browserUrl
                                break
                            }
                        }
                    }

                    val isNewer = normalizedLatest.isNotBlank() &&
                        compareSemanticVersions(normalizedLatest, BuildConfig.VERSION_NAME) > 0

                    val update = if (isNewer) {
                        AvailableAppUpdate(
                            latestVersionName = normalizedLatest,
                            releaseTitle = releaseTitle,
                            releaseNotes = releaseNotes,
                            downloadUrl = apkUrl ?: releaseHtmlUrl,
                            isApkDirectLink = apkUrl != null,
                            isPlayStoreManaged = false
                        )
                    } else {
                        null
                    }

                    Result.success(
                        AppUpdateCheckResult(
                            currentVersionName = BuildConfig.VERSION_NAME,
                            availableUpdate = update
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun playStoreListingUrl(): String = PLAY_STORE_URL

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

