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
import com.example.ui.theme.AppearancePrefs
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SupabaseAuth"

private fun persistSession(context: Context, sess: com.example.data.supabase.SupabaseAuthSession) {
    AppearancePrefs.setAutoSignInEnabled(context, true)
    AppearancePrefs.saveSupabaseSession(
        context = context,
        accessToken = sess.accessToken,
        refreshToken = sess.refreshToken,
        userId = sess.userId,
        email = sess.email,
        displayName = sess.displayName,
        avatarUrl = sess.avatarUrl,
        phone = sess.phone
    )
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val restored = AppearancePrefs.loadSavedSupabaseSession(context)
    if (restored != null) {
        AppearancePrefs.setAutoSignInEnabled(context, true)
        if (!com.example.data.supabase.MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(restored.accessToken)) {
            MomiSupabaseClient.activeSession = restored
            onAuthSuccess()
            return
        }
        if (restored.refreshToken.isNotBlank()) {
            scope.launch {
                val res = MomiSupabaseClient().refreshSession(restored.refreshToken)
                res.onSuccess { refreshed ->
                    persistSession(context, refreshed)
                    onAuthSuccess()
                }.onFailure { err ->
                    if (err.message?.startsWith("AUTH_EXPIRED:") == true) {
                        AppearancePrefs.clearSupabaseSession(context)
                        MomiSupabaseClient.activeSession = null
                        onUnauthenticated()
                    } else {
                        MomiSupabaseClient.activeSession = restored
                        onAuthSuccess()
                    }
                }
            }
            return
        }
        AppearancePrefs.clearSupabaseSession(context)
    }

    if (!AppearancePrefs.isAutoSignInEnabled(context)) {
        onUnauthenticated()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val activityContext = context as? Activity ?: context
    val supabaseClient = MomiSupabaseClient()

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
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val sess = supabaseClient.signInWithGoogleIdToken(
                idToken = googleCred.idToken,
                displayNameHint = googleCred.displayName,
                avatarUrlHint = googleCred.profilePictureUri?.toString()
            ).getOrThrow()
            persistSession(context, sess)
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
            // Fall through to unfiltered Credential Manager auto-select retry
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
    } catch (_: Exception) {
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
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val sess = MomiSupabaseClient().signInWithGoogleIdToken(
                    idToken = googleCred.idToken,
                    displayNameHint = googleCred.displayName,
                    avatarUrlHint = googleCred.profilePictureUri?.toString()
                ).getOrElse { err ->
                    onAuthError(err.localizedMessage ?: "Supabase Google Sign-In failed.")
                    return@launch
                }
                persistSession(context, sess)
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

fun onEmailPasswordAuthClicked(
    context: Context,
    email: String,
    password: String,
    displayName: String,
    isSignUp: Boolean,
    onAuthSuccess: () -> Unit,
    onConfirmationSent: (String) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val client = MomiSupabaseClient()
    scope.launch {
        if (isSignUp) {
            client.signUpWithEmailPassword(email = email, password = password, displayName = displayName)
                .onSuccess { res ->
                    val sess = res.session
                    if (sess != null) {
                        persistSession(context, sess)
                        onAuthSuccess()
                    } else {
                        onConfirmationSent(res.email)
                    }
                }
                .onFailure { err ->
                    onAuthError(err.localizedMessage ?: "Could not create account.")
                }
        } else {
            client.signInWithEmailPassword(email = email, password = password)
                .onSuccess { sess ->
                    persistSession(context, sess)
                    onAuthSuccess()
                }
                .onFailure { err ->
                    onAuthError(err.localizedMessage ?: "Sign in failed. Check email and password.")
                }
        }
    }
}

fun onVerifyEmailOtpClicked(
    context: Context,
    email: String,
    otpCode: String,
    displayName: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val client = MomiSupabaseClient()
    scope.launch {
        client.verifyEmailSignupOtp(email = email, otpCode = otpCode, displayName = displayName)
            .onSuccess { sess ->
                persistSession(context, sess)
                onAuthSuccess()
            }
            .onFailure { err ->
                onAuthError(err.localizedMessage ?: "Invalid or expired verification code.")
            }
    }
}

fun onResendEmailOtpClicked(
    email: String,
    onSent: (String) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val client = MomiSupabaseClient()
    scope.launch {
        client.resendEmailSignupOtp(email = email)
            .onSuccess {
                onSent("Verification code resent to ${email.trim()}. Check your inbox and spam folder.")
            }
            .onFailure { err ->
                onAuthError(err.localizedMessage ?: "Could not resend verification code.")
            }
    }
}

fun onSendWhatsAppOtpClicked(
    phone: String,
    displayName: String,
    email: String,
    onOtpSent: (String) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val client = MomiSupabaseClient()
    scope.launch {
        client.sendWhatsAppOtp(
            phone = phone,
            displayName = displayName,
            email = email
        ).onSuccess { normalizedPhone ->
            onOtpSent(normalizedPhone)
        }.onFailure { err ->
            onAuthError(err.localizedMessage ?: "Could not send WhatsApp verification code.")
        }
    }
}

fun onVerifyWhatsAppOtpClicked(
    context: Context,
    phone: String,
    otpCode: String,
    displayName: String,
    email: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val client = MomiSupabaseClient()
    scope.launch {
        client.verifyWhatsAppOtp(
            phone = phone,
            otpCode = otpCode,
            displayName = displayName,
            email = email
        ).onSuccess { sess ->
            persistSession(context, sess)
            onAuthSuccess()
        }.onFailure { err ->
            onAuthError(err.localizedMessage ?: "Invalid or expired WhatsApp verification code.")
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
                val activeToken = MomiSupabaseClient.activeSession?.accessToken
                AppearancePrefs.clearSupabaseSession(context)
                MomiSupabaseClient().signOutSession(activeToken)
                onSignOutComplete()
            }
        }
    }
}
