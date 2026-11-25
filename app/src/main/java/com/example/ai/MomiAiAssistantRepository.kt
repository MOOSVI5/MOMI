package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class GoogleAiHealthState {
    CHECKING,
    READY,
    MISSING_KEY,
    ERROR
}

data class GoogleAiStatus(
    val state: GoogleAiHealthState,
    val label: String,
    val detail: String,
    val activeModel: String = "gemini-2.5-flash"
)

class MomiAiAssistantRepository(
    private val httpClient: OkHttpClient = defaultHttpClient
) {
    private val systemPrompt =
        "You are Momi Assistant, the built-in creator & marketplace helper inside the Momi app. " +
            "Help creators write catchy photo/clip captions, set up Creator Pages & Community Groups, " +
            "go live, troubleshoot video uploads (compressed MP4), explain multi-image carousels, " +
            "Studio Music tracks, voice/video DMs, and guide buyers and verified merchants on Momi Marketplace. " +
            "Keep replies concise, friendly, accurate to Momi, and actionable."

    private fun resolveActiveApiKey(userOverrideKey: String = ""): String {
        val custom = userOverrideKey.trim()
        if (custom.isNotEmpty() && custom != PLACEHOLDER_KEY) return custom
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty() && buildKey != PLACEHOLDER_KEY) return buildKey
        return ""
    }

    fun currentConfigStatus(userOverrideKey: String = ""): GoogleAiStatus {
        val apiKey = resolveActiveApiKey(userOverrideKey)
        return if (apiKey.isEmpty()) {
            GoogleAiStatus(
                state = GoogleAiHealthState.READY,
                label = "Momi AI Assistant Ready",
                detail = "Built-in Creator & Studio Assistant active · Add optional Gemini key for live cloud replies.",
                activeModel = "momi-assistant-local"
            )
        } else {
            GoogleAiStatus(
                state = GoogleAiHealthState.READY,
                label = "Google AI Ready",
                detail = "Gemini API key configured · Live Gemini 2.5 Flash active",
                activeModel = "gemini-2.5-flash"
            )
        }
    }

    suspend fun probeGoogleAiStatus(userOverrideKey: String = ""): GoogleAiStatus {
        val apiKey = resolveActiveApiKey(userOverrideKey)
        if (apiKey.isEmpty()) {
            return GoogleAiStatus(
                state = GoogleAiHealthState.READY,
                label = "Momi AI Assistant Ready",
                detail = "Instant Momi Creator Assistant ready now. Paste a Gemini key below anytime for live cloud AI.",
                activeModel = "momi-assistant-local"
            )
        }
        return try {
            val (modelUsed, _) = withTimeout(20_000L) {
                requestGeminiReplyWithModel(
                    apiKey = apiKey,
                    prompt = "Respond with one word: Ready.",
                    history = emptyList(),
                    maxOutputTokens = 32
                )
            }
            GoogleAiStatus(
                state = GoogleAiHealthState.READY,
                label = "Google AI Connected",
                detail = "Live Gemini response verified on $modelUsed",
                activeModel = modelUsed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Could not reach Gemini API"
            GoogleAiStatus(
                state = GoogleAiHealthState.ERROR,
                label = "Using Built-in Assistant Fallback",
                detail = "$reason · Built-in Momi answers still work immediately."
            )
        }
    }

    fun streamReply(
        userPrompt: String,
        history: List<Pair<Boolean, String>> = emptyList(),
        userOverrideKey: String = ""
    ): Flow<Result<String>> = flow {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("Please enter a question.")))
            return@flow
        }

        val apiKey = resolveActiveApiKey(userOverrideKey)
        if (apiKey.isEmpty()) {
            emit(Result.success(generateBuiltInMomiReply(cleanPrompt)))
            return@flow
        }

        try {
            val (_, reply) = withTimeout(45_000L) {
                requestGeminiReplyWithModel(
                    apiKey = apiKey,
                    prompt = cleanPrompt,
                    history = history,
                    maxOutputTokens = 1024
                )
            }
            emit(Result.success(reply))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallback = generateBuiltInMomiReply(cleanPrompt)
            emit(
                Result.success(
                    "$fallback\n\n_(Answered by built-in Momi Assistant because Gemini API returned: ${e.localizedMessage ?: "network error"})_"
                )
            )
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.success(generateBuiltInMomiReply(userPrompt)))
    }

    fun generateBuiltInMomiReply(prompt: String): String {
        val q = prompt.lowercase()
        return when {
            listOf("caption", "travel", "hashtag", "post", "write", "quote").any { q.contains(it) } -> {
                "Here are 3 ready-to-use Momi captions:\n" +
                    "1. \"Golden light, quiet streets, and stories worth keeping. ✨\"\n" +
                    "2. \"Weekend reset in full motion — save this view for later.\"\n" +
                    "3. \"Behind every frame is a moment we almost missed.\"\n\n" +
                    "Tip: Add a Studio Music track in Create Post and pick 1-3 crisp photos or an MP4 clip under 60s for faster uploads."
            }
            listOf("page", "group", "community", "communit", "grow", "audience", "follower").any { q.contains(it) } -> {
                "To grow your Momi Page & Group:\n" +
                    "1. Open Communities from the top bar -> create either a Public Creator Page or an interactive Community Group.\n" +
                    "2. Share 1 showcase photo/clip daily with clear captions and Studio Music.\n" +
                    "3. Reply to comments early and invite members into your Group chat & Live sessions."
            }
            listOf("video", "clip", "upload", "play", "stuck", "reel", "media", "mp4").any { q.contains(it) } -> {
                "Quick video & clip checklist on Momi:\n" +
                    "• Export clips in standard MP4 (H.264 video + AAC audio), ideally 720p/1080p under 25 MB for instant playback.\n" +
                    "• Tap any video card on Home or Reels to start playback immediately.\n" +
                    "• If an older draft shows loading, pull down to refresh so Supabase fetches the latest media URL."
            }
            listOf("monetiz", "earn", "admob", "start.io", "credit", "wallet", "market", "sell", "shop").any { q.contains(it) } -> {
                "Ways to monetize on Momi:\n" +
                    "1. Open Owner Admin Console -> Play & Ads to configure AdMob Banner/Interstitial/Rewarded IDs and Start.io App ID.\n" +
                    "2. Sell digital or physical items in Momi Marketplace with verified merchant badges.\n" +
                    "3. Enable creator gifts & Credits inside Wallet and Live rooms."
            }
            listOf("story", "stories", "24", "expire").any { q.contains(it) } -> {
                "Momi Stories stay visible for exactly 24 hours from publish time, then expire automatically across Android and Web. Tap '+' on Your Story to publish a photo story anytime."
            }
            listOf("like", "view", "viewer", "who saw", "analytics", "stat").any { q.contains(it) } -> {
                "Tap the likes count or views count on any post card or post detail page to open the full list of people who liked and viewed that post."
            }
            else -> {
                "I'm Momi Assistant! Here is how I can help right now:\n" +
                    "• Captions & Reels ideas (ask: \"Write a caption for my café clip\")\n" +
                    "• Pages & Community Groups growth tips\n" +
                    "• Video playback & upload troubleshooting\n" +
                    "• AdMob / Start.io monetization & Marketplace setup\n\n" +
                    "Tip: For open-ended AI brainstorming, tap 'Gemini Key' above to paste a Google Gemini API key anytime."
            }
        }
    }

    private suspend fun requestGeminiReplyWithModel(
        apiKey: String,
        prompt: String,
        history: List<Pair<Boolean, String>>,
        maxOutputTokens: Int
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (model in MODEL_CANDIDATES) {
            try {
                val reply = executeSingleModelCall(
                    apiKey = apiKey,
                    model = model,
                    prompt = prompt,
                    history = history,
                    maxOutputTokens = maxOutputTokens
                )
                return@withContext model to reply
            } catch (e: CancellationException) {
                throw e
            } catch (e: NonRetryableGeminiException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: IllegalStateException("Google AI did not return a response. Please try again."))
    }

    private fun executeSingleModelCall(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<Pair<Boolean, String>>,
        maxOutputTokens: Int
    ): String {
        val contentsArray = JSONArray()
        history
            .takeLast(6)
            .filter { it.second.isNotBlank() }
            .forEach { (isUser, text) ->
                contentsArray.put(
                    JSONObject()
                        .put("role", if (isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", text.trim())))
                )
            }
        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )

        val generationConfig = JSONObject()
            .put("temperature", 0.7)
            .put("maxOutputTokens", maxOutputTokens)
        if (model.startsWith("gemini-2.5")) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }

        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }.toString()

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/$model:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { !it.isNullOrBlank() }
                val message = detail ?: "Google AI ($model) returned HTTP ${response.code}."
                if (response.code in setOf(400, 401, 403) &&
                    detail?.contains("API key", ignoreCase = true) == true
                ) {
                    throw NonRetryableGeminiException("Invalid GEMINI_API_KEY: $message")
                }
                throw IllegalStateException(message)
            }

            val root = JSONObject(rawBody)
            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (blockReason.isNotBlank()) {
                throw NonRetryableGeminiException("Request could not be answered ($blockReason). Try rephrasing.")
            }

            val firstCandidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val textBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val partObj = parts.optJSONObject(i) ?: continue
                    if (partObj.optBoolean("thought", false)) continue
                    val segment = partObj.optString("text").orEmpty()
                    if (segment.isNotBlank()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                        textBuilder.append(segment)
                    }
                }
            }
            val result = textBuilder.toString().trim()
            if (result.isEmpty()) {
                val finishReason = firstCandidate?.optString("finishReason").orEmpty()
                if (finishReason == "SAFETY" || finishReason == "RECITATION") {
                    throw NonRetryableGeminiException("Response filtered by safety check ($finishReason). Try rephrasing.")
                }
                error("Google AI ($model) returned an empty response (${finishReason.ifBlank { "no text" }}).")
            }
            return result
        }
    }

    private class NonRetryableGeminiException(message: String) : IllegalStateException(message)

    companion object {
        private const val PLACEHOLDER_KEY = "YOUR_GEMINI_API_KEY"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODEL_CANDIDATES = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
