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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.AdMonetizationConfig
import com.example.data.model.normalizeMonetagDirectUrl
import com.example.data.model.resolveGamezopLaunchUrl
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

private const val TAG = "MomiMonetization"
private const val MAX_INLINE_MREC_SLOTS = 2
private const val STARTIO_FILL_TIMEOUT_MS = 7500L

data class StartIoDiagnostics(
    val sdkInitialized: Boolean = false,
    val sdkStatus: String = "Initializing SDK...",
    val admobStatus: String = "Initializing...",
    val nativeStatus: String = "Waiting for SDK",
    val mrecStatus: String = "Standby",
    val bannerStatus: String = "Waiting for SDK",
    val monetagStatus: String = "Standby",
    val gamezopStatus: String = "Standby",
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

    fun updateAdMob(status: String) {
        _diagnostics.update { it.copy(admobStatus = status) }
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

    fun updateMonetag(status: String) {
        _diagnostics.update { it.copy(monetagStatus = status) }
    }

    fun updateGamezop(status: String) {
        _diagnostics.update { it.copy(gamezopStatus = status) }
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

private fun resolveMonetagDirectUrl(config: AdMonetizationConfig): String {
    if (!config.monetagEnabled) return ""
    val remote = normalizeMonetagDirectUrl(config.monetagDirectLinkUrl)
    if (remote.isNotEmpty()) return remote
    return normalizeMonetagDirectUrl(BuildConfig.MONETAG_DIRECT_LINK_URL)
}

private fun resolveAdMobBannerUnitId(config: AdMonetizationConfig): String =
    config.admobBannerUnitId.trim().ifBlank { BuildConfig.ADMOB_BANNER_UNIT_ID }

private fun resolveAdMobMrecUnitId(config: AdMonetizationConfig): String =
    config.admobMrecUnitId.trim().ifBlank { BuildConfig.ADMOB_MREC_UNIT_ID }

private fun resolveAdMobInterstitialUnitId(config: AdMonetizationConfig): String =
    config.admobInterstitialUnitId.trim().ifBlank { BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID }

/**
 * Measures its child with full requested constraints (so ad SDKs see non-zero dimensions
 * and actually request/render ads), while collapsing visible layout height to 0px until
 * [isVisible] flips true once an ad is actually loaded. Prevents blank dark placeholder boxes.
 */
@Composable
private fun CollapsibleMeasuredAdWrapper(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier.alpha(if (isVisible) 1f else 0f)
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = if (isVisible) (placeables.maxOfOrNull { it.height } ?: 0) else 0
        layout(width, height) {
            if (isVisible) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}

/**
 * Wraps Start.io's internal [WebViewClient] so if Chromium's renderer exits unexpectedly,
 * [onRenderProcessGone] recovers cleanly instead of crashing the host app.
 */
private class MomiSafeWebViewClientWrapper(
    private val delegate: WebViewClient?,
    private val onRendererGone: () -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        delegate?.shouldOverrideUrlLoading(view, request)
            ?: super.shouldOverrideUrlLoading(view, request)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        @Suppress("DEPRECATION")
        return delegate?.shouldOverrideUrlLoading(view, url)
            ?: super.shouldOverrideUrlLoading(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? =
        delegate?.shouldInterceptRequest(view, request)
            ?: super.shouldInterceptRequest(view, request)

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

    override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean =
        delegate?.shouldOverrideKeyEvent(view, event)
            ?: super.shouldOverrideKeyEvent(view, event)

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
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val current = webView.webViewClient
        if (current !is MomiSafeWebViewClientWrapper) {
            webView.webViewClient = MomiSafeWebViewClientWrapper(current, onRendererGone)
        }
    }
}

private fun hasRenderedAdWebContent(view: View?): Boolean {
    if (view == null || view.visibility != View.VISIBLE) return false
    var foundWebView = false
    var hasRenderedContent = false
    fun inspect(node: View) {
        when (node) {
            is WebView -> {
                foundWebView = true
                if (node.visibility == View.VISIBLE) {
                    hasRenderedContent = true
                }
            }
            is ViewGroup -> {
                for (i in 0 until node.childCount) {
                    inspect(node.getChildAt(i))
                }
            }
        }
    }
    inspect(view)
    return if (foundWebView) hasRenderedContent else true
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

private fun disposeAdMobContainer(container: FrameLayout) {
    for (i in container.childCount - 1 downTo 0) {
        val child = container.getChildAt(i)
        if (child is AdView) {
            runCatching { child.destroy() }
        }
    }
    runCatching { container.removeAllViews() }
}

object MomiInterstitialController {
    private const val MIN_COOLDOWN_MS = 4 * 60_000L

    @Volatile private var lastShownAtMs = 0L
    @Volatile private var isStartIoReady = false
    @Volatile private var isStartIoLoading = false
    @Volatile private var isAdMobLoading = false
    private var cachedAdMob: InterstitialAd? = null
    private var cachedStartIo: StartAppAd? = null

    fun preload(
        context: Context,
        config: AdMonetizationConfig = AdMonetizationConfig()
    ) {
        val appContext = context.applicationContext
        if (config.admobEnabled && cachedAdMob == null && !isAdMobLoading) {
            val unitId = resolveAdMobInterstitialUnitId(config)
            if (unitId.isNotBlank()) {
                isAdMobLoading = true
                MomiStartIoSession.updateInterstitial("Loading AdMob...")
                runCatching {
                    InterstitialAd.load(
                        appContext,
                        unitId,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                isAdMobLoading = false
                                cachedAdMob = ad
                                MomiStartIoSession.updateInterstitial("AdMob Ready")
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                isAdMobLoading = false
                                cachedAdMob = null
                                MomiStartIoSession.updateInterstitial("AdMob no fill (${error.code}) — Start.io standby")
                            }
                        }
                    )
                }.onFailure {
                    isAdMobLoading = false
                }
            }
        }

        if (!config.startIoEnabled) return
        if (isStartIoLoading || isStartIoReady) return
        if (!MomiStartIoSession.isInitialized.value) return

        isStartIoLoading = true
        runCatching {
            val ad = cachedStartIo ?: StartAppAd(appContext).also { cachedStartIo = it }
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    isStartIoLoading = false
                    isStartIoReady = true
                    if (cachedAdMob == null) {
                        MomiStartIoSession.updateInterstitial("Start.io Ready")
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    isStartIoLoading = false
                    isStartIoReady = false
                    if (cachedAdMob == null) {
                        val reason = ad?.errorMessage?.takeIf { it.isNotBlank() } ?: "No fill"
                        MomiStartIoSession.updateInterstitial("No fill: $reason")
                    }
                }
            })
        }.onFailure {
            isStartIoLoading = false
            isStartIoReady = false
        }
    }

    fun maybeShowOnMilestone(
        context: Context,
        config: AdMonetizationConfig = AdMonetizationConfig()
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < MIN_COOLDOWN_MS) return false

        val activity = context.findActivity()
        val adMobAd = cachedAdMob
        if (config.admobEnabled && adMobAd != null && activity != null) {
            lastShownAtMs = now
            cachedAdMob = null
            runCatching { adMobAd.show(activity) }
            preload(context, config)
            return true
        }

        val startIoAd = cachedStartIo
        if (!config.startIoEnabled || !isStartIoReady || startIoAd == null) {
            preload(context, config)
            return false
        }
        return runCatching {
            lastShownAtMs = now
            isStartIoReady = false
            startIoAd.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    preload(context, config)
                }

                override fun adDisplayed(ad: Ad?) = Unit
                override fun adClicked(ad: Ad?) = Unit

                override fun adNotDisplayed(ad: Ad?) {
                    preload(context, config)
                }
            })
        }.getOrElse {
            isStartIoReady = false
            preload(context, config)
            false
        }
    }
}

data class StartIoNativeAdState(
    val ads: List<NativeAdDetails> = emptyList(),
    val finishedLoading: Boolean = false
)

@Composable
fun rememberStartIoNativeAds(
    count: Int = 3,
    enabled: Boolean = true
): StartIoNativeAdState {
    val context = LocalContext.current
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    var state by remember { mutableStateOf(StartIoNativeAdState()) }

    LaunchedEffect(context, count, sdkReady, enabled) {
        if (!enabled) {
            state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
            return@LaunchedEffect
        }
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
                        MomiStartIoSession.updateNative(
                            if (loaded.isNotEmpty()) "Loaded (${loaded.size} ads)" else "0 native ads (falling back)"
                        )
                        mainHandler.post {
                            state = StartIoNativeAdState(ads = loaded, finishedLoading = true)
                        }
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        val reason = ad?.errorMessage?.takeIf { it.isNotBlank() } ?: "No fill"
                        MomiStartIoSession.updateNative("No fill ($reason) — falling back")
                        mainHandler.post {
                            state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
                        }
                        if (attempt == 1) {
                            mainHandler.postDelayed({ requestNative(2) }, 6500L)
                        }
                    }
                })
            }.onFailure { error ->
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
    monetizationConfig: AdMonetizationConfig = AdMonetizationConfig(),
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    val nativeAd = if (monetizationConfig.startIoEnabled && slotIndex in nativeAdState.ads.indices) {
        nativeAdState.ads[slotIndex]
    } else {
        null
    }

    val admobUnitId = remember(monetizationConfig) { resolveAdMobMrecUnitId(monetizationConfig) }
    val monetagUrl = remember(monetizationConfig) { resolveMonetagDirectUrl(monetizationConfig) }

    var admobExhausted by remember(monetizationConfig.admobEnabled, admobUnitId) {
        mutableStateOf(!monetizationConfig.admobEnabled || admobUnitId.isBlank())
    }
    var startIoMrecExhausted by remember(monetizationConfig.startIoEnabled) {
        mutableStateOf(!monetizationConfig.startIoEnabled)
    }

    // 1) AdMob Medium Rectangle (300x250) first in the waterfall when enabled
    if (!admobExhausted) {
        AdMobSponsoredCard(
            unitId = admobUnitId,
            adSize = AdSize.MEDIUM_RECTANGLE,
            widthDp = 300,
            heightDp = 250,
            tagPrefix = "AdMob",
            onFailedToFill = { admobExhausted = true },
            modifier = modifier
        )
        return
    }

    // 2) Start.io Native or collapsed-until-loaded MREC
    if (nativeAd != null) {
        StartIoNativeSponsoredPostCard(
            nativeAd = nativeAd,
            modifier = modifier
        )
        return
    }

    if (monetizationConfig.startIoEnabled && sdkReady && !startIoMrecExhausted && slotIndex < MAX_INLINE_MREC_SLOTS) {
        StartIoMrecSponsoredCard(
            onExhausted = { startIoMrecExhausted = true },
            modifier = modifier
        )
        return
    }

    // 3) Monetag Direct SmartLink Partner Showcase fallback (only if configured with an https:// URL)
    if (monetagUrl.isNotEmpty()) {
        MonetagPartnerShowcaseCard(
            directLinkUrl = monetagUrl,
            config = monetizationConfig,
            modifier = modifier
        )
    }
}

@Composable
private fun AdMobSponsoredCard(
    unitId: String,
    adSize: AdSize,
    widthDp: Int,
    heightDp: Int,
    tagPrefix: String,
    onFailedToFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoaded by remember(unitId) { mutableStateOf(false) }

    CollapsibleMeasuredAdWrapper(
        isVisible = isLoaded,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
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
                    SponsoredHeaderRow(label = "Partner Showcase")
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .height(heightDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    MomiStartIoSession.updateAdMob("$tagPrefix requesting...")
                                    val adView = AdView(ctx).apply {
                                        setAdSize(adSize)
                                        adUnitId = unitId
                                        adListener = object : AdListener() {
                                            override fun onAdLoaded() {
                                                isLoaded = true
                                                MomiStartIoSession.updateAdMob("$tagPrefix loaded")
                                            }

                                            override fun onAdFailedToLoad(error: LoadAdError) {
                                                isLoaded = false
                                                MomiStartIoSession.updateAdMob("No fill (${error.code}) — falling back")
                                                onFailedToFill()
                                            }
                                        }
                                    }
                                    addView(
                                        adView,
                                        FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.WRAP_CONTENT,
                                            FrameLayout.LayoutParams.WRAP_CONTENT,
                                            Gravity.CENTER
                                        )
                                    )
                                    post {
                                        runCatching { adView.loadAd(AdRequest.Builder().build()) }
                                            .onFailure {
                                                MomiStartIoSession.updateAdMob("Error: ${it.message}")
                                                onFailedToFill()
                                            }
                                    }
                                }
                            },
                            onRelease = { container -> disposeAdMobContainer(container) },
                            modifier = Modifier
                                .width(widthDp.dp)
                                .height(heightDp.dp)
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
                                        text = String.format(Locale.US, "%.1f", nativeAd.rating),
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
    onExhausted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var mrecLoaded by remember { mutableStateOf(false) }
    var mrecFailed by remember { mutableStateOf(activity == null) }
    var bannerFallbackFailed by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Timeout guard so Start.io never stalls silently
    LaunchedEffect(mrecFailed, mrecLoaded) {
        if (!mrecFailed && !mrecLoaded) {
            delay(STARTIO_FILL_TIMEOUT_MS)
            if (!mrecLoaded) {
                MomiStartIoSession.updateMrec("Timed out — trying 320x50 Banner")
                mrecFailed = true
            }
        }
    }

    LaunchedEffect(mrecFailed, bannerFallbackFailed) {
        if (mrecFailed && bannerFallbackFailed) {
            onExhausted()
        }
    }

    if (mrecFailed && bannerFallbackFailed) return

    if (mrecFailed) {
        StartIoStandardFeedBannerCard(
            onFailed = { bannerFallbackFailed = true },
            modifier = modifier
        )
        return
    }

    val hostActivity = activity ?: return

    // Only expands visible card height once onReceiveAd confirms real banner fill
    CollapsibleMeasuredAdWrapper(
        isVisible = mrecLoaded,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
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
                    SponsoredHeaderRow(label = "Partner Showcase")
                    Spacer(modifier = Modifier.height(8.dp))
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
                                        MomiStartIoSession.updateMrec("Renderer recovered")
                                        mrecLoaded = false
                                        mrecFailed = true
                                    }
                                    val mrec = Mrec(hostActivity, object : BannerListener {
                                        override fun onReceiveAd(banner: View) {
                                            mainHandler.post {
                                                (banner as? BannerStandard)?.let {
                                                    protectBannerStandardWebViews(it, onRendererGone)
                                                }
                                                runCatching { (banner as? Mrec)?.showBanner() }
                                                if (hasRenderedAdWebContent(banner)) {
                                                    MomiStartIoSession.updateMrec("Loaded (300x250)")
                                                    mrecFailed = false
                                                    mrecLoaded = true
                                                } else {
                                                    MomiStartIoSession.updateMrec("Blank MREC — falling back")
                                                    mrecLoaded = false
                                                    mrecFailed = true
                                                }
                                            }
                                        }

                                        override fun onFailedToReceiveAd(banner: View?) {
                                            MomiStartIoSession.updateMrec("No fill — trying 320x50 Banner")
                                            mainHandler.post {
                                                mrecLoaded = false
                                                mrecFailed = true
                                            }
                                        }

                                        override fun onImpression(banner: View?) = Unit
                                        override fun onClick(banner: View?) = Unit
                                    })
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                            onRelease = { container -> disposeStartIoBannerContainer(container) },
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
}

@Composable
private fun StartIoStandardFeedBannerCard(
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var bannerLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(bannerLoaded) {
        if (!bannerLoaded) {
            delay(STARTIO_FILL_TIMEOUT_MS)
            if (!bannerLoaded) {
                MomiStartIoSession.updateBanner("Feed 320x50 timeout")
                onFailed()
            }
        }
    }

    CollapsibleMeasuredAdWrapper(
        isVisible = bannerLoaded,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
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
                    SponsoredHeaderRow(label = "Partner Banner")
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
                                        MomiStartIoSession.updateBanner("Renderer recovered")
                                        bannerLoaded = false
                                        onFailed()
                                    }
                                    val hostCtx = ctx.findActivity() ?: ctx
                                    val banner = Banner(hostCtx, object : BannerListener {
                                        override fun onReceiveAd(banner: View) {
                                            mainHandler.post {
                                                (banner as? BannerStandard)?.let {
                                                    protectBannerStandardWebViews(it, onRendererGone)
                                                }
                                                runCatching { (banner as? Banner)?.showBanner() }
                                                if (hasRenderedAdWebContent(banner)) {
                                                    MomiStartIoSession.updateBanner("Loaded (Feed 320x50)")
                                                    bannerLoaded = true
                                                } else {
                                                    MomiStartIoSession.updateBanner("Blank banner — falling back")
                                                    bannerLoaded = false
                                                    onFailed()
                                                }
                                            }
                                        }

                                        override fun onFailedToReceiveAd(banner: View?) {
                                            MomiStartIoSession.updateBanner("No fill (Feed 320x50)")
                                            mainHandler.post {
                                                bannerLoaded = false
                                                onFailed()
                                            }
                                        }

                                        override fun onImpression(banner: View?) = Unit
                                        override fun onClick(banner: View?) = Unit
                                    })
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                            onRelease = { container -> disposeStartIoBannerContainer(container) },
                            modifier = Modifier
                                .width(320.dp)
                                .height(50.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonetagPartnerShowcaseCard(
    directLinkUrl: String,
    config: AdMonetizationConfig,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(directLinkUrl) {
        MomiStartIoSession.updateMonetag("Active Direct Partner Link")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("monetag_partner_card")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable { runCatching { uriHandler.openUri(directLinkUrl) } },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SponsoredHeaderRow(label = "Monetag Partner")
                Text(
                    text = config.partnerPromoTitle.ifBlank { "Featured Creator Deals & Partner Showcase" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = config.partnerPromoSubtitle.ifBlank {
                        "Explore verified creator collections, partner offers & exclusive community perks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = { runCatching { uriHandler.openUri(directLinkUrl) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("monetag_partner_cta")
                ) {
                    Text(
                        text = config.partnerPromoCta.ifBlank { "Explore Partner" },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open sponsored partner link",
                        modifier = Modifier.size(16.dp)
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
private fun MonetagCompactStrip(
    directLinkUrl: String,
    config: AdMonetizationConfig,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(directLinkUrl) {
        MomiStartIoSession.updateMonetag("Active Direct Partner Strip")
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { runCatching { uriHandler.openUri(directLinkUrl) } }
            .testTag("monetag_compact_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                    text = config.partnerPromoTitle.ifBlank { "Explore Partner Showcase" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open partner offer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SponsoredHeaderRow(label: String) {
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
                text = label,
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
}

@Composable
fun MomiAnchoredExploreBanner(
    monetizationConfig: AdMonetizationConfig = AdMonetizationConfig(),
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    val admobUnitId = remember(monetizationConfig) { resolveAdMobBannerUnitId(monetizationConfig) }
    val monetagUrl = remember(monetizationConfig) { resolveMonetagDirectUrl(monetizationConfig) }

    var admobExhausted by remember(monetizationConfig.admobEnabled, admobUnitId) {
        mutableStateOf(!monetizationConfig.admobEnabled || admobUnitId.isBlank())
    }
    var startIoFailed by remember(monetizationConfig.startIoEnabled) {
        mutableStateOf(!monetizationConfig.startIoEnabled)
    }

    if (!admobExhausted) {
        AdMobAnchoredStrip(
            unitId = admobUnitId,
            tag = "explore_ad_banner",
            onFailedToFill = { admobExhausted = true },
            modifier = modifier
        )
        return
    }

    if (monetizationConfig.startIoEnabled && sdkReady && !startIoFailed) {
        StartIoAnchoredStrip(
            tag = "explore_ad_banner",
            statusLabel = "Explore 320x50",
            onFailedToFill = { startIoFailed = true },
            modifier = modifier
        )
        return
    }

    if (monetagUrl.isNotEmpty()) {
        MonetagCompactStrip(
            directLinkUrl = monetagUrl,
            config = monetizationConfig,
            modifier = modifier
        )
    }
}

@Composable
fun MomiMarketplaceBanner(
    monetizationConfig: AdMonetizationConfig = AdMonetizationConfig(),
    modifier: Modifier = Modifier
) {
    val sdkReady by MomiStartIoSession.isInitialized.collectAsState()
    val admobUnitId = remember(monetizationConfig) { resolveAdMobBannerUnitId(monetizationConfig) }
    val monetagUrl = remember(monetizationConfig) { resolveMonetagDirectUrl(monetizationConfig) }

    var admobExhausted by remember(monetizationConfig.admobEnabled, admobUnitId) {
        mutableStateOf(!monetizationConfig.admobEnabled || admobUnitId.isBlank())
    }
    var startIoFailed by remember(monetizationConfig.startIoEnabled) {
        mutableStateOf(!monetizationConfig.startIoEnabled)
    }

    if (!admobExhausted) {
        AdMobAnchoredStrip(
            unitId = admobUnitId,
            tag = "marketplace_ad_banner",
            onFailedToFill = { admobExhausted = true },
            modifier = modifier
        )
        return
    }

    if (monetizationConfig.startIoEnabled && sdkReady && !startIoFailed) {
        StartIoAnchoredStrip(
            tag = "marketplace_ad_banner",
            statusLabel = "Market 320x50",
            onFailedToFill = { startIoFailed = true },
            modifier = modifier
        )
        return
    }

    if (monetagUrl.isNotEmpty()) {
        MonetagCompactStrip(
            directLinkUrl = monetagUrl,
            config = monetizationConfig,
            modifier = modifier
        )
    }
}

@Composable
private fun AdMobAnchoredStrip(
    unitId: String,
    tag: String,
    onFailedToFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoaded by remember(unitId) { mutableStateOf(false) }

    CollapsibleMeasuredAdWrapper(
        isVisible = isLoaded,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .testTag(tag),
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
                            val adView = AdView(ctx).apply {
                                setAdSize(AdSize.BANNER)
                                adUnitId = unitId
                                adListener = object : AdListener() {
                                    override fun onAdLoaded() {
                                        isLoaded = true
                                        MomiStartIoSession.updateAdMob("Banner loaded (320x50)")
                                    }

                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        isLoaded = false
                                        MomiStartIoSession.updateAdMob("Banner no fill (${error.code})")
                                        onFailedToFill()
                                    }
                                }
                            }
                            addView(
                                adView,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER
                                )
                            )
                            post {
                                runCatching { adView.loadAd(AdRequest.Builder().build()) }
                                    .onFailure { onFailedToFill() }
                            }
                        }
                    },
                    onRelease = { container -> disposeAdMobContainer(container) },
                    modifier = Modifier
                        .width(320.dp)
                        .height(50.dp)
                )
            }
        }
    }
}

@Composable
private fun StartIoAnchoredStrip(
    tag: String,
    statusLabel: String,
    onFailedToFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(isLoaded) {
        if (!isLoaded) {
            delay(STARTIO_FILL_TIMEOUT_MS)
            if (!isLoaded) {
                MomiStartIoSession.updateBanner("No fill ($statusLabel timeout)")
                onFailedToFill()
            }
        }
    }

    CollapsibleMeasuredAdWrapper(
        isVisible = isLoaded,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .testTag(tag),
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
                                MomiStartIoSession.updateBanner("Renderer recovered")
                                isLoaded = false
                                onFailedToFill()
                            }
                            val hostCtx = ctx.findActivity() ?: ctx
                            val banner = Banner(hostCtx, object : BannerListener {
                                override fun onReceiveAd(banner: View) {
                                    mainHandler.post {
                                        (banner as? BannerStandard)?.let {
                                            protectBannerStandardWebViews(it, onRendererGone)
                                        }
                                        runCatching { (banner as? Banner)?.showBanner() }
                                        if (hasRenderedAdWebContent(banner)) {
                                            MomiStartIoSession.updateBanner("Loaded ($statusLabel)")
                                            isLoaded = true
                                        } else {
                                            MomiStartIoSession.updateBanner("Blank banner ($statusLabel)")
                                            isLoaded = false
                                            onFailedToFill()
                                        }
                                    }
                                }

                                override fun onFailedToReceiveAd(banner: View?) {
                                    MomiStartIoSession.updateBanner("No fill ($statusLabel)")
                                    mainHandler.post {
                                        isLoaded = false
                                        onFailedToFill()
                                    }
                                }

                                override fun onImpression(banner: View?) = Unit
                                override fun onClick(banner: View?) = Unit
                            })
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                                MomiStartIoSession.updateBanner("Requesting $statusLabel...")
                                runCatching { banner.loadAd(320, 50) }
                            }
                        }
                    },
                    onRelease = { container -> disposeStartIoBannerContainer(container) },
                    modifier = Modifier
                        .width(320.dp)
                        .height(50.dp)
                )
            }
        }
    }
}

@Composable
fun MomiGamezopArcadeCard(
    monetizationConfig: AdMonetizationConfig = AdMonetizationConfig(),
    modifier: Modifier = Modifier
) {
    val gamezopUrl = remember(monetizationConfig) { resolveGamezopLaunchUrl(monetizationConfig) }
    if (gamezopUrl.isEmpty()) {
        LaunchedEffect(monetizationConfig.gamezopEnabled) {
            MomiStartIoSession.updateGamezop(
                if (monetizationConfig.gamezopEnabled) "Enabled (needs Publisher ID or URL)" else "Standby"
            )
        }
        return
    }

    val uriHandler = LocalUriHandler.current
    LaunchedEffect(gamezopUrl) {
        MomiStartIoSession.updateGamezop("Arcade Ready")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { runCatching { uriHandler.openUri(gamezopUrl) } }
            .testTag("gamezop_arcade_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = "Gamezop Instant Games Arcade",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Partner Games",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Gamezop",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = monetizationConfig.gamezopBannerTitle.ifBlank { "Momi Arcade · Play Instant Games" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Instant HTML5 mini-games · Play in browser",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = { runCatching { uriHandler.openUri(gamezopUrl) } },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("gamezop_play_button")
            ) {
                Text(
                    text = "Play",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Launch Gamezop Arcade",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

