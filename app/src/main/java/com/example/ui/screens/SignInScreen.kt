package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.attemptAutoSignIn
import com.example.auth.onEmailPasswordAuthClicked
import com.example.auth.onGoogleSignInClicked
import com.example.auth.onResendEmailOtpClicked
import com.example.auth.onSendWhatsAppOtpClicked
import com.example.auth.onVerifyEmailOtpClicked
import com.example.auth.onVerifyWhatsAppOtpClicked
import com.example.ui.theme.LocalMomiBrandGradient
import com.example.ui.theme.LumaScriptFamily

@Composable
fun SignInScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isAutoChecking by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    var showEmailForm by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var pendingVerificationEmail by remember { mutableStateOf<String?>(null) }
    var otpCodeInput by remember { mutableStateOf("") }

    var showWhatsAppForm by remember { mutableStateOf(false) }
    var whatsappPhoneInput by remember { mutableStateOf("") }
    var whatsappNameInput by remember { mutableStateOf("") }
    var whatsappEmailInput by remember { mutableStateOf("") }
    var pendingWhatsAppPhone by remember { mutableStateOf<String?>(null) }
    var whatsappOtpInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAutoChecking = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = coroutineScope
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(LocalMomiBrandGradient.current))
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        fontFamily = LumaScriptFamily,
                        fontSize = 42.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Momi",
                    fontFamily = LumaScriptFamily,
                    fontSize = 44.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sign in with Supabase to share moments, stories, clips & shop across Android and Web.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                if (infoMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = infoMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Button(
                    onClick = {
                        errorMessage = null
                        infoMessage = null
                        isSigningIn = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                errorMessage = msg
                                if (msg.contains("provider", ignoreCase = true) || msg.contains("audience", ignoreCase = true)) {
                                    showEmailForm = true
                                }
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                isSigningIn = false
                            }
                        )
                    },
                    enabled = !isSigningIn && !isAutoChecking,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isSigningIn || isAutoChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        infoMessage = null
                        showWhatsAppForm = !showWhatsAppForm
                        if (showWhatsAppForm) showEmailForm = false
                    },
                    enabled = !isSigningIn && !isAutoChecking,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("toggle_whatsapp_login_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp)
                    )
                    Text(
                        text = if (showWhatsAppForm) "Hide WhatsApp Login" else "Continue with WhatsApp",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showWhatsAppForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val verifyPhone = pendingWhatsAppPhone
                        if (verifyPhone != null) {
                            Text(
                                text = "Enter the 6-digit verification code sent via WhatsApp to $verifyPhone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = whatsappOtpInput,
                                onValueChange = { whatsappOtpInput = it.filter { ch -> ch.isDigit() }.take(8) },
                                label = { Text("WhatsApp OTP Code") },
                                leadingIcon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whatsapp_otp_input")
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    isSigningIn = true
                                    onVerifyWhatsAppOtpClicked(
                                        context = context,
                                        phone = verifyPhone,
                                        otpCode = whatsappOtpInput,
                                        displayName = whatsappNameInput,
                                        email = whatsappEmailInput,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            pendingWhatsAppPhone = null
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            errorMessage = msg
                                        },
                                        scope = coroutineScope
                                    )
                                },
                                enabled = !isSigningIn && whatsappOtpInput.length >= 4,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_whatsapp_otp_button")
                            ) {
                                Text("Verify WhatsApp & Continue", fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        isSigningIn = true
                                        onSendWhatsAppOtpClicked(
                                            phone = verifyPhone,
                                            displayName = whatsappNameInput,
                                            email = whatsappEmailInput,
                                            onOtpSent = { sentPhone ->
                                                isSigningIn = false
                                                infoMessage = "New WhatsApp verification code sent to $sentPhone."
                                            },
                                            onAuthError = { msg ->
                                                isSigningIn = false
                                                errorMessage = msg
                                            },
                                            scope = coroutineScope
                                        )
                                    },
                                    enabled = !isSigningIn,
                                    modifier = Modifier.testTag("resend_whatsapp_otp_button")
                                ) {
                                    Text("Resend Code", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        pendingWhatsAppPhone = null
                                        whatsappOtpInput = ""
                                        errorMessage = null
                                    }
                                ) {
                                    Text("Change Number", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = whatsappNameInput,
                                onValueChange = { whatsappNameInput = it },
                                label = { Text("Display Name (optional)") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whatsapp_name_input")
                            )
                            OutlinedTextField(
                                value = whatsappPhoneInput,
                                onValueChange = { whatsappPhoneInput = it },
                                label = { Text("WhatsApp Number (+country code)") },
                                placeholder = { Text("+923001234567") },
                                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whatsapp_phone_input")
                            )
                            OutlinedTextField(
                                value = whatsappEmailInput,
                                onValueChange = { whatsappEmailInput = it },
                                label = { Text("Email (optional, 1 account per email)") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whatsapp_email_input")
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    isSigningIn = true
                                    onSendWhatsAppOtpClicked(
                                        phone = whatsappPhoneInput,
                                        displayName = whatsappNameInput,
                                        email = whatsappEmailInput,
                                        onOtpSent = { sentPhone ->
                                            isSigningIn = false
                                            pendingWhatsAppPhone = sentPhone
                                            whatsappOtpInput = ""
                                            infoMessage = "WhatsApp verification code sent to $sentPhone."
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            errorMessage = msg
                                        },
                                        scope = coroutineScope
                                    )
                                },
                                enabled = !isSigningIn && !isAutoChecking && whatsappPhoneInput.isNotBlank(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("send_whatsapp_otp_button")
                            ) {
                                Text("Send WhatsApp OTP", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            showEmailForm = !showEmailForm
                            if (showEmailForm) showWhatsAppForm = false
                        },
                        modifier = Modifier.testTag("toggle_email_login_button")
                    ) {
                        Text(
                            text = if (showEmailForm) "Hide Email Login" else "Use Email & Password",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                AnimatedVisibility(visible = showEmailForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val verifyEmail = pendingVerificationEmail
                        if (verifyEmail != null) {
                            Text(
                                text = "Enter the 6-digit verification code sent to $verifyEmail.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = otpCodeInput,
                                onValueChange = { otpCodeInput = it.filter { ch -> ch.isDigit() }.take(8) },
                                label = { Text("6-Digit Verification Code") },
                                leadingIcon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    isSigningIn = true
                                    onVerifyEmailOtpClicked(
                                        context = context,
                                        email = verifyEmail,
                                        otpCode = otpCodeInput,
                                        displayName = displayNameInput,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            pendingVerificationEmail = null
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            errorMessage = msg
                                        },
                                        scope = coroutineScope
                                    )
                                },
                                enabled = !isSigningIn && otpCodeInput.length >= 6,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                Text("Verify Email & Continue", fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        onResendEmailOtpClicked(
                                            email = verifyEmail,
                                            onSent = { msg -> infoMessage = msg },
                                            onAuthError = { msg -> errorMessage = msg },
                                            scope = coroutineScope
                                        )
                                    },
                                    modifier = Modifier.testTag("resend_otp_button")
                                ) {
                                    Text("Resend Code", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        pendingVerificationEmail = null
                                        otpCodeInput = ""
                                        isSignUpMode = false
                                        errorMessage = null
                                    }
                                ) {
                                    Text("Back to Sign In", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            if (isSignUpMode) {
                                OutlinedTextField(
                                    value = displayNameInput,
                                    onValueChange = { displayNameInput = it },
                                    label = { Text("Display Name") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("display_name_input")
                                )
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("email_input")
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password (6+ chars)") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input")
                            )

                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    isSigningIn = true
                                    onEmailPasswordAuthClicked(
                                        context = context,
                                        email = emailInput,
                                        password = passwordInput,
                                        displayName = displayNameInput,
                                        isSignUp = isSignUpMode,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            onAuthSuccess()
                                        },
                                        onConfirmationSent = { sentEmail ->
                                            isSigningIn = false
                                            pendingVerificationEmail = sentEmail
                                            otpCodeInput = ""
                                            infoMessage = "Verification code sent to $sentEmail. Enter the 6-digit code below."
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            errorMessage = msg
                                        },
                                        scope = coroutineScope
                                    )
                                },
                                enabled = !isSigningIn && !isAutoChecking,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("email_sign_in_button")
                            ) {
                                Text(
                                    text = if (isSignUpMode) "Create Momi Account" else "Sign In with Email",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            TextButton(
                                onClick = {
                                    isSignUpMode = !isSignUpMode
                                    errorMessage = null
                                    infoMessage = null
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("switch_auth_mode_button")
                            ) {
                                Text(
                                    text = if (isSignUpMode) {
                                        "Already have an account? Sign in"
                                    } else {
                                        "New to Momi? Create an account"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (isAutoChecking) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Restoring your Supabase session...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
