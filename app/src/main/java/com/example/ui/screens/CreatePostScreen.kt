package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MomiGroup
import com.example.data.model.MomiPage
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiMusicAudioPlayer
import com.example.ui.components.MomiVideoPlayer
import com.example.ui.components.PostMediaImage
import com.example.ui.components.StoryColorBackground
import com.example.ui.components.StoryOverlayPlacementBox
import com.example.util.MediaAssets
import com.example.util.StoryBackgroundCatalog
import com.example.util.StoryTextOverlayStyle
import com.example.util.StudioMusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val PostCategories = listOf("Travel", "Architecture", "Coffee", "Night", "Minimal")
private val AspectOptions = listOf("1:1" to 1f, "4:5" to 0.8f, "16:9" to 1.777f)
private val TrendingHashtagSuggestions = listOf(
    "momi", "ootd", "travel", "coffee", "streetstyle",
    "minimal", "nightvibes", "architecture", "aesthetic", "portrait"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    initialModeStory: Boolean = false,
    creators: List<UserProfile> = emptyList(),
    pages: List<MomiPage> = emptyList(),
    groups: List<MomiGroup> = emptyList(),
    initialPageId: String? = null,
    initialGroupId: String? = null,
    initialCommunityName: String = "",
    currentUserId: String = "",
    onClose: () -> Unit,
    onPublishPost: (
        imageKey: String,
        localImagePath: String?,
        localExtraImagePaths: List<String>,
        mediaType: String,
        localVideoPath: String?,
        videoDurationMs: Long,
        audioTitle: String,
        localAudioPath: String?,
        filterTag: String,
        aspectRatio: String,
        taggedUsernames: List<String>,
        hashtags: List<String>,
        caption: String,
        location: String,
        category: String,
        isReel: Boolean,
        pageId: String?,
        groupId: String?,
        communityName: String,
        onFinished: (Boolean) -> Unit
    ) -> Unit,
    onPublishStory: (
        imageKey: String,
        localImagePath: String?,
        mediaType: String,
        localVideoPath: String?,
        videoDurationMs: Long,
        audioTitle: String,
        localAudioPath: String?,
        caption: String,
        backgroundColorKey: String,
        textOverlay: String,
        textOverlayStyle: String,
        textOverlayPositionY: Float,
        onFinished: (Boolean) -> Unit
    ) -> Unit,
    onGoLiveClick: () -> Unit = {},
    publishingStatusLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isStoryMode by remember { mutableStateOf(initialModeStory) }
    var selectedImageKey by remember { mutableStateOf("img_post_coastal") }
    var localImagePath by remember { mutableStateOf<String?>(null) }
    var localExtraImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var mediaType by remember { mutableStateOf("IMAGE") }
    var localVideoPath by remember { mutableStateOf<String?>(null) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var autoExtractedThumbnailPath by remember { mutableStateOf<String?>(null) }
    var mediaValidationMessage by remember { mutableStateOf<String?>(null) }
    var selectedStoryBgKey by remember { mutableStateOf("") }
    var storyTextOverlay by remember { mutableStateOf("") }
    var storyOverlayStyle by remember { mutableStateOf(StoryTextOverlayStyle.PILL_DARK.code) }
    var storyOverlayPosY by remember { mutableFloatStateOf(0.5f) }
    var selectedAudioTitle by remember { mutableStateOf("") }
    var localAudioPath by remember { mutableStateOf<String?>(null) }
    var previewMuted by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("NONE") }
    var selectedAspectRatio by remember { mutableStateOf("1:1") }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var showAdjustSliders by remember { mutableStateOf(false) }
    var taggedUsernames by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagSearchQuery by remember { mutableStateOf("") }
    var selectedHashtags by remember { mutableStateOf<List<String>>(emptyList()) }
    var customHashtagInput by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Travel") }
    var shareAsClip by remember { mutableStateOf(false) }
    var selectedPageId by remember { mutableStateOf(initialPageId?.takeIf { it.isNotBlank() }) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId?.takeIf { it.isNotBlank() }) }
    var selectedCommunityName by remember { mutableStateOf(initialCommunityName) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    val availableCreators = remember(creators, currentUserId) {
        creators.filter { it.userId != currentUserId && it.username.isNotBlank() }
    }
    val liveColorFilter = remember(selectedFilter, brightness, contrast, warmth) {
        MediaAssets.colorFilterForEdit(
            filterTag = selectedFilter,
            brightness = brightness,
            contrast = contrast,
            warmth = warmth
        )
    }
    val previewAspectFloat = when {
        isStoryMode -> 0.68f
        mediaType == "IMAGE" -> AspectOptions.firstOrNull { it.first == selectedAspectRatio }?.second ?: 1f
        else -> 1f
    }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessingMedia = true
            coroutineScope.launch {
                val savedPaths = withContext(Dispatchers.IO) {
                    uris.take(5).mapNotNull { uri ->
                        MediaAssets.copyPickedImageToInternalStorage(context, uri)
                    }
                }
                if (savedPaths.isNotEmpty()) {
                    selectedStoryBgKey = ""
                    localImagePath = savedPaths.first()
                    localExtraImagePaths = savedPaths.drop(1)
                    localVideoPath = null
                    videoDurationMs = 0L
                    autoExtractedThumbnailPath = null
                    mediaValidationMessage = null
                    mediaType = "IMAGE"
                }
                isProcessingMedia = false
            }
        }
    }

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedImageToInternalStorage(context, uri)
                }
                if (savedPath != null) {
                    selectedStoryBgKey = ""
                    localImagePath = savedPath
                    localExtraImagePaths = emptyList()
                    localVideoPath = null
                    videoDurationMs = 0L
                    autoExtractedThumbnailPath = null
                    mediaValidationMessage = null
                    mediaType = "IMAGE"
                }
                isProcessingMedia = false
            }
        }
    }

    val videoThumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedImageToInternalStorage(context, uri)
                }
                if (savedPath != null) {
                    localImagePath = savedPath
                    mediaValidationMessage = null
                }
                isProcessingMedia = false
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            coroutineScope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedVideoToInternalStorage(context, uri)
                }
                if (extracted != null) {
                    if (extracted.durationMs > MediaAssets.MAX_POST_VIDEO_DURATION_MS) {
                        mediaValidationMessage =
                            "Video is ${MediaAssets.formatDuration(extracted.durationMs)} — Momi supports videos up to 80:00 (80 minutes). Please select a shorter video."
                    } else {
                        mediaValidationMessage = null
                        selectedStoryBgKey = ""
                        localVideoPath = extracted.localVideoPath
                        localImagePath = extracted.thumbnailPath
                        autoExtractedThumbnailPath = extracted.thumbnailPath
                        localExtraImagePaths = emptyList()
                        videoDurationMs = extracted.durationMs
                        mediaType = "VIDEO"
                        shareAsClip = true
                    }
                } else {
                    mediaValidationMessage = "Could not load selected video file. Check storage access or choose another video."
                }
                isProcessingMedia = false
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            coroutineScope.launch {
                val copied = withContext(Dispatchers.IO) {
                    StudioMusicCatalog.copyPickedAudioToInternalStorage(context, uri)
                }
                if (copied != null) {
                    localAudioPath = copied.first
                    selectedAudioTitle = copied.second
                }
                isProcessingMedia = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close composer",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = if (isStoryMode) "New Story" else "New Post",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false
            )

            val publishContainerColor by animateColorAsState(
                targetValue = if (isPublishing) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "publish_btn_color"
            )
            val publishButtonScale by animateFloatAsState(
                targetValue = if (isPublishing) 0.96f else 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "publish_btn_scale"
            )

            Button(
                enabled = !isProcessingMedia && !isPublishing,
                onClick = {
                    isPublishing = true
                    if (isStoryMode) {
                        val usingColorCanvas = selectedStoryBgKey.isNotBlank()
                        onPublishStory(
                            selectedImageKey,
                            if (usingColorCanvas) null else localImagePath,
                            if (usingColorCanvas) "IMAGE" else mediaType,
                            if (usingColorCanvas) null else localVideoPath,
                            if (usingColorCanvas) 0L else videoDurationMs,
                            selectedAudioTitle,
                            localAudioPath,
                            caption,
                            selectedStoryBgKey,
                            storyTextOverlay,
                            storyOverlayStyle,
                            storyOverlayPosY
                        ) { _ ->
                            isPublishing = false
                        }
                    } else {
                        val finalCaption = buildString {
                            val base = caption.ifBlank { "Captured on Momi" }.trim()
                            append(base)
                            val missingTags = selectedHashtags.filter { tag ->
                                !base.contains("#$tag", ignoreCase = true)
                            }
                            if (missingTags.isNotEmpty()) {
                                append(" ")
                                append(missingTags.joinToString(" ") { "#$it" })
                            }
                        }
                        val mergedHashtags = MediaAssets.extractHashtags(finalCaption, selectedHashtags)
                        val mergedMentions = MediaAssets.extractMentions(finalCaption, taggedUsernames)

                        coroutineScope.launch {
                            var bakedPrimary = localImagePath
                            var bakedExtras = localExtraImagePaths
                            if (mediaType == "IMAGE") {
                                val hasPixelEdit = kotlin.math.abs(brightness) >= 0.01f ||
                                    kotlin.math.abs(contrast - 1f) >= 0.01f ||
                                    kotlin.math.abs(warmth) >= 0.01f ||
                                    rotationDegrees != 0 ||
                                    selectedAspectRatio != "1:1"
                                if (hasPixelEdit) {
                                    bakedPrimary = withContext(Dispatchers.IO) {
                                        val sourceForEdit = localImagePath ?: run {
                                            val bmp = BitmapFactory.decodeResource(
                                                context.resources,
                                                MediaAssets.drawableForKey(selectedImageKey)
                                            )
                                            if (bmp != null) {
                                                val dir = File(context.cacheDir, "momi_edit_src").apply { mkdirs() }
                                                val tmp = File(dir, "preset_${UUID.randomUUID()}.jpg")
                                                tmp.outputStream().use { out ->
                                                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                                }
                                                bmp.recycle()
                                                tmp.absolutePath
                                            } else null
                                        }
                                        MediaAssets.renderEditedImageToFile(
                                            context = context,
                                            sourcePath = sourceForEdit,
                                            brightness = brightness,
                                            contrast = contrast,
                                            warmth = warmth,
                                            rotationDegrees = rotationDegrees,
                                            targetAspectRatio = selectedAspectRatio
                                        )
                                    }
                                    if (localExtraImagePaths.isNotEmpty()) {
                                        bakedExtras = withContext(Dispatchers.IO) {
                                            localExtraImagePaths.map { extraPath ->
                                                MediaAssets.renderEditedImageToFile(
                                                    context = context,
                                                    sourcePath = extraPath,
                                                    brightness = brightness,
                                                    contrast = contrast,
                                                    warmth = warmth,
                                                    rotationDegrees = rotationDegrees,
                                                    targetAspectRatio = selectedAspectRatio
                                                ) ?: extraPath
                                            }
                                        }
                                    }
                                }
                            }
                            onPublishPost(
                                selectedImageKey,
                                bakedPrimary,
                                bakedExtras,
                                mediaType,
                                localVideoPath,
                                videoDurationMs,
                                selectedAudioTitle,
                                localAudioPath,
                                selectedFilter,
                                selectedAspectRatio,
                                mergedMentions,
                                mergedHashtags,
                                finalCaption,
                                location,
                                selectedCategory,
                                shareAsClip,
                                selectedPageId,
                                selectedGroupId,
                                selectedCommunityName
                            ) { _ ->
                                isPublishing = false
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = publishContainerColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = publishContainerColor.copy(alpha = 0.9f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = publishButtonScale
                        scaleY = publishButtonScale
                    }
                    .animateContentSize(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
                    .testTag("publish_post_button")
            ) {
                AnimatedContent(
                    targetState = isPublishing to isStoryMode,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "publish_btn_content"
                ) { (publishingNow, storyModeNow) ->
                    if (publishingNow) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = publishingStatusLabel?.takeIf { it.isNotBlank() }
                                    ?: if (storyModeNow) "Saving..." else "Uploading...",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Text(
                            text = if (storyModeNow) "Publish Story" else "Share",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Spacious Segmented Mode Switcher: Post | Story | Go Live
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(false to "Post", true to "Story").forEach { (storyOption, label) ->
                val active = isStoryMode == storyOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { isStoryMode = storyOption }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFD92D4E).copy(alpha = 0.14f))
                    .clickable(onClick = onGoLiveClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("go_live_mode_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = Color(0xFFD92D4E),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Go Live",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = Color(0xFFD92D4E)
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Preview Canvas
            val allSelectedPhotoPaths = remember(localImagePath, localExtraImagePaths, mediaType) {
                if (mediaType == "IMAGE" && localImagePath != null) {
                    listOfNotNull(localImagePath) + localExtraImagePaths
                } else {
                    emptyList()
                }
            }
            val hasSelectedMusic = selectedAudioTitle.isNotBlank() || !localAudioPath.isNullOrBlank()
            val showStoryColorCanvas = isStoryMode && selectedStoryBgKey.isNotBlank()
            Box(
                modifier = Modifier
                    .then(
                        if (isStoryMode) {
                            Modifier
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                                .widthIn(max = 360.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .aspectRatio(previewAspectFloat)
                                .clip(RoundedCornerShape(20.dp))
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(previewAspectFloat)
                                .clipToBounds()
                        }
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (showStoryColorCanvas) {
                    StoryColorBackground(
                        backgroundColorKey = selectedStoryBgKey,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("story_preview_color_canvas")
                    )
                } else if (mediaType == "VIDEO" && localVideoPath != null) {
                    MomiVideoPlayer(
                        mediaId = localVideoPath.orEmpty(),
                        localVideoPath = localVideoPath,
                        videoData = null,
                        videoDurationMs = videoDurationMs,
                        fallbackImageKey = selectedImageKey,
                        fallbackLocalImagePath = localImagePath,
                        fallbackImageData = null,
                        filterTag = selectedFilter,
                        isPlaying = true,
                        localAudioPath = localAudioPath,
                        audioTitle = selectedAudioTitle,
                        initialMuted = false,
                        showMuteButton = true,
                        showDurationBadge = true,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isStoryMode && videoDurationMs > 30_000L) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Crop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Long video (${MediaAssets.formatDuration(videoDurationMs)}) detected",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Auto-trim to the first 30 seconds for optimal story performance",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                                Button(
                                    onClick = {
                                        videoDurationMs = 30_000L
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("auto_trim_button")
                                ) {
                                    Text("Auto-Trim to 30s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (!isStoryMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Video ${MediaAssets.formatDuration(videoDurationMs)} • Max 80:00",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (!isStoryMode && allSelectedPhotoPaths.size > 1) {
                    val pagerState = rememberPagerState(pageCount = { allSelectedPhotoPaths.size })
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            PostMediaImage(
                                imageKey = selectedImageKey,
                                localImagePath = allSelectedPhotoPaths[page],
                                filterTag = selectedFilter,
                                customColorFilter = liveColorFilter,
                                contentDescription = "Selected photo ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = rotationDegrees.toFloat() }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Collections,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${pagerState.currentPage + 1}/${allSelectedPhotoPaths.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(allSelectedPhotoPaths.size) { idx ->
                                Box(
                                    modifier = Modifier
                                        .size(if (idx == pagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (idx == pagerState.currentPage) Color.White
                                            else Color.White.copy(alpha = 0.45f)
                                        )
                                )
                            }
                        }
                    }
                } else {
                    PostMediaImage(
                        imageKey = selectedImageKey,
                        localImagePath = localImagePath,
                        filterTag = selectedFilter,
                        customColorFilter = liveColorFilter,
                        contentDescription = "Selected post preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotationDegrees.toFloat() }
                    )
                }

                if (isStoryMode && storyTextOverlay.isNotBlank()) {
                    StoryOverlayPlacementBox(
                        textOverlay = storyTextOverlay,
                        styleCode = storyOverlayStyle,
                        positionY = storyOverlayPosY,
                        onPositionYChange = { storyOverlayPosY = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 36.dp)
                    )
                }

                if (isStoryMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.58f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("story_24h_badge"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Temporary 24-hour story",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "24h Temporary",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isStoryMode && taggedUsernames.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AlternateEmail,
                            contentDescription = "Tagged creators",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${taggedUsernames.size} tagged",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (mediaType != "VIDEO" && hasSelectedMusic) {
                    MomiMusicAudioPlayer(
                        mediaId = "preview_${selectedAudioTitle}_${localAudioPath.orEmpty()}",
                        localAudioPath = localAudioPath,
                        audioData = null,
                        audioTitle = selectedAudioTitle,
                        isPlaying = true,
                        isMuted = previewMuted
                    )
                    IconButton(
                        onClick = { previewMuted = !previewMuted },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = if (previewMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (previewMuted) "Unmute preview music" else "Mute preview music",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isProcessingMedia || isPublishing,
                    enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)),
                    exit = fadeOut(tween(220)),
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.52f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = when {
                                    isPublishing && isStoryMode -> "Uploading & saving story..."
                                    isPublishing -> "Publishing to Supabase..."
                                    else -> "Loading media..."
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (isStoryMode) {
                Spacer(modifier = Modifier.height(10.dp))

                // Story Background Color Palette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Background Color",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (selectedStoryBgKey.isBlank()) "Photo / Video" else StoryBackgroundCatalog.byKey(selectedStoryBgKey)?.label.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "story_bg_media") {
                        val isMediaActive = selectedStoryBgKey.isBlank()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedStoryBgKey = "" }
                                .testTag("story_bg_media")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isMediaActive) 2.5.dp else 1.dp,
                                        color = if (isMediaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = "Use photo or video background",
                                    tint = if (isMediaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Media",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMediaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(StoryBackgroundCatalog.options, key = { it.key }) { bgOpt ->
                        val active = selectedStoryBgKey == bgOpt.key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    selectedStoryBgKey = bgOpt.key
                                    mediaType = "IMAGE"
                                    localImagePath = null
                                    localVideoPath = null
                                    videoDurationMs = 0L
                                }
                                .testTag("story_bg_${bgOpt.key}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(bgOpt.startColorArgb), Color(bgOpt.endColorArgb))
                                        )
                                    )
                                    .border(
                                        width = if (active) 3.dp else 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.onBackground else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "${bgOpt.label} selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bgOpt.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Story Text Overlay Composer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Text Overlay",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (storyTextOverlay.isNotBlank()) {
                        Text(
                            text = "Drag text on preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = storyTextOverlay,
                    onValueChange = { if (it.length <= 160) storyTextOverlay = it },
                    placeholder = { Text("Add bold text on your story...") },
                    trailingIcon = {
                        if (storyTextOverlay.isNotEmpty()) {
                            IconButton(
                                onClick = { storyTextOverlay = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear text overlay"
                                )
                            }
                        }
                    },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("story_overlay_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(StoryTextOverlayStyle.entries, key = { it.code }) { styleOpt ->
                        val active = storyOverlayStyle == styleOpt.code
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { storyOverlayStyle = styleOpt.code }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("story_overlay_style_${styleOpt.code}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = styleOpt.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Position",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf("Top" to 0.24f, "Center" to 0.50f, "Bottom" to 0.76f).forEach { (posLabel, posVal) ->
                        val active = kotlin.math.abs(storyOverlayPosY - posVal) < 0.12f
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { storyOverlayPosY = posVal }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("story_overlay_pos_${posLabel.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = posLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Media Pickers (Photo & Video via Zero-Permission Android Photo Picker)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Select media",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStoryMode) "Photo or Video" else "Up to 5 Photos or 1 Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !isProcessingMedia && !isPublishing,
                        onClick = {
                            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (isStoryMode) {
                                singlePhotoPickerLauncher.launch(req)
                            } else {
                                multiPhotoPickerLauncher.launch(req)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pick_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isStoryMode) "Photo" else "Photos (1-5)",
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        enabled = !isProcessingMedia && !isPublishing,
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pick_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Video (max 80m)",
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (mediaValidationMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable { mediaValidationMessage = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mediaValidationMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (mediaType == "VIDEO" && localVideoPath != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                PostMediaImage(
                                    imageKey = selectedImageKey,
                                    localImagePath = localImagePath ?: autoExtractedThumbnailPath,
                                    filterTag = selectedFilter,
                                    contentDescription = "Video cover thumbnail",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Video Cover Thumbnail",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (localImagePath != null && localImagePath != autoExtractedThumbnailPath) {
                                        "Custom thumbnail selected"
                                    } else {
                                        "Auto frame (${MediaAssets.formatDuration(videoDurationMs)} / 80:00)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                enabled = !isProcessingMedia && !isPublishing,
                                onClick = {
                                    videoThumbnailPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("video_thumbnail_picker_button")
                            ) {
                                Text("Thumbnail", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (localImagePath != autoExtractedThumbnailPath && autoExtractedThumbnailPath != null) {
                                TextButton(
                                    onClick = { localImagePath = autoExtractedThumbnailPath },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("reset_video_thumbnail_button")
                                ) {
                                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (!isStoryMode && localExtraImagePaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(localExtraImagePaths) { idx, extraPath ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                PostMediaImage(
                                    imageKey = selectedImageKey,
                                    localImagePath = extraPath,
                                    filterTag = selectedFilter,
                                    contentDescription = "Extra photo ${idx + 2}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .clickable {
                                            localExtraImagePaths = localExtraImagePaths.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MediaAssets.presetPhotoOptions) { (key, label) ->
                    val selected = selectedStoryBgKey.isBlank() && mediaType == "IMAGE" && localImagePath == null && selectedImageKey == key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedStoryBgKey = ""
                            localImagePath = null
                            localExtraImagePaths = emptyList()
                            localVideoPath = null
                            autoExtractedThumbnailPath = null
                            mediaValidationMessage = null
                            videoDurationMs = 0L
                            mediaType = "IMAGE"
                            selectedImageKey = key
                        }
                    ) {
                        Image(
                            painter = painterResource(id = MediaAssets.drawableForKey(key)),
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (selected) Modifier.border(
                                        2.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Music & Sound Selector (Studio Tracks + Pick Song From Phone)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Music & Sound",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedAudioTitle.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                selectedAudioTitle = ""
                                localAudioPath = null
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedAudioTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove music",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "music_original") {
                    val active = selectedAudioTitle.isBlank()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedAudioTitle = ""
                                localAudioPath = null
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Original",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item(key = "music_pick_phone") {
                    val customPicked = localAudioPath != null
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (customPicked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = !isProcessingMedia && !isPublishing) {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("pick_audio_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = if (customPicked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "From Phone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (customPicked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(StudioMusicCatalog.tracks, key = { it.id }) { track ->
                    val active = localAudioPath == null && selectedAudioTitle == track.fullTitle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                localAudioPath = null
                                selectedAudioTitle = track.fullTitle
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${track.title} · ${track.mood}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isStoryMode && mediaType == "IMAGE") {
                Spacer(modifier = Modifier.height(16.dp))

                // Photo Studio Toolbar: Aspect Ratio, Rotate, and Tone Adjustments
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filters & Photo Studio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = { showAdjustSliders = !showAdjustSliders },
                        modifier = Modifier.testTag("toggle_photo_adjust_button")
                    ) {
                        Text(
                            text = if (showAdjustSliders) "Hide Adjust" else "Adjust Tone",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(MediaAssets.postFilterOptions, key = { it.tag }) { option ->
                        val active = selectedFilter == option.tag
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = option.tag }
                                .padding(2.dp)
                                .testTag("filter_option_${option.tag}")
                        ) {
                            PostMediaImage(
                                imageKey = selectedImageKey,
                                localImagePath = localImagePath,
                                filterTag = option.tag,
                                contentDescription = "${option.label} filter preview",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (active) Modifier.border(
                                            2.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectOptions.forEach { (label, _) ->
                        val active = selectedAspectRatio == label
                        Row(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedAspectRatio = label }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag("aspect_ratio_$label"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Crop,
                                contentDescription = null,
                                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { rotationDegrees = (rotationDegrees + 90) % 360 }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .testTag("rotate_photo_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RotateRight,
                            contentDescription = "Rotate 90 degrees",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (rotationDegrees == 0) "Rotate" else "${rotationDegrees}°",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (showAdjustSliders) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Brightness", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${(brightness * 100).toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = -0.25f..0.25f,
                                modifier = Modifier.testTag("edit_brightness_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Contrast", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${(contrast * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = contrast,
                                onValueChange = { contrast = it },
                                valueRange = 0.75f..1.35f,
                                modifier = Modifier.testTag("edit_contrast_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Warmth", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${(warmth * 100).toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = warmth,
                                onValueChange = { warmth = it },
                                valueRange = -0.30f..0.30f,
                                modifier = Modifier.testTag("edit_warmth_slider")
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = {
                                        brightness = 0f
                                        contrast = 1f
                                        warmth = 0f
                                        rotationDegrees = 0
                                        selectedAspectRatio = "1:1"
                                        selectedFilter = "NONE"
                                    }
                                ) {
                                    Text("Reset Edits")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caption Input
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = { Text(if (isStoryMode) "Add a story note..." else "Write a note or caption (#hashtags @mentions work)...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("caption_input_field")
            )

            if (!isStoryMode) {
                Spacer(modifier = Modifier.height(14.dp))

                // Hashtags Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hashtags",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customHashtagInput,
                        onValueChange = { customHashtagInput = it },
                        placeholder = { Text("Add #hashtag") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hashtag_input_field")
                    )
                    Button(
                        onClick = {
                            val clean = customHashtagInput.trim().removePrefix("#").lowercase()
                                .replace(Regex("[^a-z0-9_]"), "")
                            if (clean.isNotBlank() && !selectedHashtags.contains(clean)) {
                                selectedHashtags = (selectedHashtags + clean).take(15)
                            }
                            customHashtagInput = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_hashtag_button")
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allChips = (selectedHashtags + TrendingHashtagSuggestions).distinct()
                    items(allChips, key = { "hash_opt_$it" }) { tag ->
                        val active = selectedHashtags.contains(tag)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedHashtags = if (active) {
                                        selectedHashtags - tag
                                    } else {
                                        (selectedHashtags + tag).take(15)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("hashtag_chip_$tag")
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Tag Creators Section
                if (availableCreators.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AlternateEmail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tag Creators",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (taggedUsernames.isNotEmpty()) {
                            Text(
                                text = "${taggedUsernames.size}/10 tagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (availableCreators.size > 4) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tagSearchQuery,
                            onValueChange = { tagSearchQuery = it },
                            placeholder = { Text("Filter creators by @username") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("tag_creator_search")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val filteredCreators = remember(availableCreators, tagSearchQuery) {
                        val q = tagSearchQuery.trim().removePrefix("@")
                        if (q.isEmpty()) availableCreators else availableCreators.filter {
                            it.username.contains(q, ignoreCase = true) ||
                                it.displayName.contains(q, ignoreCase = true)
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCreators.take(20), key = { "tag_user_${it.userId}" }) { creator ->
                            val handle = creator.username.lowercase()
                            val active = taggedUsernames.contains(handle)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (active) 1.5.dp else 0.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        taggedUsernames = if (active) {
                                            taggedUsernames - handle
                                        } else {
                                            (taggedUsernames + handle).take(10)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("tag_creator_${creator.username}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LumaAvatar(
                                    username = creator.username,
                                    avatarKey = creator.avatarKey,
                                    avatarData = creator.avatarData,
                                    size = 24.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "@${creator.username}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (!isStoryMode) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = { Text("Add location") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Topic",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PostCategories) { cat ->
                        val selected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (pages.isNotEmpty() || groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Post Destination",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "dest_main_feed") {
                            val isMain = selectedPageId == null && selectedGroupId == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isMain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        selectedPageId = null
                                        selectedGroupId = null
                                        selectedCommunityName = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("dest_chip_main_feed")
                            ) {
                                Text(
                                    text = "Main Feed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMain) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(pages, key = { "dest_page_${it.id}" }) { page ->
                            val selected = selectedPageId == page.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        selectedPageId = page.id
                                        selectedGroupId = null
                                        selectedCommunityName = page.name
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Page · ${page.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(groups, key = { "dest_grp_${it.id}" }) { group ->
                            val selected = selectedGroupId == group.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        selectedPageId = null
                                        selectedGroupId = group.id
                                        selectedCommunityName = group.name
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Group · ${group.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Share as Momi Clip",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Appear in the full-screen Clips feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = shareAsClip,
                        onCheckedChange = { shareAsClip = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}
