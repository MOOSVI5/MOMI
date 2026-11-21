package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.AccountPerkItem
import com.example.data.model.AdMonetizationConfig
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiStartIoSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerMonetizationAndPlaySheet(
    currentConfig: AdMonetizationConfig,
    isSaving: Boolean,
    onSaveConfig: (AdMonetizationConfig, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isDebugAds = BuildConfig.DEBUG
    val adDiagnostics by MomiStartIoSession.diagnostics.collectAsStateWithLifecycle()

    var admobEnabled by remember(currentConfig) { mutableStateOf(currentConfig.admobEnabled) }
    var admobBannerUnitId by remember(currentConfig) { mutableStateOf(currentConfig.admobBannerUnitId) }
    var admobMrecUnitId by remember(currentConfig) { mutableStateOf(currentConfig.admobMrecUnitId) }
    var admobInterstitialUnitId by remember(currentConfig) { mutableStateOf(currentConfig.admobInterstitialUnitId) }
    var startIoEnabled by remember(currentConfig) { mutableStateOf(currentConfig.startIoEnabled) }
    var monetagEnabled by remember(currentConfig) { mutableStateOf(currentConfig.monetagEnabled) }
    var monetagDirectLinkUrl by remember(currentConfig) { mutableStateOf(currentConfig.monetagDirectLinkUrl) }
    var monetagWebZoneId by remember(currentConfig) { mutableStateOf(currentConfig.monetagWebZoneId) }
    var monetagWebMetaTag by remember(currentConfig) { mutableStateOf(currentConfig.monetagWebMetaTag) }
    var partnerPromoTitle by remember(currentConfig) { mutableStateOf(currentConfig.partnerPromoTitle) }
    var partnerPromoSubtitle by remember(currentConfig) { mutableStateOf(currentConfig.partnerPromoSubtitle) }
    var partnerPromoCta by remember(currentConfig) { mutableStateOf(currentConfig.partnerPromoCta) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monetizers & Play Readiness",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live 3-tier waterfall (AdMob -> Start.io -> Monetag Partner) synced across Android & Web.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Live Multi-Network Diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        PlayInfoLine("Google AdMob", adDiagnostics.admobStatus)
                        PlayInfoLine("Start.io SDK", "${adDiagnostics.sdkStatus} (App ID ${BuildConfig.STARTIO_APP_ID})")
                        PlayInfoLine("Start.io Feed / MREC", "${adDiagnostics.nativeStatus} | ${adDiagnostics.mrecStatus}")
                        PlayInfoLine("Start.io Banner", adDiagnostics.bannerStatus)
                        PlayInfoLine("Monetag Direct / Web", adDiagnostics.monetagStatus)
                        PlayInfoLine("Interstitial", adDiagnostics.interstitialStatus)
                        PlayInfoLine(
                            "Ad Serving Mode",
                            if (isDebugAds) "Debug Test Ads ($0 — signed Release build uses live payouts)" else "Release Paid Live Ads"
                        )
                        PlayInfoLine(
                            "App Identity",
                            "${BuildConfig.APPLICATION_ID} (v${BuildConfig.VERSION_NAME} / code ${BuildConfig.VERSION_CODE})"
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Configure AdMob, Start.io & Monetag",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google AdMob (Tier 1)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Serves AdMob banners & interstitials before fallback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = admobEnabled, onCheckedChange = { admobEnabled = it })
                        }

                        if (admobEnabled) {
                            OutlinedTextField(
                                value = admobBannerUnitId,
                                onValueChange = { admobBannerUnitId = it },
                                label = { Text("AdMob Banner Unit ID (optional)") },
                                placeholder = { Text(BuildConfig.ADMOB_BANNER_UNIT_ID) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = admobMrecUnitId,
                                onValueChange = { admobMrecUnitId = it },
                                label = { Text("AdMob MREC Unit ID (optional)") },
                                placeholder = { Text(BuildConfig.ADMOB_MREC_UNIT_ID) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = admobInterstitialUnitId,
                                onValueChange = { admobInterstitialUnitId = it },
                                label = { Text("AdMob Interstitial Unit ID (optional)") },
                                placeholder = { Text(BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Start.io SDK (Tier 2)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Collapsed cleanly until filled — never shows empty boxes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = startIoEnabled, onCheckedChange = { startIoEnabled = it })
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Monetag Partner & Web (Tier 3)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("SmartLink Direct Partner Cards on Android + Web Tag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = monetagEnabled, onCheckedChange = { monetagEnabled = it })
                        }

                        if (monetagEnabled) {
                            OutlinedTextField(
                                value = monetagDirectLinkUrl,
                                onValueChange = {
                                    monetagDirectLinkUrl = it
                                    validationError = null
                                },
                                label = { Text("Monetag Direct SmartLink URL (https://...)") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("monetag_direct_link_input")
                            )
                            OutlinedTextField(
                                value = monetagWebZoneId,
                                onValueChange = { monetagWebZoneId = it },
                                label = { Text("Monetag Web Zone ID (e.g. 1234567)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("monetag_web_zone_input")
                            )
                            OutlinedTextField(
                                value = monetagWebMetaTag,
                                onValueChange = { monetagWebMetaTag = it },
                                label = { Text("Monetag Web <meta name=\"monetag\"> Token") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("monetag_web_meta_input")
                            )
                            OutlinedTextField(
                                value = partnerPromoTitle,
                                onValueChange = { partnerPromoTitle = it },
                                label = { Text("Partner Card Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = partnerPromoSubtitle,
                                onValueChange = { partnerPromoSubtitle = it },
                                label = { Text("Partner Card Subtitle") },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = partnerPromoCta,
                                onValueChange = { partnerPromoCta = it },
                                label = { Text("Partner CTA Label") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!validationError.isNullOrBlank()) {
                            Text(
                                text = validationError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = {
                                val trimmedLink = monetagDirectLinkUrl.trim()
                                if (trimmedLink.isNotEmpty() && !trimmedLink.startsWith("https://")) {
                                    validationError = "Monetag Direct SmartLink URL must start with https://"
                                    return@Button
                                }
                                onSaveConfig(
                                    currentConfig.copy(
                                        admobEnabled = admobEnabled,
                                        admobBannerUnitId = admobBannerUnitId.trim(),
                                        admobMrecUnitId = admobMrecUnitId.trim(),
                                        admobInterstitialUnitId = admobInterstitialUnitId.trim(),
                                        startIoEnabled = startIoEnabled,
                                        monetagEnabled = monetagEnabled,
                                        monetagDirectLinkUrl = trimmedLink,
                                        monetagWebZoneId = monetagWebZoneId.trim(),
                                        monetagWebMetaTag = monetagWebMetaTag.trim(),
                                        partnerPromoTitle = partnerPromoTitle.trim(),
                                        partnerPromoSubtitle = partnerPromoSubtitle.trim(),
                                        partnerPromoCta = partnerPromoCta.trim()
                                    )
                                ) { ok ->
                                    if (ok) onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_monetizers_config_button")
                        ) {
                            Text(
                                text = if (isSaving) "Saving..." else "Save Monetizer Settings (Android & Web)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Google Play Policy & Quota Hardening",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        PlayChecklistRow("Zero broad storage permissions (Photo Picker only)")
                        PlayChecklistRow("Explicit Sponsored labeling + https:// external browser launch for Direct Links")
                        PlayChecklistRow("ProGuard keep rules configured for AdMob & Start.io in Release .aab")
                        PlayChecklistRow("100 MB offline Firestore cache + sticky feed during Spark quota limits")
                    }
                }
            }

            item {
                Text(
                    text = "Open Management Portals",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalLinkButton(
                        title = "Monetag Publisher Dashboard",
                        subtitle = "Create Direct SmartLink & Web MultiTag / Meta token",
                        onClick = { runCatching { uriHandler.openUri("https://publishers.monetag.com") } }
                    )
                    PortalLinkButton(
                        title = "Google AdMob Console",
                        subtitle = "Manage Android App ID & Banner / MREC / Interstitial Ad Units",
                        onClick = { runCatching { uriHandler.openUri("https://admob.google.com") } }
                    )
                    PortalLinkButton(
                        title = "Start.io Publisher Portal",
                        subtitle = "Link Play Store listing & view impressions / eCPM",
                        onClick = { runCatching { uriHandler.openUri("https://portal.start.io") } }
                    )
                    PortalLinkButton(
                        title = "Firebase Quota & Blaze Upgrade",
                        subtitle = "Project gen-lang-client-0132255277 daily reads/writes",
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://console.firebase.google.com/project/gen-lang-client-0132255277/usage/details")
                            }
                        }
                    )
                    PortalLinkButton(
                        title = "Google Play Console",
                        subtitle = "Upload signed .aab, complete Data Safety & publish",
                        onClick = { runCatching { uriHandler.openUri("https://play.google.com/console") } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerPerksAndGrantsSheet(
    allCreators: List<UserProfile>,
    allPosts: List<Post>,
    isProcessing: Boolean,
    onGrantCreator: (targetUser: UserProfile, creditsAmount: Long, perk: AccountPerkItem?, grantVerified: Boolean) -> Unit,
    onRecommendPost: (post: Post, recommended: Boolean, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var consentedOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCreatorUid by remember { mutableStateOf<String?>(null) }
    var grantCredits by remember { mutableLongStateOf(200L) }
    var selectedPerkKey by remember { mutableStateOf<String?>(null) }
    var grantVerified by remember { mutableStateOf(false) }

    val filteredCreators = remember(allCreators, consentedOnly, searchQuery) {
        val q = searchQuery.trim()
        allCreators.filter { creator ->
            val matchesConsent = !consentedOnly || creator.partnerConsent
            val matchesQuery = q.isEmpty() ||
                creator.username.contains(q, ignoreCase = true) ||
                creator.displayName.contains(q, ignoreCase = true)
            matchesConsent && matchesQuery
        }.sortedByDescending { it.partnerConsent }
    }

    val realPosts = remember(allPosts, searchQuery) {
        val q = searchQuery.trim()
        allPosts.filter { post ->
            !post.id.startsWith("showcase_") &&
                (q.isEmpty() ||
                    post.authorUsername.contains(q, ignoreCase = true) ||
                    post.caption.contains(q, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Owner Perks, Grants & Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grant free credits, verified status, or in-app perks to consenting creators, and feature posts under Recommended by Momi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Creator Grants", 1 to "Recommend Posts").forEach { (idx, title) ->
                        val active = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { activeTab = idx }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("owner_grants_tab_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(if (activeTab == 0) "Search creators by name or @username" else "Filter posts by caption or @author")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (activeTab == 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Show opted-in consenting creators only",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = consentedOnly,
                            onCheckedChange = { consentedOnly = it },
                            modifier = Modifier.testTag("owner_grants_consented_filter")
                        )
                    }
                }

                if (filteredCreators.isEmpty()) {
                    item {
                        Text(
                            text = "No matching creators found. Users can enable Partner Boost & Perks Consent inside Wallet -> Perks or Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(filteredCreators, key = { "grant_creator_${it.userId}" }) { creator ->
                        val isSelected = selectedCreatorUid == creator.userId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCreatorUid = if (isSelected) null else creator.userId
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LumaAvatar(
                                        username = creator.username,
                                        avatarKey = creator.avatarKey,
                                        avatarData = creator.avatarData,
                                        size = 40.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = creator.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (creator.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = "Verified",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "@${creator.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (creator.partnerConsent) Color(0xFF16A34A).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = if (creator.partnerConsent) "Consented ✓" else "Not Opted In",
                                            color = if (creator.partnerConsent) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    if (!creator.partnerConsent) {
                                        Text(
                                            text = "@${creator.username} has not opted in to Partner Boost & Perks Consent yet. They can enable it in Wallet -> Perks.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                        Text("Bonus Credits", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(0L, 50L, 200L, 500L, 1000L).forEach { amt ->
                                                FilterChip(
                                                    selected = grantCredits == amt,
                                                    onClick = { grantCredits = amt },
                                                    label = { Text(if (amt == 0L) "0 cr" else "+$amt cr") }
                                                )
                                            }
                                        }

                                        Text("Unlock In-App Perk", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilterChip(
                                                selected = selectedPerkKey == null,
                                                onClick = { selectedPerkKey = null },
                                                label = { Text("None") }
                                            )
                                            MomiCreditCatalog.perks.forEach { perk ->
                                                FilterChip(
                                                    selected = selectedPerkKey == perk.key,
                                                    onClick = { selectedPerkKey = perk.key },
                                                    label = { Text(perk.title) }
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { grantVerified = !grantVerified }
                                        ) {
                                            Checkbox(
                                                checked = grantVerified,
                                                onCheckedChange = { grantVerified = it }
                                            )
                                            Text(
                                                text = "Grant Verified Creator Checkmark",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Button(
                                            enabled = !isProcessing && (grantCredits > 0L || selectedPerkKey != null || grantVerified),
                                            onClick = {
                                                val chosenPerk = selectedPerkKey?.let { MomiCreditCatalog.perkByKey(it) }
                                                onGrantCreator(creator, grantCredits, chosenPerk, grantVerified)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("grant_user_button_${creator.userId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CardGiftcard,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Send Grant to @${creator.username}", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (realPosts.isEmpty()) {
                    item {
                        Text(
                            text = "No synced community posts available to recommend yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(realPosts, key = { "rec_post_${it.id}" }) { post ->
                        val isOwnerRecommended = post.boostTier == "OWNER_RECOMMENDED"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "@${post.authorUsername} · ${post.category}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = post.caption.ifBlank { "Shared media post" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (post.isBoosted) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = post.recommendedLabel.ifBlank { "Boosted" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        onRecommendPost(post, !isOwnerRecommended, "Recommended by Momi")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .testTag("owner_recommend_post_${post.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isOwnerRecommended) "Featured" else "Recommend",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayInfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlayChecklistRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PortalLinkButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open external portal",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
