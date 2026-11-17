package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class MomiAiAssistantRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Momi Assistant, the built-in helper inside the Momi Android social & marketplace app. " +
                        "Help creators write catchy photo/clip captions, troubleshoot video uploads (under 8 MB compressed MP4), " +
                        "explain multi-image carousel posts, Studio Music & From Phone tracks, rich voice/video DMs, " +
                        "and guide buyers and verified merchants on the Momi Marketplace. " +
                        "Keep replies concise, friendly, accurate to the app, and actionable."
                )
            }
        )
    }

    fun streamReply(userPrompt: String): Flow<Result<String>> = flow {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("Please enter a question.")))
            return@flow
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(cleanPrompt).collect { chunk ->
                    val piece = chunk.text
                    if (!piece.isNullOrEmpty()) {
                        accumulated.append(piece)
                        emit(Result.success(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isEmpty()) {
                emit(Result.failure(IllegalStateException("No response received from Momi Assistant. Please try again.")))
            }
        } catch (e: TimeoutCancellationException) {
            emit(Result.failure(IllegalStateException("Assistant timed out. Check your connection and try again.", e)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.failure(error))
    }
}
