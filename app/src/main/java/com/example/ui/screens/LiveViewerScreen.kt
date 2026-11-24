package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveComment
import com.example.data.model.LiveFrame
import com.example.data.model.LiveGiftItem
import com.example.data.model.LiveStream
import com.example.data.model.UserWallet
import com.example.ui.components.LumaAvatar
import com.example.ui.components.SendGiftBottomSheet
import com.example.ui.theme.SilkBlueLight
import com.example.util.MediaAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiveViewerScreen(
    stream: LiveStream?,
    liveFrame: LiveFrame?,
    comments: List<LiveComment>,
    wallet: UserWallet?,
    isSendingGift: Boolean,
    onJoin: (onJoined: (Boolean) -> Unit) -> Unit,
    onLeave: () -> Unit,
    onSendComment: (String) -> Unit,
    onSendHeart: () -> Unit,
    onSendGift: (LiveGiftItem) -> Unit,
    onOpenWallet: () -> Unit,
    onAuthorClick: (userId: String, username: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val streamId = stream?.id.orEmpty()
    var commentDraft by remember { mutableStateOf("") }
    var showGiftSheet by remember { mutableStateOf(false) }
    var joinedSuccessfully by remember(streamId) { mutableStateOf(false) }
    var activeGiftBannerText by remember { mutableStateOf<String?>(null) }
    var lastSeenGiftCommentId by remember(streamId) { mutableStateOf<String?>(null) }
    var heartBurstTick by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val latestOnJoin by rememberUpdatedState(onJoin)
    val latestOnLeave by rememberUpdatedState(onLeave)

    LaunchedEffect(streamId) {
        if (streamId.isNotBlank()) {
            latestOnJoin { joined ->
                joinedSuccessfully = joined
            }
        }
    }

    DisposableEffect(streamId) {
        onDispose {
            if (joinedSuccessfully) {
                latestOnLeave()
            }
        }
    }

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.lastIndex)
        }
    }

    val latestGiftComment = remember(comments) {
        comments.lastOrNull { it.text.startsWith("[GIFT]") }
    }
    LaunchedEffect(latestGiftComment?.id) {
        val giftComment = latestGiftComment ?: return@LaunchedEffect
        if (lastSeenGiftCommentId != giftComment.id) {
            lastSeenGiftCommentId = giftComment.id
            activeGiftBannerText = "@${giftComment.username} ${giftComment.text.removePrefix("[GIFT]").trim()}"
            delay(3500L)
            activeGiftBannerText = null
        }
    }

    var decodedFrameBitmap by remember(streamId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(liveFrame?.seq, liveFrame?.frameData, liveFrame?.cameraEnabled) {
        val camOn = liveFrame?.cameraEnabled != false
        val encoded = liveFrame?.frameData.orEmpty()
        if (!camOn) {
            decodedFrameBitmap = null
        } else if (encoded.isNotBlank()) {
            val bitmap = withContext(Dispatchers.Default) {
                MediaAssets.decodeBase64ToBitmap(encoded)?.asImageBitmap()
            }
            if (bitmap != null) {
                decodedFrameBitmap = bitmap
            }
        }
    }

    if (showGiftSheet && stream != null) {
        SendGiftBottomSheet(
            recipientUsername = stream.username,
            isLiveContext = true,
            wallet = wallet,
            isProcessing = isSendingGift,
            onSendGift = { gift ->
                onSendGift(gift)
                showGiftSheet = false
            },
            onOpenWallet = {
                showGiftSheet = false
                onOpenWallet()
            },
            onDismiss = { showGiftSheet = false }
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "viewerLivePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "viewerPulse"
    )

    val showingVideoFrame = stream != null &&
        stream.isLive &&
        liveFrame?.cameraEnabled != false &&
        decodedFrameBitmap != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101B2D),
                        Color(0xFF1C1322),
                        Color(0xFF0A0D14)
                    )
                )
            )
    ) {
        if (showingVideoFrame && decodedFrameBitmap != null) {
            Image(
                bitmap = decodedFrameBitmap!!,
                contentDescription = "Live broadcast from @${stream?.username ?: "creator"}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live_viewer_video_frame")
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
        }

        // Center Stage (interactive Live Studio Stage when no camera frame is active, or ended view)
        if (!showingVideoFrame) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (stream == null || !stream.isLive) {
                    Text(
                        text = "Live broadcast ended",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This creator has finished their live session.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
                        Text("Back to Feed", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val isCameraPaused = liveFrame?.cameraEnabled == false
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .alpha(if (isCameraPaused) 0.92f else pulseAlpha)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFFD92D4E), CircleShape)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LumaAvatar(
                            username = stream.username,
                            avatarKey = stream.avatarKey,
                            avatarData = stream.avatarData,
                            avatarUrl = stream.avatarUrl,
                            size = 102.dp,
                            hasActiveStory = true
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFD92D4E))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE STUDIO STAGE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ViewerStudioEqualizerBars(isActive = liveFrame?.micMuted != true)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stream.title.ifBlank { "@${stream.username} Live" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCameraPaused) Icons.Filled.VideocamOff else Icons.Filled.Sensors,
                            contentDescription = null,
                            tint = SilkBlueLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCameraPaused) {
                                "Broadcaster camera paused · Chat & gifts live"
                            } else {
                                "Live Audio & Studio Stage Active"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFD92D4E).copy(alpha = 0.9f))
                                .clickable {
                                    heartBurstTick += 1
                                    onSendHeart()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Send heart",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Send Heart",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.92f))
                                .clickable { showGiftSheet = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CardGiftcard,
                                contentDescription = "Send gift",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Send Gift",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (stream != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAuthorClick(stream.userId, stream.username) }
                            .padding(vertical = 4.dp)
                    ) {
                        LumaAvatar(
                            username = stream.username,
                            avatarKey = stream.avatarKey,
                            avatarData = stream.avatarData,
                            avatarUrl = stream.avatarUrl,
                            size = 40.dp,
                            hasActiveStory = stream.isLive
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stream.username,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (stream.authorVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified",
                                        tint = SilkBlueLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (stream.isLive) Color(0xFFD92D4E) else Color.Gray)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (stream.isLive) "LIVE" else "ENDED",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = "Live viewers",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = MediaAssets.formatCompactCount(stream.viewerCount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.28f))
                                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CardGiftcard,
                                        contentDescription = "Stream gifts credits",
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${MediaAssets.formatCompactCount(stream.giftsCredits)} cr",
                                        color = Color(0xFFFFE082),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Momi Live",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_live_viewer_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close live room",
                        tint = Color.White
                    )
                }
            }

            if (stream != null && stream.isLive && liveFrame?.micMuted == true) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MicOff,
                            contentDescription = "Host mic muted",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Host mic muted",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = activeGiftBannerText != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeGiftBannerText?.let { banner ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF9A3412).copy(alpha = 0.9f),
                        modifier = Modifier
                            .border(1.dp, Color(0xFFFBBF24), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CardGiftcard,
                                contentDescription = null,
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = banner,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Chat, Gift & Heart Bar
        if (stream != null && stream.isLive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (comments.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            val isGiftAlert = comment.text.startsWith("[GIFT]")
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isGiftAlert) Color(0xFF7C2D12).copy(alpha = 0.78f)
                                        else Color.Black.copy(alpha = 0.5f)
                                    )
                                    .then(
                                        if (isGiftAlert) {
                                            Modifier.border(1.dp, Color(0xFFFBBF24), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LumaAvatar(
                                    username = comment.username,
                                    avatarKey = comment.avatarKey,
                                    size = 24.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${comment.username}  ",
                                    color = if (isGiftAlert) Color(0xFFFDE68A) else SilkBlueLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isGiftAlert) comment.text.removePrefix("[GIFT]").trim() else comment.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isGiftAlert) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text("Join live chat...", color = Color.White.copy(alpha = 0.65f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SilkBlueLight,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_comment_input")
                    )
                    IconButton(
                        onClick = {
                            if (commentDraft.isNotBlank()) {
                                onSendComment(commentDraft)
                                commentDraft = ""
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_live_comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send comment",
                            tint = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.9f))
                            .clickable { showGiftSheet = true }
                            .padding(horizontal = 11.dp, vertical = 10.dp)
                            .testTag("open_live_gift_sheet_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CardGiftcard,
                            contentDescription = "Send credit gift",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gift",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFD92D4E).copy(alpha = 0.85f))
                            .clickable {
                                heartBurstTick += 1
                                onSendHeart()
                            }
                            .padding(horizontal = 11.dp, vertical = 10.dp)
                            .testTag("send_live_heart_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Send heart",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = MediaAssets.formatCompactCount(stream.likesCount),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = heartBurstTick > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 116.dp)
        ) {
            LaunchedEffect(heartBurstTick) {
                delay(900L)
                heartBurstTick = 0
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(28.dp))
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF85A1), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ViewerStudioEqualizerBars(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "viewerLiveEq")
    val p1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
        label = "veq1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(510), RepeatMode.Reverse),
        label = "veq2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(tween(370), RepeatMode.Reverse),
        label = "veq3"
    )
    val levels = if (isActive) listOf(p1, p2, p3, (p1 + p2) * 0.5f, p2) else listOf(0.18f, 0.18f, 0.18f, 0.18f, 0.18f)
    Canvas(modifier = modifier.size(width = 76.dp, height = 28.dp)) {
        val barWidth = size.width / 9f
        val gap = barWidth
        levels.forEachIndexed { idx, level ->
            val barH = (size.height * level).coerceAtLeast(size.height * 0.18f)
            val x = idx * (barWidth + gap)
            drawRoundRect(
                color = if (isActive) SilkBlueLight else Color.White.copy(alpha = 0.45f),
                topLeft = Offset(x, (size.height - barH) / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
