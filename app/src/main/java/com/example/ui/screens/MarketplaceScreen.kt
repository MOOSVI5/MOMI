package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.UserProfile
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets
import java.util.Locale

private val MarketCanvasBg = Color(0xFFFFFFFF)
private val MarketPromoGray = Color(0xFFF5F5F5)
private val MarketChipGray = Color(0xFFEDEDED)
private val MarketBorderGray = Color(0xFFE0E0E0)
private val MarketCharcoal = Color(0xFF191919)
private val MarketMutedGray = Color(0xFF6B6B6B)
private val MarketCrimson = Color(0xFFE53238)
private val MarketPromoBlue = Color(0xFF3665F3)

internal val MarketCategories = listOf(
    "All Deals",
    "Collectibles",
    "Electronics",
    "Fashion",
    "Home",
    "Beauty"
)

@Composable
fun MarketplaceScreen(
    profile: UserProfile?,
    currentUserId: String,
    isAdminUser: Boolean,
    merchants: List<MerchantAccount>,
    products: List<MarketProduct>,
    orders: List<MarketOrder>,
    onBack: () -> Unit,
    onCreateOrUpdateMerchant: (storeName: String, tagline: String, category: String, location: String, onDone: (Boolean) -> Unit) -> Unit,
    onSetMerchantVerified: (merchantUid: String, verified: Boolean) -> Unit,
    onPublishProduct: (
        merchant: MerchantAccount,
        title: String,
        description: String,
        category: String,
        priceUsd: Double,
        originalPriceUsd: Double,
        freeShipping: Boolean,
        imagePaths: List<String>,
        localVideoPath: String?,
        videoDurationMs: Long,
        stockCount: Long,
        onDone: (Boolean) -> Unit
    ) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onPlaceOrder: (MarketProduct, String, (Boolean) -> Unit) -> Unit,
    onUpdateOrderStatus: (String, String) -> Unit,
    onMessageSeller: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All Deals") }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<MarketProduct?>(null) }
    var showMerchantStudio by remember { mutableStateOf(false) }
    var showOrdersSheet by remember { mutableStateOf(false) }
    var showAdminVerifySheet by remember { mutableStateOf(false) }

    val ownMerchant = remember(merchants, currentUserId) {
        merchants.firstOrNull { it.userId == currentUserId }
    }
    val merchantsByUid = remember(merchants) { merchants.associateBy { it.userId } }

    val filteredProducts = remember(products, merchantsByUid, selectedCategory, searchQuery) {
        products.map { product ->
            val sellerVerified = merchantsByUid[product.sellerUid]?.isVerified ?: false
            product.copy(sellerVerified = sellerVerified)
        }.filter { product ->
            val matchesCat = selectedCategory == "All Deals" ||
                product.category.equals(selectedCategory, ignoreCase = true)
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                product.title.contains(q, ignoreCase = true) ||
                product.storeName.contains(q, ignoreCase = true) ||
                product.category.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val freeShippingDeals = remember(filteredProducts) {
        filteredProducts.filter { it.freeShipping }.take(10)
    }

    selectedProduct?.let { product ->
        val freshProduct = filteredProducts.firstOrNull { it.id == product.id } ?: product
        MarketProductDetailSheet(
            product = freshProduct,
            merchant = merchantsByUid[freshProduct.sellerUid],
            isOwnListing = freshProduct.sellerUid == currentUserId,
            onDismiss = { selectedProduct = null },
            onPlaceOrder = { address, onDone ->
                onPlaceOrder(freshProduct, address) { ok ->
                    onDone(ok)
                    if (ok) selectedProduct = null
                }
            },
            onMessageSeller = {
                selectedProduct = null
                onMessageSeller(freshProduct.sellerUid)
            },
            onDeleteListing = {
                onDeleteProduct(freshProduct.id)
                selectedProduct = null
            }
        )
    }

    if (showMerchantStudio) {
        MerchantStudioSheet(
            profile = profile,
            ownMerchant = ownMerchant,
            onDismiss = { showMerchantStudio = false },
            onSaveMerchant = onCreateOrUpdateMerchant,
            onPublishProduct = onPublishProduct
        )
    }

    if (showOrdersSheet) {
        MarketOrdersSheet(
            currentUserId = currentUserId,
            orders = orders,
            onUpdateOrderStatus = onUpdateOrderStatus,
            onDismiss = { showOrdersSheet = false }
        )
    }

    if (showAdminVerifySheet && isAdminUser) {
        AdminVerifyMerchantsSheet(
            merchants = merchants,
            onSetVerified = onSetMerchantVerified,
            onDismiss = { showAdminVerifySheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MarketCanvasBg)
            .statusBarsPadding()
    ) {
        // Marketplace Header Bar (Clean white & charcoal styling per screenshots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MarketCanvasBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to feed",
                        tint = MarketCharcoal
                    )
                }
                Text(
                    text = "Momi Market",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MarketCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { searchVisible = !searchVisible },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search marketplace",
                        tint = MarketCharcoal
                    )
                }
                IconButton(
                    onClick = { showMerchantStudio = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_sell_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddBusiness,
                        contentDescription = "Merchant studio & sell",
                        tint = MarketCharcoal
                    )
                }
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = { showOrdersSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("market_orders_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = "My Orders",
                            tint = MarketCharcoal
                        )
                    }
                    if (orders.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 6.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(MarketCrimson),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = orders.size.coerceAtMost(9).toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (isAdminUser) {
                    IconButton(
                        onClick = { showAdminVerifySheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("market_admin_verify_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin verify merchants",
                            tint = MarketPromoBlue
                        )
                    }
                }
            }
        }

        if (searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search products, stores, or categories...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("market_search_input")
            )
        }

        // Deals / Categories Bar (Screenshot 3 style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MarketCanvasBg)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Deals",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MarketCharcoal
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                items(MarketCategories) { cat ->
                    val active = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) MarketCharcoal else MarketChipGray)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else MarketCharcoal
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = MarketBorderGray)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MarketCanvasBg),
            contentPadding = PaddingValues(bottom = 84.dp)
        ) {
            // Screenshot 1 Hero 1: "Shopping made easy"
            item(key = "hero_shopping_easy") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MarketPromoGray)
                        .padding(horizontal = 18.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Shopping made easy",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MarketCharcoal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enjoy verified merchant stores, photos & product clips, and direct orders.",
                        fontSize = 15.sp,
                        color = MarketCharcoal.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showMerchantStudio = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MarketCharcoal,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = if (ownMerchant == null) "Start selling" else "Post a listing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    if (ownMerchant != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = if (ownMerchant.isVerified) MarketPromoBlue else MarketMutedGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (ownMerchant.isVerified) {
                                    "${ownMerchant.storeName} · Verified Merchant"
                                } else {
                                    "${ownMerchant.storeName} · Pending admin verification"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ownMerchant.isVerified) MarketPromoBlue else MarketMutedGray
                            )
                        }
                    }
                }
            }

            // Blue shipping banner (Screenshot 3 style)
            item(key = "global_shipping_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MarketPromoBlue)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Verified Marketplace. Global reach.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Every verified badge is reviewed by the Momi owner.",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                            .clickable { selectedCategory = "All Deals" }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Shop now",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Screenshot 1 Hero 2: "Great finds. Free shipping." + crimson pill & collage
            item(key = "hero_free_shipping_collage") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MarketPromoGray)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Great finds. Free\nshipping.",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MarketCharcoal,
                        lineHeight = 31.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Find collectibles, tech, fashion, and creator drops with free shipping.",
                        fontSize = 14.sp,
                        color = MarketCharcoal.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { selectedCategory = "Collectibles" },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MarketCharcoal,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Shop collectibles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    val collageProducts = products.take(6)
                    val showcaseKeys = listOf("img_post_coastal", "img_post_coffee", "img_post_architecture", "img_post_neon_street")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..2) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (col == 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MarketCrimson)
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Free shipping",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                for (row in 0..1) {
                                    val idx = col * 2 + row
                                    val itemProduct = collageProducts.getOrNull(idx)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .clickable {
                                                if (itemProduct != null) {
                                                    selectedProduct = itemProduct
                                                } else {
                                                    showMerchantStudio = true
                                                }
                                            }
                                    ) {
                                        PostMediaImage(
                                            imageKey = itemProduct?.imageKey ?: showcaseKeys[idx % showcaseKeys.size],
                                            localImagePath = null,
                                            imageData = itemProduct?.imageData,
                                            filterTag = "NONE",
                                            contentDescription = itemProduct?.title ?: "Featured item",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Today's Deals / All with free shipping" horizontal strip
            if (freeShippingDeals.isNotEmpty()) {
                item(key = "todays_deals_strip") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MarketCanvasBg)
                            .padding(top = 18.dp, bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "Today's Deals",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MarketCharcoal
                            )
                            Text(
                                text = "All with free shipping",
                                fontSize = 14.sp,
                                color = MarketMutedGray
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(freeShippingDeals, key = { "today_${it.id}" }) { deal ->
                                MarketCompactDealCard(
                                    product = deal,
                                    onClick = { selectedProduct = deal }
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: "Featured Deals"
            item(key = "featured_deals_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (selectedCategory == "All Deals") "Featured Deals" else selectedCategory,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MarketCharcoal
                    )
                }
            }

            if (filteredProducts.isEmpty()) {
                item(key = "empty_market") {
                    Surface(
                        color = MarketPromoGray,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = MarketCharcoal,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No listings in $selectedCategory yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MarketCharcoal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open Merchant Studio to create your storefront and publish products with photos and video.",
                                fontSize = 13.sp,
                                color = MarketMutedGray
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { showMerchantStudio = true },
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MarketCharcoal,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Open Merchant Studio", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Featured first card horizontal (matching Screenshots 2 & 3 top item)
                val leadProduct = filteredProducts.first()
                item(key = "lead_${leadProduct.id}") {
                    MarketLeadProductCard(
                        product = leadProduct,
                        onClick = { selectedProduct = leadProduct }
                    )
                }

                // 2-Column Product Grid matching Screenshots 2 & 3
                val remainingRows = filteredProducts.drop(1).chunked(2)
                items(remainingRows, key = { row -> "grid_${row.first().id}" }) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { product ->
                            MarketGridProductCard(
                                product = product,
                                onClick = { selectedProduct = product },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketCompactDealCard(
    product: MarketProduct,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MarketPromoGray)
            .border(0.75.dp, MarketBorderGray, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            PostMediaImage(
                imageKey = product.imageKey,
                localImagePath = null,
                imageData = product.imageData,
                filterTag = "NONE",
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (product.isVideoProduct) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = "Has product video",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = product.title,
            fontSize = 13.sp,
            color = MarketCharcoal,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUsd(product.priceUsd),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MarketCharcoal
        )
    }
}

@Composable
private fun MarketLeadProductCard(
    product: MarketProduct,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MarketCanvasBg)
            .border(0.75.dp, MarketBorderGray, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("market_product_${product.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MarketPromoGray)
            ) {
                PostMediaImage(
                    imageKey = product.imageKey,
                    localImagePath = null,
                    imageData = product.imageData,
                    filterTag = "NONE",
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (product.isVideoProduct) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = "Video available",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.title,
                    fontSize = 15.sp,
                    color = MarketCharcoal,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.storeName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MarketMutedGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (product.sellerVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified merchant",
                            tint = MarketPromoBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatUsd(product.priceUsd),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MarketCharcoal
                )
                if (product.discountPercent > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatUsd(product.originalPriceUsd),
                            fontSize = 12.sp,
                            color = MarketMutedGray,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            text = " | ${product.discountPercent}% OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MarketCharcoal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketGridProductCard(
    product: MarketProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MarketCanvasBg)
            .border(0.75.dp, MarketBorderGray, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("market_product_${product.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MarketPromoGray)
        ) {
            PostMediaImage(
                imageKey = product.imageKey,
                localImagePath = null,
                imageData = product.imageData,
                filterTag = "NONE",
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (product.isVideoProduct) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = "Has product video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = product.title,
            fontSize = 14.sp,
            color = MarketCharcoal,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = product.storeName,
                fontSize = 11.sp,
                color = MarketMutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (product.sellerVerified) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Verified merchant",
                    tint = MarketPromoBlue,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = formatUsd(product.priceUsd),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MarketCharcoal
        )

        if (product.discountPercent > 0L) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatUsd(product.originalPriceUsd),
                    fontSize = 11.sp,
                    color = MarketMutedGray,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    text = " | ${product.discountPercent}% OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MarketCharcoal
                )
            }
        }
    }
}

internal fun formatUsd(amount: Double): String =
    String.format(Locale.US, "US $%.2f", amount)
