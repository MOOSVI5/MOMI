package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.startapp.sdk.ads.banner.bannerstandard.BannerStandard
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
private const val MAX_INLINE_WEBVIEW_MREC_SLOTS = 2

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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Wraps Start.io's internal [WebViewClient] so that if Chromium's sandboxed renderer process
 * terminates abnormally (code -1 / OOM / GPU teardown), [onRenderProcessGone] always returns true
 * and destroys the dead [WebView] rather than letting Start.io's default client return false and
 * crash the host app.
 */
private class MomiSafeWebViewClientWrapper(
    private val delegate: WebViewClient?,
    private val onRendererGone: () -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return delegate?.shouldOverrideUrlLoading(view, request)
            ?: super.shouldOverrideUrlLoading(view, request)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        @Suppress("DEPRECATION")
        return delegate?.shouldOverrideUrlLoading(view, url)
            ?: super.shouldOverrideUrlLoading(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        return delegate?.shouldInterceptRequest(view, request)
            ?: super.shouldInterceptRequest(view, request)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        @Suppress("DEPRECATION")
        return delegate?.shouldInterceptRequest(view, url)
            ?: super.shouldInterceptRequest(view, url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (delegate != null) delegate.onPageStarted(view, url, favicon)
        else super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (delegate != null) delegate.onPageFinished(view, url)
        else super.onPageFinished(view, url)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        if (delegate != null) delegate.onLoadResource(view, url)
        else super.onLoadResource(view, url)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        if (delegate != null) delegate.onPageCommitVisible(view, url)
        else super.onPageCommitVisible(view, url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (delegate != null) delegate.onReceivedError(view, request, error)
        else super.onReceivedError(view, request, error)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        @Suppress("DEPRECATION")
        if (delegate != null) delegate.onReceivedError(view, errorCode, description, failingUrl)
        else super.onReceivedError(view, errorCode, description, failingUrl)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (delegate != null) delegate.onReceivedHttpError(view, request, errorResponse)
        else super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        if (delegate != null) delegate.onReceivedSslError(view, handler, error)
        else super.onReceivedSslError(view, handler, error)
    }

    override fun onFormResubmission(view: WebView?, dontResend: Message?, resend: Message?) {
        if (delegate != null) delegate.onFormResubmission(view, dontResend, resend)
        else super.onFormResubmission(view, dontResend, resend)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        if (delegate != null) delegate.doUpdateVisitedHistory(view, url, isReload)
        else super.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
        if (delegate != null) delegate.onScaleChanged(view, oldScale, newScale)
        else super.onScaleChanged(view, oldScale, newScale)
    }

    override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
        return delegate?.shouldOverrideKeyEvent(view, event)
            ?: super.shouldOverrideKeyEvent(view, event)
    }

    override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
        if (delegate != null) delegate.onUnhandledKeyEvent(view, event)
        else super.onUnhandledKeyEvent(view, event)
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?
    ) {
        if (delegate != null) delegate.onReceivedHttpAuthRequest(view, handler, host, realm)
        else super.onReceivedHttpAuthRequest(view, handler, host, realm)
    }

    override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        if (delegate != null) delegate.onReceivedClientCertRequest(view, request)
        else super.onReceivedClientCertRequest(view, request)
    }

    override fun onReceivedLoginRequest(
        view: WebView?,
        realm: String?,
        account: String?,
        args: String?
    ) {
        if (delegate != null) delegate.onReceivedLoginRequest(view, realm, account, args)
        else super.onReceivedLoginRequest(view, realm, account, args)
    }

    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && delegate != null) {
            delegate.onSafeBrowsingHit(view, request, threatType, callback)
        } else {
            super.onSafeBrowsingHit(view, request, threatType, callback)
        }
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?
    ): Boolean {
        val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() else null
        Log.w(TAG, "Ad WebView renderer terminated (didCrash=$didCrash); recovering safely")
        if (view != null) {
            destroyWebViewSafely(view)
        }
        Handler(Looper.getMainLooper()).post { onRendererGone() }
        return true
    }
}

private fun wrapSingleWebViewClient(webView: WebView?, onRendererGone: () -> Unit) {
    if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        val current = webView.webViewClient
        if (current !is MomiSafeWebViewClientWrapper) {
            webView.webViewClient = MomiSafeWebViewClientWrapper(current, onRendererGone)
        }
    }
}

internal fun wrapDescendantWebViews(view: View?, onRendererGone: () -> Unit) {
    when (view) {
        is WebView -> wrapSingleWebViewClient(view, onRendererGone)
        is ViewGroup -> {
            for (i in 0 until view.childCount) {
                wrapDescendantWebViews(view.getChildAt(i), onRendererGone)
            }
        }
    }
}

private fun protectBannerStandardWebViews(
    bannerView: BannerStandard,
    onRendererGone: () -> Unit
) {
    runCatching { wrapSingleWebViewClient(bannerView.webView, onRendererGone) }
    runCatching { wrapSingleWebViewClient(bannerView.twoPartWebView, onRendererGone) }
    wrapDescendantWebViews(bannerView, onRendererGone)
}

private fun attachBannerWebViewProtector(
    bannerView: BannerStandard,
    mainHandler: Handler,
    onRendererGone: () -> Unit
) {
    protectBannerStandardWebViews(bannerView, onRendererGone)
    bannerView.post { protectBannerStandardWebViews(bannerView, onRendererGone) }
    bannerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            mainHandler.post { protectBannerStandardWebViews(bannerView, onRendererGone) }
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    })
}

private fun destroyWebViewSafely(webView: WebView?) {
    if (webView == null) return
    runCatching {
        webView.stopLoading()
        webView.onPause()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
    }
}

private fun destroyDescendantWebViews(view: View?) {
    when (view) {
        is WebView -> destroyWebViewSafely(view)
        is ViewGroup -> {
            val children = (0 until view.childCount).map { view.getChildAt(it) }
            children.forEach { destroyDescendantWebViews(it) }
        }
    }
}

internal fun disposeStartIoBannerContainer(container: FrameLayout) {
    for (i in container.childCount - 1 downTo 0) {
        val child = container.getChildAt(i)
        if (child is BannerStandard) {
            runCatching { child.hideBanner() }
            runCatching { child.setBannerListener(null) }
            runCatching { destroyWebViewSafely(child.webView) }
            runCatching { destroyWebViewSafely(child.twoPartWebView) }
        }
        destroyDescendantWebViews(child)
    }
    runCatching { container.removeAllViews() }
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
                nativeLoader.load(prefs, object : AdEventListener {
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
    } else if (nativeAdState.finishedLoading && slotIndex < MAX_INLINE_WEBVIEW_MREC_SLOTS) {
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
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var mrecFailed by remember { mutableStateOf(activity == null) }
    var bannerFallbackFailed by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (mrecFailed && bannerFallbackFailed) return

    if (mrecFailed) {
        StartIoStandardFeedBannerCard(
            onFailed = { bannerFallbackFailed = true },
            modifier = modifier
        )
        return
    }

    val hostActivity = activity ?: return

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
                                val onRendererGone = {
                                    MomiStartIoSession.updateMrec("Renderer recovered (hidden)")
                                    mrecFailed = true
                                }
                                val mrec = Mrec(hostActivity, object : BannerListener {
                                    override fun onReceiveAd(banner: View) {
                                        Log.d(TAG, "MREC ad received")
                                        MomiStartIoSession.updateMrec("Loaded (300x250)")
                                        mainHandler.post {
                                            (banner as? BannerStandard)?.let {
                                                protectBannerStandardWebViews(it, onRendererGone)
                                            }
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
                                attachBannerWebViewProtector(mrec, mainHandler, onRendererGone)
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
                        onRelease = { container ->
                            disposeStartIoBannerContainer(container)
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (!sdkReady || adFailed) return

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
                        val onRendererGone = {
                            MomiStartIoSession.updateBanner("Renderer recovered (hidden)")
                            adFailed = true
                        }
                        val hostCtx = ctx.findActivity() ?: ctx
                        val banner = Banner(hostCtx, object : BannerListener {
                            override fun onReceiveAd(banner: View) {
                                Log.d(TAG, "Explore banner ad received")
                                MomiStartIoSession.updateBanner("Loaded (320x50)")
                                mainHandler.post {
                                    (banner as? BannerStandard)?.let {
                                        protectBannerStandardWebViews(it, onRendererGone)
                                    }
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
                        attachBannerWebViewProtector(banner, mainHandler, onRendererGone)
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
                onRelease = { container ->
                    disposeStartIoBannerContainer(container)
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

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
                                val onRendererGone = {
                                    MomiStartIoSession.updateBanner("Renderer recovered (hidden)")
                                    onFailed()
                                }
                                val hostCtx = ctx.findActivity() ?: ctx
                                val banner = Banner(hostCtx, object : BannerListener {
                                    override fun onReceiveAd(banner: View) {
                                        Log.d(TAG, "Feed fallback banner received")
                                        MomiStartIoSession.updateBanner("Loaded (Feed 320x50)")
                                        mainHandler.post {
                                            (banner as? BannerStandard)?.let {
                                                protectBannerStandardWebViews(it, onRendererGone)
                                            }
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
                                attachBannerWebViewProtector(banner, mainHandler, onRendererGone)
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
                        onRelease = { container ->
                            disposeStartIoBannerContainer(container)
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (!sdkReady || adFailed) return

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
                        val onRendererGone = {
                            MomiStartIoSession.updateBanner("Renderer recovered (hidden)")
                            adFailed = true
                        }
                        val hostCtx = ctx.findActivity() ?: ctx
                        val banner = Banner(hostCtx, object : BannerListener {
                            override fun onReceiveAd(banner: View) {
                                MomiStartIoSession.updateBanner("Loaded (Market 320x50)")
                                mainHandler.post {
                                    (banner as? BannerStandard)?.let {
                                        protectBannerStandardWebViews(it, onRendererGone)
                                    }
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
                        attachBannerWebViewProtector(banner, mainHandler, onRendererGone)
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
                onRelease = { container ->
                    disposeStartIoBannerContainer(container)
                },
                modifier = Modifier
                    .width(320.dp)
                    .height(50.dp)
            )
        }
    }
}


