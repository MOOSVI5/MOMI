package com.example.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.Mrec
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "MomiStartIo"

data class StartIoDiagnostics(
    val sdkInitialized: Boolean = false,
    val sdkStatus: String = "Initializing SDK...",
    val nativeStatus: String = "Waiting for SDK",
    val mrecStatus: String = "Standby",
    val bannerStatus: String = "Waiting for SDK",
    val interstitialStatus: String = "Waiting for SDK"
)

object MomiStartIoSession {
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _diagnostics = MutableStateFlow(StartIoDiagnostics())
    val diagnostics: StateFlow<StartIoDiagnostics> = _diagnostics.asStateFlow()

    fun markInitialized(source: String = "SDK ready") {
        _isInitialized.value = true
        _diagnostics.update { it.copy(sdkInitialized = true, sdkStatus = source) }
    }

    fun scheduleFallbackReady(delayMs: Long = 2000L) {
        if (_isInitialized.value) return
        Handler(Looper.getMainLooper()).postDelayed({
            if (!_isInitialized.value) {
                markInitialized("SDK ready (fallback)")
            }
        }, delayMs)
    }

    fun updateNative(status: String) {
        _diagnostics.update { it.copy(nativeStatus = status) }
    }

    fun updateMrec(status: String) {
        _diagnostics.update { it.copy(mrecStatus = status) }
    }

    fun updateBanner(status: String) {
        _diagnostics.update { it.copy(bannerStatus = status) }
    }

    fun updateInterstitial(status: String) {
        _diagnostics.update { it.copy(interstitialStatus = status) }
    }
}

object MomiInterstitialController {
    private const val MIN_COOLDOWN_MS = 4 * 60_000L

    @Volatile private var lastShownAtMs = 0L
    @Volatile private var isAdReady = false
    @Volatile private var isLoading = false
    private var cachedAd: StartAppAd? = null

    fun preload(context: Context) {
        if (isLoading || isAdReady) return
        val appContext = context.applicationContext
        if (!MomiStartIoSession.isInitialized.value) {
            MomiStartIoSession.updateInterstitial("Waiting for SDK")
            return
        }
        isLoading = true
        MomiStartIoSession.updateInterstitial("Loading...")
        runCatching {
            val ad = cachedAd ?: StartAppAd(appContext).also { cachedAd = it }
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    isLoading = false
                    isAdReady = true
                    MomiStartIoSession.updateInterstitial("Ready")
                    Log.d(TAG, "Interstitial preloaded")
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    isLoading = false
                    isAdReady = false
                    val reason = ad?.errorMessage?.takeIf { it.isNotBlank() } ?: "No fill"
                    MomiStartIoSession.updateInterstitial("No fill: $reason")
                    Log.w(TAG, "Interstitial preload skipped: $reason")
                }
            })
        }.onFailure { err ->
            isLoading = false
            isAdReady = false
            MomiStartIoSession.updateInterstitial("Error: ${err.message ?: "init"}")
        }
    }

    fun maybeShowOnMilestone(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < MIN_COOLDOWN_MS) return false
        val ad = cachedAd
        if (!isAdReady || ad == null) {
            preload(context)
            return false
        }
        return runCatching {
            lastShownAtMs = now
            isAdReady = false
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    preload(context)
                }

                override fun adDisplayed(ad: Ad?) = Unit

                override fun adClicked(ad: Ad?) = Unit

                override fun adNotDisplayed(ad: Ad?) {
                    preload(context)
                }
            })
        }.getOrElse {
            isAdReady = false
            preload(context)
            false
        }
    }
}

data class StartIoNativeAdState(
    val ads: List<NativeAdDetails> = emptyList(),
    val finishedLoading: Boolean = false
)

@Composable
fun rememberStartIoNativeAds(count: Int = 3): StartIoNativeAdState {
    val context = LocalContext.current
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    var state by remember { mutableStateOf(StartIoNativeAdState()) }

    LaunchedEffect(context, count, sdkReady) {
        if (!sdkReady) return@LaunchedEffect
        val mainHandler = Handler(Looper.getMainLooper())

        fun requestNative(attempt: Int) {
            MomiStartIoSession.updateNative(if (attempt == 1) "Requesting..." else "Retrying...")
            runCatching {
                val nativeLoader = StartAppNativeAd(context)
                val prefs = NativeAdPreferences().apply {
                    setAdsNumber(count)
                    setAutoBitmapDownload(true)
                    setImageSize(NativeAdPreferences.NativeAdBitmapSize.SIZE340X340)
                    setSecondaryImageSize(2)
                }
                nativeLoader.loadAd(prefs, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        val loaded = nativeLoader.nativeAds.orEmpty().toList()
                        Log.d(TAG, "Native ads received: ${loaded.size}")
                        MomiStartIoSession.updateNative(
                            if (loaded.isNotEmpty()) "Loaded (${loaded.size} ads)" else "Returned 0 native ads (using MREC/Banner)"
                        )
                        mainHandler.post {
                            state = StartIoNativeAdState(ads = loaded, finishedLoading = true)
                        }
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        val reason = ad?.errorMessage?.takeIf { it.isNotBlank() } ?: "No fill"
                        Log.w(TAG, "Native ad failed (attempt $attempt): $reason")
                        MomiStartIoSession.updateNative("No fill ($reason) — falling back to MREC/Banner")
                        mainHandler.post {
                            state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
                        }
                        if (attempt == 1) {
                            mainHandler.postDelayed({ requestNative(2) }, 6500L)
                        }
                    }
                })
            }.onFailure { error ->
                Log.w(TAG, "Native ad loader error: ${error.message}")
                MomiStartIoSession.updateNative("Error: ${error.message ?: "load failed"}")
                state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
            }
        }

        requestNative(attempt = 1)
    }

    return state
}

@Composable
fun MomiInlineFeedAdCard(
    slotIndex: Int = 0,
    nativeAdState: StartIoNativeAdState = StartIoNativeAdState(),
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    if (!sdkReady) return

    val nativeAd = if (slotIndex in nativeAdState.ads.indices) {
        nativeAdState.ads[slotIndex]
    } else {
        null
    }

    if (nativeAd != null) {
        StartIoNativeSponsoredPostCard(
            nativeAd = nativeAd,
            modifier = modifier
        )
    } else if (nativeAdState.finishedLoading) {
        StartIoMrecSponsoredCard(modifier = modifier)
    }
}

@Composable
private fun StartIoNativeSponsoredPostCard(
    nativeAd: NativeAdDetails,
    modifier: Modifier = Modifier
) {
    val iconBitmap = remember(nativeAd) {
        nativeAd.secondaryImageBitmap?.asImageBitmap()
    }
    val creativeBitmap = remember(nativeAd) {
        nativeAd.imageBitmap?.asImageBitmap()
    }
    var registeredView by remember(nativeAd) { mutableStateOf<View?>(null) }

    DisposableEffect(nativeAd, registeredView) {
        onDispose {
            runCatching { nativeAd.unregisterView() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_feed_ad_card")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header mimicking a genuine Momi creator post
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            iconBitmap != null -> Image(
                                bitmap = iconBitmap,
                                contentDescription = nativeAd.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            !nativeAd.secondaryImageUrl.isNullOrBlank() -> AsyncImage(
                                model = nativeAd.secondaryImageUrl,
                                contentDescription = nativeAd.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> Icon(
                                imageVector = Icons.Outlined.Campaign,
                                contentDescription = "Sponsored",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nativeAd.title?.ifBlank { "Featured Partner" } ?: "Featured Partner",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Sponsored",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (nativeAd.rating > 0f) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format("%.1f", nativeAd.rating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (!nativeAd.category.isNullOrBlank()) {
                                Text(
                                    text = nativeAd.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Creative Banner Image
                when {
                    creativeBitmap != null -> Image(
                        bitmap = creativeBitmap,
                        contentDescription = nativeAd.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                    )
                    !nativeAd.imageUrl.isNullOrBlank() -> AsyncImage(
                        model = nativeAd.imageUrl,
                        contentDescription = nativeAd.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                    )
                }

                // Description & CTA Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = nativeAd.description?.ifBlank { nativeAd.title ?: "Learn more" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Button(
                        onClick = { registeredView?.performClick() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = nativeAd.callToAction?.ifBlank { "Learn More" } ?: "Learn More",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Native Start.io impression & click tracking layer covering the post card
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        isClickable = true
                        isFocusable = true
                        nativeAd.registerViewForInteraction(this)
                        registeredView = this
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun StartIoMrecSponsoredCard(
    modifier: Modifier = Modifier
) {
    var mrecFailed by remember { mutableStateOf(false) }
    var bannerFallbackFailed by remember { mutableStateOf(false) }
    var mrecRef by remember { mutableStateOf<Mrec?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (mrecFailed && bannerFallbackFailed) return

    if (mrecFailed) {
        StartIoStandardFeedBannerCard(
            onFailed = { bannerFallbackFailed = true },
            modifier = modifier
        )
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mrecRef?.hideBanner() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_feed_ad_card")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Sponsored",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Partner Showcase",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = "Sponsored content",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Explicit 300x250dp box ensures Start.io MREC always has full non-zero dimensions
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val mrec = Mrec(ctx, object : BannerListener {
                                    override fun onReceiveAd(banner: View) {
                                        Log.d(TAG, "MREC ad received")
                                        MomiStartIoSession.updateMrec("Loaded (300x250)")
                                        mainHandler.post {
                                            mrecFailed = false
                                            runCatching { (banner as? Mrec)?.showBanner() }
                                        }
                                    }

                                    override fun onFailedToReceiveAd(banner: View?) {
                                        Log.w(TAG, "MREC ad failed; falling back to 320x50 banner")
                                        MomiStartIoSession.updateMrec("No fill — trying 320x50 Banner")
                                        mainHandler.post { mrecFailed = true }
                                    }

                                    override fun onImpression(banner: View?) = Unit

                                    override fun onClick(banner: View?) = Unit
                                })
                                mrecRef = mrec
                                addView(
                                    mrec,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        Gravity.CENTER
                                    )
                                )
                                post {
                                    MomiStartIoSession.updateMrec("Requesting 300x250...")
                                    runCatching { mrec.loadAd(300, 250) }
                                }
                            }
                        },
                        modifier = Modifier
                            .width(300.dp)
                            .height(250.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun MomiAnchoredExploreBanner(
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    var adFailed by remember { mutableStateOf(false) }
    var bannerRef by remember { mutableStateOf<Banner?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (!sdkReady || adFailed) return

    DisposableEffect(Unit) {
        onDispose {
            runCatching { bannerRef?.hideBanner() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("explore_ad_banner"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val banner = Banner(ctx, object : BannerListener {
                            override fun onReceiveAd(banner: View) {
                                Log.d(TAG, "Explore banner ad received")
                                MomiStartIoSession.updateBanner("Loaded (320x50)")
                                mainHandler.post {
                                    adFailed = false
                                    runCatching { (banner as? Banner)?.showBanner() }
                                }
                            }

                            override fun onFailedToReceiveAd(banner: View?) {
                                Log.w(TAG, "Explore banner failed to receive")
                                MomiStartIoSession.updateBanner("No fill (Explore 320x50)")
                                mainHandler.post { adFailed = true }
                            }

                            override fun onImpression(banner: View?) = Unit

                            override fun onClick(banner: View?) = Unit
                        })
                        bannerRef = banner
                        addView(
                            banner,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                        post {
                            MomiStartIoSession.updateBanner("Requesting 320x50...")
                            runCatching { banner.loadAd(320, 50) }
                        }
                    }
                },
                modifier = Modifier
                    .width(320.dp)
                    .height(50.dp)
            )
        }
    }
}

@Composable
private fun StartIoStandardFeedBannerCard(
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bannerRef by remember { mutableStateOf<Banner?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { bannerRef?.hideBanner() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_feed_ad_card")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sponsored",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = "Sponsored banner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val banner = Banner(ctx, object : BannerListener {
                                    override fun onReceiveAd(banner: View) {
                                        Log.d(TAG, "Feed fallback banner received")
                                        MomiStartIoSession.updateBanner("Loaded (Feed 320x50)")
                                        mainHandler.post {
                                            runCatching { (banner as? Banner)?.showBanner() }
                                        }
                                    }

                                    override fun onFailedToReceiveAd(banner: View?) {
                                        MomiStartIoSession.updateBanner("No fill (Feed 320x50)")
                                        mainHandler.post(onFailed)
                                    }

                                    override fun onImpression(banner: View?) = Unit

                                    override fun onClick(banner: View?) = Unit
                                })
                                bannerRef = banner
                                addView(
                                    banner,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        Gravity.CENTER
                                    )
                                )
                                post {
                                    MomiStartIoSession.updateBanner("Requesting Feed 320x50...")
                                    runCatching { banner.loadAd(320, 50) }
                                }
                            }
                        },
                        modifier = Modifier
                            .width(320.dp)
                            .height(50.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MomiMarketplaceBanner(
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    var adFailed by remember { mutableStateOf(false) }
    var bannerRef by remember { mutableStateOf<Banner?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (!sdkReady || adFailed) return

    DisposableEffect(Unit) {
        onDispose {
            runCatching { bannerRef?.hideBanner() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .border(0.75.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("marketplace_ad_banner"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Sponsored Deal",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B6B6B)
            )
            Icon(
                imageVector = Icons.Outlined.Campaign,
                contentDescription = "Sponsored partner deal",
                tint = Color(0xFF3665F3),
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val banner = Banner(ctx, object : BannerListener {
                            override fun onReceiveAd(banner: View) {
                                MomiStartIoSession.updateBanner("Loaded (Market 320x50)")
                                mainHandler.post {
                                    adFailed = false
                                    runCatching { (banner as? Banner)?.showBanner() }
                                }
                            }

                            override fun onFailedToReceiveAd(banner: View?) {
                                MomiStartIoSession.updateBanner("No fill (Market 320x50)")
                                mainHandler.post { adFailed = true }
                            }

                            override fun onImpression(banner: View?) = Unit

                            override fun onClick(banner: View?) = Unit
                        })
                        bannerRef = banner
                        addView(
                            banner,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                        post {
                            runCatching { banner.loadAd(320, 50) }
                        }
                    }
                },
                modifier = Modifier
                    .width(320.dp)
                    .height(50.dp)
            )
        }
    }
}

