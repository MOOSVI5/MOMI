package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.billing.PlayBillingUiState
import com.example.data.model.AccountPerkItem
import com.example.data.model.CreditPackageOption
import com.example.data.model.CreditTransaction
import com.example.data.model.LiveGiftItem
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.ui.components.LumaAvatar
import com.example.ui.components.SendGiftBottomSheet
import com.example.ui.theme.MaroonVibrant
import com.example.ui.theme.SilkBlueDeep
import com.example.util.MediaAssets
import java.util.Locale

@Composable
fun WalletScreen(
    currentUserId: String,
    profile: UserProfile?,
    wallet: UserWallet?,
    transactions: List<CreditTransaction>,
    allCreators: List<UserProfile>,
    billingState: PlayBillingUiState,
    isProcessing: Boolean,
    onRefreshBilling: () -> Unit,
    onBuyWithPlay: (CreditPackageOption) -> Unit,
    onSandboxTopUp: (CreditPackageOption) -> Unit,
    onSendGiftToCreator: (recipientUid: String, recipientUsername: String, gift: LiveGiftItem) -> Unit,
    onUnlockPerk: (AccountPerkItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    var giftTargetCreator by remember { mutableStateOf<UserProfile?>(null) }

    val balance = wallet?.balance ?: 0L
    val totalPurchased = wallet?.totalPurchased ?: 0L
    val totalEarned = wallet?.totalEarned ?: 0L
    val totalSpent = wallet?.totalSpent ?: 0L
    val unlockedPerks = wallet?.unlockedPerks.orEmpty()
    val accountTierLabel = MomiCreditCatalog.tierLabel(wallet?.accountTier ?: MomiCreditCatalog.TIER_STANDARD)
    val verificationLabel = if (profile?.isVerified == true) {
        "Verified Creator"
    } else {
        MomiCreditCatalog.verificationLabel(wallet?.verificationTier ?: MomiCreditCatalog.VERIFICATION_NONE)
    }

    val otherCreators = remember(allCreators, currentUserId) {
        allCreators.filter { it.userId != currentUserId }
    }

    giftTargetCreator?.let { creator ->
        SendGiftBottomSheet(
            recipientUsername = creator.username,
            isLiveContext = false,
            wallet = wallet,
            isProcessing = isProcessing,
            onSendGift = { gift ->
                onSendGiftToCreator(creator.userId, creator.username, gift)
                giftTargetCreator = null
            },
            onOpenWallet = { selectedTab = 0 },
            onDismiss = { giftTargetCreator = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("wallet_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Momi Pay & Credits",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${profile?.username ?: "creator"} · $accountTierLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefreshBilling,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh Play Billing",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "wallet_hero_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SilkBlueDeep, MaroonVibrant)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Momi Credit Account",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = verificationLabel,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Icon(
                                imageVector = Icons.Filled.Toll,
                                contentDescription = "Credits",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(bottom = 3.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = MediaAssets.formatCompactCount(balance),
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("wallet_balance_text")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "credits",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WalletStatChip("Purchased", totalPurchased, Modifier.weight(1f))
                            WalletStatChip("Gift Earned", totalEarned, Modifier.weight(1f))
                            WalletStatChip("Spent", totalSpent, Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "wallet_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(0, "Top Up", Icons.Filled.ShoppingBag),
                        Triple(1, "Gift", Icons.Filled.CardGiftcard),
                        Triple(2, "Perks", Icons.Filled.WorkspacePremium),
                        Triple(3, "History", Icons.Filled.History)
                    ).forEach { (idx, label, icon) ->
                        val active = selectedTab == idx
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedTab = idx }
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                .testTag("wallet_tab_$idx"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    item(key = "play_billing_status") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = billingState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    items(MomiCreditCatalog.packages, key = { it.productId }) { pkg ->
                        val priceLabel = billingState.localizedPrices[pkg.productId]
                            ?: String.format(Locale.US, "$%.2f", pkg.fallbackPriceUsd)
                        val skuPublishedInPlay = pkg.productId in billingState.loadedProductIds
                        CreditPackCard(
                            pkg = pkg,
                            priceLabel = priceLabel,
                            skuPublishedInPlay = skuPublishedInPlay,
                            isProcessing = isProcessing,
                            onBuyWithPlay = { onBuyWithPlay(pkg) },
                            onSandboxTopUp = { onSandboxTopUp(pkg) }
                        )
                    }
                }

                1 -> {
                    item(key = "gift_intro") {
                        Text(
                            text = "Send credit gifts directly to creators or join a Live Stream room to celebrate streamers on air.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (otherCreators.isEmpty()) {
                        item(key = "gift_empty_creators") {
                            Text(
                                text = "No other signed-in creators available yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(otherCreators, key = { "gift_creator_${it.userId}" }) { creator ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LumaAvatar(
                                        username = creator.username,
                                        avatarKey = creator.avatarKey,
                                        avatarData = creator.avatarData,
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                    Button(
                                        onClick = { giftTargetCreator = creator },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .testTag("wallet_gift_creator_${creator.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CardGiftcard,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Gift", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item(key = "perks_roadmap_banner") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Stars,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Account Perks & Verification Roadmap",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Unlock supporter tiers and queue for priority creator verification now. Upcoming Momi updates will expand these perks with ID KYC badges, creator cashouts, and VIP lounges.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(MomiCreditCatalog.perks, key = { it.key }) { perk ->
                        val isUnlocked = perk.key in unlockedPerks
                        val canAfford = balance >= perk.creditsCost
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = perk.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isUnlocked) Color(0xFF16A34A).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (isUnlocked) "Active" else "${perk.creditsCost} cr",
                                            color = if (isUnlocked) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = perk.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (perk.comingSoonFeatures.isNotEmpty()) {
                                    Text(
                                        text = "Future perks included: ${perk.comingSoonFeatures.joinToString(" · ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isUnlocked) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Unlocked",
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Unlocked on your Momi account",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else {
                                    Button(
                                        enabled = canAfford && !isProcessing,
                                        onClick = { onUnlockPerk(perk) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .testTag("unlock_perk_${perk.key}")
                                    ) {
                                        Text(
                                            text = if (canAfford) "Unlock for ${perk.creditsCost} Credits"
                                            else "Need ${perk.creditsCost} Credits",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (transactions.isEmpty()) {
                        item(key = "history_empty") {
                            Text(
                                text = "No credit transactions yet. Top up credits or send a gift during a live stream!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(transactions, key = { it.txId.ifBlank { it.id } }) { tx ->
                            TransactionRowCard(currentUserId = currentUserId, tx = tx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletStatChip(
    label: String,
    value: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            text = "${MediaAssets.formatCompactCount(value)} cr",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CreditPackCard(
    pkg: CreditPackageOption,
    priceLabel: String,
    skuPublishedInPlay: Boolean,
    isProcessing: Boolean,
    onBuyWithPlay: () -> Unit,
    onSandboxTopUp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pkg.isPopular) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Toll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${pkg.credits} Credits · ${pkg.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = pkg.bonusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = priceLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !isProcessing,
                    onClick = onBuyWithPlay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("buy_credits_${pkg.productId}")
                ) {
                    Text(
                        text = if (skuPublishedInPlay) "Buy via Google Play ($priceLabel)"
                        else "Google Play ($priceLabel)",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (BuildConfig.DEBUG && !skuPublishedInPlay) {
                    OutlinedButton(
                        enabled = !isProcessing,
                        onClick = onSandboxTopUp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("sandbox_topup_${pkg.productId}")
                    ) {
                        Text("Dev Test +${pkg.credits}", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    currentUserId: String,
    tx: CreditTransaction
) {
    val isInbound = when (tx.type) {
        CreditTransaction.TYPE_TOP_UP -> true
        CreditTransaction.TYPE_GIFT -> tx.recipientUid == currentUserId
        else -> false
    }
    val deltaText = if (isInbound) "+${tx.creditsAmount} cr" else "-${tx.creditsAmount} cr"
    val deltaColor = if (isInbound) Color(0xFF16A34A) else MaterialTheme.colorScheme.secondary
    val title = when (tx.type) {
        CreditTransaction.TYPE_TOP_UP -> "Credit Top-Up (${tx.creditsAmount} cr)"
        CreditTransaction.TYPE_GIFT -> if (isInbound) {
            "Gift ${tx.giftName ?: "Gift"} from @${tx.senderUsername}"
        } else {
            "Sent ${tx.giftName ?: "Gift"} to @${tx.recipientUsername}"
        }
        else -> "Perk Unlocked (${tx.perkKey ?: "Perk"})"
    }
    val methodLabel = when (tx.paymentMethod) {
        CreditTransaction.METHOD_GOOGLE_PLAY -> "Google Play"
        CreditTransaction.METHOD_DEBUG_SANDBOX -> "Dev Sandbox"
        else -> "Momi Credits"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$methodLabel · ${MediaAssets.formatRelativeTime(tx.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = deltaText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = deltaColor
            )
        }
    }
}
