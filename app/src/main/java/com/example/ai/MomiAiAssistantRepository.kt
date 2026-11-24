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

class MomiAiAssistantRepository(
    private val httpClient: OkHttpClient = defaultHttpClient
) {
    private val systemPrompt =
        "You are Momi Assistant, the built-in creator & marketplace helper inside the Momi app. " +
            "Help creators write catchy photo/clip captions, set up Creator Pages & Community Groups, " +
            "go live, troubleshoot video uploads (compressed MP4), explain multi-image carousels, " +
            "Studio Music tracks, voice/video DMs, and guide buyers and verified merchants on Momi Marketplace. " +
            "Keep replies concise, friendly, accurate to Momi, and actionable."

    fun streamReply(userPrompt: String): Flow<Result<String>> = flow {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("Please enter a question.")))
            return@flow
        }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY") {
            emit(
                Result.failure(
                    IllegalStateException(
                        "Momi AI Assistant needs GEMINI_API_KEY configured in AI Studio Secrets."
                    )
                )
            )
            return@flow
        }

        try {
            val reply = withTimeout(45_000L) {
                requestGeminiReply(apiKey = apiKey, prompt = cleanPrompt)
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

    private suspend fun requestGeminiReply(apiKey: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 700)
                )
            }.toString()

            val request = Request.Builder()
                .url("$GEMINI_ENDPOINT?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { !it.isNullOrBlank() }
                    error(detail ?: "Momi AI returned HTTP ${response.code}. Please try again.")
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val textBuilder = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val segment = parts.optJSONObject(i)?.optString("text").orEmpty()
                        if (segment.isNotBlank()) {
                            if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                            textBuilder.append(segment)
                        }
                    }
                }
                val result = textBuilder.toString().trim()
                require(result.isNotEmpty()) {
                    "No response received from Momi Assistant. Please try again."
                }
                result
            }
        }

    companion object {
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
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
