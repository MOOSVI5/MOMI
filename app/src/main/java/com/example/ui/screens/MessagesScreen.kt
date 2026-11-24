package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.data.model.MessageThread
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiMusicAudioPlayer
import com.example.ui.components.MomiVideoPlayer
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun MessagesScreen(
    currentUserId: String,
    profile: UserProfile?,
    allCreators: List<UserProfile>,
    threads: List<MessageThread>,
    initialThreadId: String? = null,
    isSendingMedia: Boolean = false,
    observeChatMessages: (String) -> Flow<List<ChatMessage>>,
    onStartChat: (UserProfile, (MessageThread) -> Unit) -> Unit,
    onClearUnread: (MessageThread) -> Unit,
    onSendMessage: (MessageThread, String) -> Unit,
    onSendPhoto: (MessageThread, String) -> Unit = { _, _ -> },
    onSendVideo: (MessageThread, String, Long) -> Unit = { _, _, _ -> },
    onSendAudio: (MessageThread, String, Long) -> Unit = { _, _, _ -> },
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

        val peerUid = openThread.participantUids.firstOrNull { it != currentUserId }
        val peerProfile = allCreators.firstOrNull { it.userId == peerUid }
        ChatThreadView(
            currentUserId = currentUserId,
            thread = openThread,
            peerAvatarData = peerProfile?.avatarData,
            peerAvatarUrl = peerProfile?.avatarUrl,
            messages = messages,
            isSendingMedia = isSendingMedia,
            onSend = { text -> onSendMessage(openThread, text) },
            onSendPhoto = { localPath -> onSendPhoto(openThread, localPath) },
            onSendVideo = { localPath, durationMs -> onSendVideo(openThread, localPath, durationMs) },
            onSendAudio = { localPath, durationMs -> onSendAudio(openThread, localPath, durationMs) },
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
                                        avatarData = profile?.avatarData,
                                        avatarUrl = profile?.avatarUrl,
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
                                            avatarData = creator.avatarData,
                                            avatarUrl = creator.avatarUrl,
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
                        val peerUid = thread.participantUids.firstOrNull { it != currentUserId }
                        val peerProfile = allCreators.firstOrNull { it.userId == peerUid }
                        val peerAvatarData = peerProfile?.avatarData
                        val peerAvatarUrl = peerProfile?.avatarUrl
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
                                    avatarData = peerAvatarData,
                                    avatarUrl = peerAvatarUrl,
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
    peerAvatarData: String?,
    peerAvatarUrl: String? = null,
    messages: List<ChatMessage>,
    isSendingMedia: Boolean,
    onSend: (String) -> Unit,
    onSendPhoto: (String) -> Unit,
    onSendVideo: (String, Long) -> Unit,
    onSendAudio: (String, Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var playingAudioMsgId by remember { mutableStateOf<String?>(null) }
    var playingVideoMsgId by remember { mutableStateOf<String?>(null) }
    var localMediaNotice by remember { mutableStateOf<String?>(null) }

    // Voice Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun stopAndCleanupRecorder(deleteFile: Boolean = true) {
        val currentRecorder = recorder
        recorder = null
        runCatching { currentRecorder?.stop() }
        runCatching { currentRecorder?.release() }
        if (deleteFile) {
            runCatching { recordingFile?.delete() }
            recordingFile = null
        }
        isRecording = false
        recordingElapsedMs = 0L
    }

    fun startVoiceRecording() {
        localMediaNotice = null
        val outFile = File(context.cacheDir, "voice_${UUID.randomUUID()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(22050)
                setAudioEncodingBitRate(48_000)
                setMaxDuration(45_000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            recordingFile = outFile
            recordingStartMs = System.currentTimeMillis()
            recordingElapsedMs = 0L
            isRecording = true
        }.onFailure {
            runCatching { mediaRecorder.release() }
            runCatching { outFile.delete() }
            localMediaNotice = "Microphone unavailable right now. Please try again."
        }
    }

    fun finishAndSendRecording() {
        val currentRecorder = recorder
        val outFile = recordingFile
        val duration = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(1000L)
        recorder = null
        recordingFile = null
        isRecording = false
        recordingElapsedMs = 0L

        val stoppedCleanly = runCatching {
            currentRecorder?.stop()
            currentRecorder?.release()
            outFile != null && outFile.exists() && outFile.length() > 512L
        }.getOrDefault(false)

        if (stoppedCleanly && outFile != null) {
            onSendAudio(outFile.absolutePath, duration)
        } else {
            runCatching { currentRecorder?.release() }
            runCatching { outFile?.delete() }
            localMediaNotice = "Hold or speak a little longer to send a voice note."
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordingElapsedMs = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
            if (recordingElapsedMs >= 44_500L) {
                finishAndSendRecording()
                break
            }
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndCleanupRecorder(deleteFile = true)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            localMediaNotice = "Microphone permission is needed to record voice notes."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localMediaNotice = null
            coroutineScope.launch {
                val path = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedImageToInternalStorage(context, uri)
                }
                if (path != null) {
                    onSendPhoto(path)
                } else {
                    localMediaNotice = "Could not load selected photo."
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localMediaNotice = null
            coroutineScope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedVideoToInternalStorage(context, uri)
                }
                if (extracted != null) {
                    onSendVideo(extracted.localVideoPath, extracted.durationMs)
                } else {
                    localMediaNotice = "Could not prepare selected video."
                }
            }
        }
    }

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
                avatarData = peerAvatarData,
                avatarUrl = peerAvatarUrl,
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

        if (!localMediaNotice.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = localMediaNotice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { localMediaNotice = null },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss notice",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

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
                val bubbleContainerColor = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val bubbleTextColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isFromMe) 18.dp else 4.dp,
                    bottomEnd = if (isFromMe) 4.dp else 18.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(bubbleShape)
                            .background(bubbleContainerColor)
                            .padding(
                                horizontal = if (msg.messageType == "IMAGE" || msg.messageType == "VIDEO") 6.dp else 14.dp,
                                vertical = if (msg.messageType == "IMAGE" || msg.messageType == "VIDEO") 6.dp else 10.dp
                            )
                    ) {
                        when (msg.messageType) {
                            "IMAGE" -> {
                                Column {
                                    PostMediaImage(
                                        imageKey = "img_post_coastal",
                                        localImagePath = null,
                                        imageData = msg.imageData,
                                        imageUrl = msg.mediaUrl,
                                        filterTag = "NONE",
                                        contentDescription = "Photo message",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    if (msg.text.isNotBlank() && msg.text != "Photo") {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = bubbleTextColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            "VIDEO" -> {
                                val isPlayingThisVideo = playingVideoMsgId == msg.id
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black)
                                            .clickable {
                                                playingVideoMsgId = if (isPlayingThisVideo) null else msg.id
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MomiVideoPlayer(
                                            mediaId = "chat_${msg.id}",
                                            localVideoPath = null,
                                            videoData = msg.videoData,
                                            videoUrl = msg.mediaUrl,
                                            videoDurationMs = msg.videoDurationMs,
                                            fallbackImageKey = "img_post_coastal",
                                            fallbackLocalImagePath = null,
                                            fallbackImageData = null,
                                            filterTag = "NONE",
                                            isPlaying = isPlayingThisVideo,
                                            initialMuted = false,
                                            showMuteButton = isPlayingThisVideo,
                                            showDurationBadge = true,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (!isPlayingThisVideo) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.6f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Play video message",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (msg.text.isNotBlank() && msg.text != "Video clip") {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = bubbleTextColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            "AUDIO" -> {
                                val isPlayingAudio = playingAudioMsgId == msg.id
                                if (isPlayingAudio && (!msg.audioData.isNullOrBlank() || !msg.mediaUrl.isNullOrBlank())) {
                                    MomiMusicAudioPlayer(
                                        mediaId = "voice_${msg.id}",
                                        localAudioPath = null,
                                        audioData = msg.audioData,
                                        audioUrl = msg.mediaUrl,
                                        audioTitle = "Voice note",
                                        isPlaying = true,
                                        isMuted = false
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            playingAudioMsgId = if (isPlayingAudio) null else msg.id
                                        }
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(bubbleTextColor.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlayingAudio) "Pause voice note" else "Play voice note",
                                            tint = bubbleTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = bubbleTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (msg.audioDurationMs > 0L) {
                                            MediaAssets.formatDuration(msg.audioDurationMs)
                                        } else {
                                            "Voice note"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = bubbleTextColor
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = bubbleTextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { stopAndCleanupRecorder(deleteFile = true) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel recording",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53238))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recording ${MediaAssets.formatDuration(recordingElapsedMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { finishAndSendRecording() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("send_voice_note_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send voice message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !isSendingMedia,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "Attach photo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    enabled = !isSendingMedia,
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_attach_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "Attach video",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    enabled = !isSendingMedia,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            startVoiceRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_record_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Record voice message",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
                Spacer(modifier = Modifier.width(6.dp))
                val canSend = draft.isNotBlank() && !isSendingMedia
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSend(draft)
                            draft = ""
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("send_message_button")
                ) {
                    if (isSendingMedia) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
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
    }
}
