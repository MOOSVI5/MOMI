package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.data.model.MessageThread
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import kotlinx.coroutines.flow.Flow

@Composable
fun MessagesScreen(
    currentUserId: String,
    profile: UserProfile?,
    allCreators: List<UserProfile>,
    threads: List<MessageThread>,
    initialThreadId: String? = null,
    observeChatMessages: (String) -> Flow<List<ChatMessage>>,
    onStartChat: (UserProfile, (MessageThread) -> Unit) -> Unit,
    onClearUnread: (MessageThread) -> Unit,
    onSendMessage: (MessageThread, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeThreadId by remember(initialThreadId) { mutableStateOf(initialThreadId) }
    var fallbackThread by remember { mutableStateOf<MessageThread?>(null) }

    BackHandler {
        if (activeThreadId != null) {
            activeThreadId = null
            fallbackThread = null
        } else {
            onBack()
        }
    }

    val openThread = activeThreadId?.let { id ->
        threads.firstOrNull { it.id == id } ?: fallbackThread?.takeIf { it.id == id }
    }

    if (openThread != null) {
        LaunchedEffect(openThread.id, openThread.unreadUid, openThread.unreadCount) {
            if (openThread.isUnreadFor(currentUserId)) {
                onClearUnread(openThread)
            }
        }
        val messages by remember(openThread.id) {
            observeChatMessages(openThread.id)
        }.collectAsStateWithLifecycle(initialValue = emptyList())

        ChatThreadView(
            currentUserId = currentUserId,
            thread = openThread,
            messages = messages,
            onSend = { text -> onSendMessage(openThread, text) },
            onBack = {
                activeThreadId = null
                fallbackThread = null
            },
            modifier = modifier
        )
    } else {
        val otherCreators = remember(allCreators, currentUserId) {
            allCreators.filter { it.userId != currentUserId }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = profile?.username ?: "Messages",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Active Momi Creators Row — tap any creator to start or resume a 1:1 DM
                item(key = "creators_row") {
                    Column {
                        Text(
                            text = "People on Momi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "own_note") {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(74.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "Active",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LumaAvatar(
                                        username = profile?.username ?: "you",
                                        avatarKey = profile?.avatarKey ?: "avatar_aria",
                                        size = 58.dp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            items(otherCreators, key = { "creator_${it.userId}" }) { creator ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(74.dp)
                                        .clickable {
                                            onStartChat(creator) { opened ->
                                                fallbackThread = opened
                                                activeThreadId = opened.id
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "Message",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box {
                                        LumaAvatar(
                                            username = creator.username,
                                            avatarKey = creator.avatarKey,
                                            size = 58.dp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = creator.displayName.split(" ").firstOrNull().orEmpty()
                                            .ifBlank { creator.username },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "messages_header") {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (threads.isEmpty()) {
                    item(key = "empty_threads") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No conversations yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (otherCreators.isEmpty()) {
                                    "Sign in on another phone to start messaging creators in real time."
                                } else {
                                    "Tap any creator above to start a real-time chat."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(threads, key = { it.id }) { thread ->
                        val peerUsername = thread.peerUsernameFor(currentUserId)
                        val peerDisplayName = thread.peerDisplayNameFor(currentUserId)
                        val peerAvatarKey = thread.peerAvatarKeyFor(currentUserId)
                        val isUnread = thread.isUnreadFor(currentUserId)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fallbackThread = thread
                                    activeThreadId = thread.id
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("message_thread_${thread.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                LumaAvatar(
                                    username = peerUsername,
                                    avatarKey = peerAvatarKey,
                                    size = 54.dp,
                                    hasActiveStory = true
                                )
                                if (thread.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${thread.lastMessage} · ${thread.timeLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isUnread) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isUnread) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadView(
    currentUserId: String,
    thread: MessageThread,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    val peerUsername = thread.peerUsernameFor(currentUserId)
    val peerDisplayName = thread.peerDisplayNameFor(currentUserId)
    val peerAvatarKey = thread.peerAvatarKeyFor(currentUserId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to inbox",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            LumaAvatar(
                username = peerUsername,
                avatarKey = peerAvatarKey,
                size = 36.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = peerDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (thread.isOnline) "Active now · @$peerUsername" else "@$peerUsername",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (thread.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isFromMe = msg.senderUid == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isFromMe) 18.dp else 4.dp,
                                    bottomEnd = if (isFromMe) 4.dp else 18.dp
                                )
                            )
                            .background(
                                if (isFromMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message @$peerUsername...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            val canSend = draft.isNotBlank()
            IconButton(
                onClick = {
                    if (canSend) {
                        onSend(draft)
                        draft = ""
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
