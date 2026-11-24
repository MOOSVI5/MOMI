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

    fun currentConfigStatus(): GoogleAiStatus {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (apiKey.isEmpty() || apiKey == PLACEHOLDER_KEY) {
            GoogleAiStatus(
                state = GoogleAiHealthState.MISSING_KEY,
                label = "Needs GEMINI_API_KEY",
                detail = "Set GEMINI_API_KEY in AI Studio Secrets (or GitHub Actions Secrets for APK builds) so Google Gemini can answer."
            )
        } else {
            GoogleAiStatus(
                state = GoogleAiHealthState.READY,
                label = "Google AI Ready",
                detail = "GEMINI_API_KEY configured · Using Gemini 2.5 Flash"
            )
        }
    }

    suspend fun probeGoogleAiStatus(): GoogleAiStatus {
        val configStatus = currentConfigStatus()
        if (configStatus.state == GoogleAiHealthState.MISSING_KEY) {
            return configStatus
        }
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
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
                detail = "Live response verified on $modelUsed",
                activeModel = modelUsed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GoogleAiStatus(
                state = GoogleAiHealthState.ERROR,
                label = "Google AI Not Answering",
                detail = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not reach Google Gemini API. Check GEMINI_API_KEY and internet connection."
            )
        }
    }

    fun streamReply(
        userPrompt: String,
        history: List<Pair<Boolean, String>> = emptyList()
    ): Flow<Result<String>> = flow {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("Please enter a question.")))
            return@flow
        }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == PLACEHOLDER_KEY) {
            emit(
                Result.failure(
                    IllegalStateException(
                        "Google AI is not configured yet: add GEMINI_API_KEY in AI Studio Secrets (and GitHub Secrets if building APK) and rebuild."
                    )
                )
            )
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
            emit(Result.failure(e))
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.failure(error))
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
            // Keep thinking budget at 0 for assistant chat so internal thoughts never consume output tokens
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
