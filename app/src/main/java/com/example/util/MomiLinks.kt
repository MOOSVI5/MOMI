package com.example.util

import android.net.Uri
import com.example.data.model.Post

sealed class MomiDeepLink {
    data class PostTarget(val postId: String) : MomiDeepLink()
    data class UserTarget(val username: String) : MomiDeepLink()
}

object MomiLinks {
    const val WEB_APP_URL = "https://moosvi5.github.io/MOMI/"
    const val ANDROID_APK_URL = "https://github.com/MOOSVI5/MOMI/releases/latest/download/Momi-v2.1.apk"
    const val ANDROID_RELEASES_URL = "https://github.com/MOOSVI5/MOMI/releases/latest"

    private val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,128}$")

    fun buildPostShareUrl(postId: String): String {
        val cleanId = sanitizeId(postId)
        return if (cleanId != null) {
            "${WEB_APP_URL}?post=${Uri.encode(cleanId)}"
        } else {
            WEB_APP_URL
        }
    }

    fun buildProfileShareUrl(username: String): String {
        val cleanUser = sanitizeUsername(username)
        return if (cleanUser != null) {
            "${WEB_APP_URL}?user=${Uri.encode(cleanUser)}"
        } else {
            WEB_APP_URL
        }
    }

    fun buildPostShareText(post: Post): String {
        val url = buildPostShareUrl(post.id)
        val captionSnippet = post.caption.trim()
            .replace("\n", " ")
            .let { if (it.length > 140) it.take(137) + "..." else it }
        val header = if (captionSnippet.isNotEmpty()) {
            "${post.authorDisplayName} (@${post.authorUsername}) on Momi:\n\"$captionSnippet\""
        } else {
            "See @${post.authorUsername}'s post on Momi"
        }
        return "$header\n\nOpen in Momi:\n$url\n\nGet Momi for Android: $ANDROID_APK_URL"
    }

    fun buildProfileShareText(username: String): String {
        val clean = sanitizeUsername(username) ?: "momi"
        val url = buildProfileShareUrl(clean)
        return "Check out @$clean on Momi:\n$url\n\nWeb & Desktop: $WEB_APP_URL\nGet Android App: $ANDROID_APK_URL"
    }

    fun parseDeepLink(uri: Uri?): MomiDeepLink? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()

        if (scheme == "momi") {
            return when (host) {
                "post" -> {
                    val raw = uri.pathSegments.firstOrNull()
                        ?: uri.getQueryParameter("id")
                        ?: uri.getQueryParameter("post")
                    sanitizeId(raw)?.let { MomiDeepLink.PostTarget(it) }
                }
                "user" -> {
                    val raw = uri.pathSegments.firstOrNull()
                        ?: uri.getQueryParameter("username")
                        ?: uri.getQueryParameter("user")
                    sanitizeUsername(raw)?.let { MomiDeepLink.UserTarget(it) }
                }
                else -> null
            }
        }

        if ((scheme == "https" || scheme == "http") && host == "moosvi5.github.io") {
            val postParam = uri.getQueryParameter("post") ?: uri.getQueryParameter("p")
            sanitizeId(postParam)?.let { return MomiDeepLink.PostTarget(it) }

            val userParam = uri.getQueryParameter("user") ?: uri.getQueryParameter("u")
            sanitizeUsername(userParam)?.let { return MomiDeepLink.UserTarget(it) }

            val fragment = uri.fragment.orEmpty()
            if (fragment.startsWith("post-")) {
                sanitizeId(fragment.removePrefix("post-"))?.let { return MomiDeepLink.PostTarget(it) }
            }
            if (fragment.startsWith("user-")) {
                sanitizeUsername(fragment.removePrefix("user-"))?.let { return MomiDeepLink.UserTarget(it) }
            }
        }

        return null
    }

    private fun sanitizeId(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.takeIf { SAFE_ID_REGEX.matches(it) }
    }

    private fun sanitizeUsername(raw: String?): String? {
        val trimmed = raw?.trim()?.removePrefix("@").orEmpty()
        return trimmed.takeIf { SAFE_ID_REGEX.matches(it) }
    }
}
