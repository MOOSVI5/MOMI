package com.example.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

private const val TAG = "MomiStartIo"

data class StartIoNativeAdState(
    val ads: List<NativeAdDetails> = emptyList(),
    val finishedLoading: Boolean = false
)

@Composable
fun rememberStartIoNativeAds(count: Int = 3): StartIoNativeAdState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StartIoNativeAdState()) }

    LaunchedEffect(context, count) {
        val mainHandler = Handler(Looper.getMainLooper())
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
                    mainHandler.post {
                        state = StartIoNativeAdState(ads = loaded, finishedLoading = true)
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "Native ad failed to load: ${ad?.errorMessage ?: "no fill"}")
                    mainHandler.post {
                        state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
                    }
                }
            })
        }.onFailure { error ->
            Log.w(TAG, "Native ad loader error: ${error.message}")
            state = StartIoNativeAdState(ads = emptyList(), finishedLoading = true)
        }
    }

    return state
}

@Composable
fun MomiInlineFeedAdCard(
    slotIndex: Int = 0,
    nativeAdState: StartIoNativeAdState = StartIoNativeAdState(),
    modifier: Modifier = Modifier
) {
    val nativeAd = if (nativeAdState.ads.isNotEmpty()) {
        nativeAdState.ads[slotIndex % nativeAdState.ads.size]
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
    var adFailed by remember { mutableStateOf(false) }
    var mrecRef by remember { mutableStateOf<Mrec?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (adFailed) return

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
                                        mainHandler.post { adFailed = false }
                                    }

                                    override fun onFailedToReceiveAd(banner: View?) {
                                        Log.w(TAG, "MREC ad failed to receive")
                                        mainHandler.post { adFailed = true }
                                    }

                                    override fun onImpression(banner: View?) = Unit

                                    override fun onClick(banner: View?) = Unit
                                })
                                mrecRef = mrec
                                addView(
                                    mrec,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        Gravity.CENTER
                                    )
                                )
                                mrec.showBanner()
                                mrec.loadAd(300, 250)
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
    var adFailed by remember { mutableStateOf(false) }
    var bannerRef by remember { mutableStateOf<Banner?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (adFailed) return

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
                                mainHandler.post { adFailed = false }
                            }

                            override fun onFailedToReceiveAd(banner: View?) {
                                Log.w(TAG, "Explore banner failed to receive")
                                mainHandler.post { adFailed = true }
                            }

                            override fun onImpression(banner: View?) = Unit

                            override fun onClick(banner: View?) = Unit
                        })
                        bannerRef = banner
                        addView(
                            banner,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER
                            )
                        )
                        banner.showBanner()
                        banner.loadAd(320, 50)
                    }
                },
                modifier = Modifier
                    .width(320.dp)
                    .height(50.dp)
            )
        }
    }
}
