package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GoogleAiHealthState
import com.example.ai.GoogleAiStatus

data class AiAssistantTurn(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data object Generating : AiAssistantUiState
    data class Error(val message: String) : AiAssistantUiState
}

private val QuickAssistantPrompts = listOf(
    "Write a catchy travel caption",
    "Ideas to grow my Momi Page & Group",
    "Best ways to monetize my app",
    "Why did my video fail to upload?"
)

@Composable
fun FloatingMomiAssistantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .testTag("ai_assistant_fab")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = "Open Momi AI Assistant",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomiAiAssistantBottomSheet(
    messages: List<AiAssistantTurn>,
    uiState: AiAssistantUiState,
    aiStatus: GoogleAiStatus,
    isCheckingStatus: Boolean,
    savedApiKey: String = "",
    onSaveApiKey: (String) -> Unit = {},
    onCheckStatus: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var promptDraft by remember { mutableStateOf("") }
    var showKeyEditor by remember { mutableStateOf(false) }
    var apiKeyDraft by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    val isBusy = uiState is AiAssistantUiState.Generating

    LaunchedEffect(Unit) {
        onCheckStatus()
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val submitCurrentDraft = {
        val clean = promptDraft.trim()
        if (clean.isNotEmpty() && !isBusy) {
            promptDraft = ""
            onSendPrompt(clean)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 640.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Momi AI Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Always-on Creator · Captions · Studio help",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close assistant"
                    )
                }
            }

            // AI Status Diagnostic Pill + Optional Gemini Key Toggle
            val statusBg = when (aiStatus.state) {
                GoogleAiHealthState.READY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                GoogleAiHealthState.CHECKING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                GoogleAiHealthState.MISSING_KEY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                GoogleAiHealthState.ERROR -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            }
            val statusTint = when (aiStatus.state) {
                GoogleAiHealthState.READY -> MaterialTheme.colorScheme.primary
                GoogleAiHealthState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
                GoogleAiHealthState.MISSING_KEY -> MaterialTheme.colorScheme.secondary
                GoogleAiHealthState.ERROR -> MaterialTheme.colorScheme.tertiary
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .testTag("google_ai_status_card")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCheckingStatus || aiStatus.state == GoogleAiHealthState.CHECKING) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = statusTint
                            )
                        } else {
                            Icon(
                                imageVector = if (aiStatus.state == GoogleAiHealthState.READY) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.ErrorOutline
                                },
                                contentDescription = aiStatus.label,
                                tint = statusTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCheckingStatus) "Checking AI status..." else aiStatus.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = aiStatus.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showKeyEditor = !showKeyEditor },
                            modifier = Modifier.testTag("ai_key_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = "Configure Gemini Key",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (savedApiKey.isNotBlank()) "Key" else "Add Key",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = onCheckStatus,
                            enabled = !isCheckingStatus,
                            modifier = Modifier.testTag("ai_check_status_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Test AI status",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showKeyEditor) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKeyDraft,
                                onValueChange = { apiKeyDraft = it },
                                placeholder = { Text("Paste optional Gemini API key...") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_gemini_key_input")
                            )
                            Button(
                                onClick = {
                                    onSaveApiKey(apiKeyDraft.trim())
                                    showKeyEditor = false
                                    onCheckStatus()
                                },
                                modifier = Modifier.testTag("ai_save_key_button")
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QuickAssistantPrompts) { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !isBusy) {
                                onSendPrompt(suggestion)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item(key = "assistant_welcome") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Ask me anything about creating posts, growing Pages & Groups, playing/uploading clips, sending media messages, or selling on Momi Marketplace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    items(messages.filter { it.text.isNotBlank() }, key = { it.id }) { turn ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            turn.isError -> MaterialTheme.colorScheme.errorContainer
                                            turn.isUser -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(horizontal = 13.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = turn.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        turn.isError -> MaterialTheme.colorScheme.onErrorContainer
                                        turn.isUser -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                if (isBusy) {
                    item(key = "assistant_typing") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    placeholder = { Text("Ask Momi Assistant...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCurrentDraft() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_assistant_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                val canSend = promptDraft.isNotBlank() && !isBusy
                IconButton(
                    onClick = submitCurrentDraft,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("ai_assistant_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send question",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
