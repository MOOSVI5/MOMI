package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.supabase.MomiSupabaseClient
import com.example.data.supabase.SupabaseAuthSession
import com.example.ui.theme.AppearancePrefs
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    AppearancePrefs.loadSupabaseSessionRaw(context)?.let { parts ->
        if (MomiSupabaseClient.activeSession == null) {
            MomiSupabaseClient.activeSession = SupabaseAuthSession(
                accessToken = parts[0].orEmpty(),
                refreshToken = parts[1].orEmpty(),
                userId = parts[2].orEmpty(),
                email = parts[3]
            )
        }
    }
    if (Firebase.auth.currentUser != null) {
        AppearancePrefs.setAutoSignInEnabled(context, true)
        onAuthSuccess()
        return
    }
    if (!AppearancePrefs.isAutoSignInEnabled(context)) {
        onUnauthenticated()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val activityContext = context as? Activity ?: context

    suspend fun tryGoogleId(filterAuthorizedOnly: Boolean): Boolean {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorizedOnly)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val req = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = credentialManager.getCredential(activityContext, req)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(authCredential).await()
            AppearancePrefs.setAutoSignInEnabled(context, true)
            scope.launch {
                runCatching { MomiSupabaseClient().signInWithGoogleIdToken(googleIdToken) }
                    .getOrNull()?.getOrNull()?.let { sess ->
                        AppearancePrefs.saveSupabaseSession(context, sess.accessToken, sess.refreshToken, sess.userId, sess.email)
                    }
            }
            return true
        }
        return false
    }

    scope.launch {
        try {
            if (tryGoogleId(filterAuthorizedOnly = true)) {
                onAuthSuccess()
                return@launch
            }
        } catch (_: GetCredentialCancellationException) {
            onUnauthenticated()
            return@launch
        } catch (_: Exception) {
            // Fall through to unfiltered Credential Manager auto-select retry for fresh preview launches
        }

        try {
            if (tryGoogleId(filterAuthorizedOnly = false)) {
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activityContext = context as? Activity ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                AppearancePrefs.setAutoSignInEnabled(context, true)
                scope.launch {
                    runCatching { MomiSupabaseClient().signInWithGoogleIdToken(googleIdToken) }
                        .getOrNull()?.getOrNull()?.let { sess ->
                            AppearancePrefs.saveSupabaseSession(context, sess.accessToken, sess.refreshToken, sess.userId, sess.email)
                        }
                }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    AppearancePrefs.setAutoSignInEnabled(context, false)
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                AppearancePrefs.clearSupabaseSession(context)
                MomiSupabaseClient.activeSession = null
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
