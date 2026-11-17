package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar
import com.example.ui.components.MomiVideoPlayer
import com.example.ui.components.PostMediaImage
import com.example.util.MediaAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketProductDetailSheet(
    product: MarketProduct,
    merchant: MerchantAccount?,
    isOwnListing: Boolean,
    canCurateEditorial: Boolean = false,
    onDismiss: () -> Unit,
    onPlaceOrder: (shippingAddress: String, onDone: (Boolean) -> Unit) -> Unit,
    onMessageSeller: () -> Unit,
    onDeleteListing: () -> Unit,
    onToggleFeatured: ((Boolean) -> Unit)? = null,
    onReportListing: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var shippingAddress by remember { mutableStateOf("") }
    var isOrdering by remember { mutableStateOf(false) }

    val frames = remember(product.id, product.imageData, product.extraImageDatas) {
        product.allProductFrames
    }
    val totalSlides = frames.size + if (product.isVideoProduct) 1 else 0
    val pagerState = rememberPagerState(pageCount = { totalSlides.coerceAtLeast(1) })

    if (showCheckoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!isOrdering) showCheckoutDialog = false },
            title = { Text("Complete Order", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${product.title} — ${formatUsd(product.priceUsd)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = shippingAddress,
                        onValueChange = { shippingAddress = it },
                        label = { Text("Delivery Address & Phone") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = shippingAddress.isNotBlank() && !isOrdering,
                    onClick = {
                        isOrdering = true
                        onPlaceOrder(shippingAddress) { ok ->
                            isOrdering = false
                            if (ok) showCheckoutDialog = false
                        }
                    }
                ) {
                    Text(if (isOrdering) "Placing..." else "Place Order")
                }
            },
            dismissButton = {
                TextButton(enabled = !isOrdering, onClick = { showCheckoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .background(Color(0xFFF5F5F5))
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val isVideoSlide = product.isVideoProduct && page == frames.size
                    if (isVideoSlide) {
                        MomiVideoPlayer(
                            mediaId = product.id,
                            localVideoPath = null,
                            videoData = product.videoData,
                            videoChunksCount = product.videoChunksCount,
                            remoteCollection = "marketProducts",
                            videoDurationMs = product.videoDurationMs,
                            fallbackImageKey = product.imageKey,
                            fallbackLocalImagePath = null,
                            fallbackImageData = product.imageData,
                            filterTag = "NONE",
                            isPlaying = pagerState.currentPage == page,
                            initialMuted = false,
                            showMuteButton = true,
                            showDurationBadge = true,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val frame = frames.getOrElse(page) { frames.first() }
                        PostMediaImage(
                            imageKey = frame.imageKey,
                            localImagePath = null,
                            imageData = frame.imageData,
                            filterTag = "NONE",
                            contentDescription = product.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (totalSlides > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        val slideLabel = if (product.isVideoProduct && pagerState.currentPage == frames.size) {
                            "Video"
                        } else {
                            "${pagerState.currentPage + 1}/$totalSlides"
                        }
                        Text(
                            text = slideLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.storeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3665F3)
                    )
                    if (product.sellerVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified merchant",
                            tint = Color(0xFF3665F3),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Verified by Admin",
                            fontSize = 11.sp,
                            color = Color(0xFF3665F3),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "· Pending Admin Verification",
                            fontSize = 11.sp,
                            color = Color(0xFF6B6B6B)
                        )
                    }
                }

                Text(
                    text = product.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191919)
                )

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = formatUsd(product.priceUsd),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF191919)
                    )
                    if (product.discountPercent > 0L) {
                        Text(
                            text = formatUsd(product.originalPriceUsd),
                            fontSize = 14.sp,
                            color = Color(0xFF6B6B6B),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            text = "${product.discountPercent}% OFF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53238)
                        )
                    }
                }

                if (product.freeShipping || product.isFeatured) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (product.isFeatured) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF3665F3))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Editor's Pick",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (product.freeShipping) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE53238))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Free shipping",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (!merchant?.tagline.isNullOrBlank()) {
                    Text(
                        text = "Store: ${merchant?.tagline}",
                        fontSize = 12.sp,
                        color = Color(0xFF6B6B6B)
                    )
                }

                if (product.description.isNotBlank()) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E0E0))
                    Text(
                        text = product.description,
                        fontSize = 14.sp,
                        color = Color(0xFF191919),
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (canCurateEditorial && onToggleFeatured != null) {
                    OutlinedButton(
                        onClick = { onToggleFeatured(!product.isFeatured) },
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("market_feature_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (product.isFeatured) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = Color(0xFF3665F3)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (product.isFeatured) "Remove from Editor's Picks" else "Feature as Editor's Pick",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3665F3)
                        )
                    }
                }

                if (isOwnListing) {
                    OutlinedButton(
                        onClick = onDeleteListing,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Listing", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onMessageSeller,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Message", fontWeight = FontWeight.Bold, color = Color(0xFF191919))
                        }
                        Button(
                            onClick = { showCheckoutDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF191919),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("market_buy_now_button")
                        ) {
                            Text("Buy Now", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onReportListing != null) {
                        TextButton(
                            onClick = onReportListing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("market_report_listing_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                tint = Color(0xFFE53238),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Report listing to Safety Team",
                                color = Color(0xFFE53238),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MerchantStudioSheet(
    profile: UserProfile?,
    ownMerchant: MerchantAccount?,
    onDismiss: () -> Unit,
    onSaveMerchant: (storeName: String, tagline: String, category: String, location: String, onDone: (Boolean) -> Unit) -> Unit,
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
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var storeName by remember(ownMerchant) { mutableStateOf(ownMerchant?.storeName ?: "") }
    var tagline by remember(ownMerchant) { mutableStateOf(ownMerchant?.tagline ?: "") }
    var storeLocation by remember(ownMerchant) { mutableStateOf(ownMerchant?.location ?: "") }
    var editingStoreOnly by remember(ownMerchant) { mutableStateOf(ownMerchant == null) }

    var productTitle by remember { mutableStateOf("") }
    var productDesc by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("Collectibles") }
    var priceText by remember { mutableStateOf("") }
    var originalPriceText by remember { mutableStateOf("") }
    var freeShipping by remember { mutableStateOf(true) }
    var stockText by remember { mutableStateOf("5") }
    var selectedImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var localVideoPath by remember { mutableStateOf<String?>(null) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var isBusy by remember { mutableStateOf(false) }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isBusy = true
            scope.launch {
                val paths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { MediaAssets.copyPickedImageToInternalStorage(context, it) }.take(5)
                }
                if (paths.isNotEmpty()) {
                    selectedImagePaths = paths
                }
                isBusy = false
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    MediaAssets.copyPickedVideoToInternalStorage(context, uri)
                }
                if (extracted != null) {
                    localVideoPath = extracted.localVideoPath
                    videoDurationMs = extracted.durationMs
                    if (selectedImagePaths.isEmpty() && extracted.thumbnailPath != null) {
                        selectedImagePaths = listOf(extracted.thumbnailPath)
                    }
                }
                isBusy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (ownMerchant == null || editingStoreOnly) "Merchant Store Account" else "Publish Marketplace Item",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191919)
                    )
                    if (ownMerchant != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = if (ownMerchant.isVerified) Color(0xFF3665F3) else Color(0xFF6B6B6B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (ownMerchant.isVerified) {
                                    "Verified Merchant (${ownMerchant.storeName})"
                                } else {
                                    "Awaiting Admin Verification (${ownMerchant.storeName})"
                                },
                                fontSize = 12.sp,
                                color = if (ownMerchant.isVerified) Color(0xFF3665F3) else Color(0xFF6B6B6B)
                            )
                        }
                    }
                }
                if (ownMerchant != null) {
                    TextButton(onClick = { editingStoreOnly = !editingStoreOnly }) {
                        Text(if (editingStoreOnly) "Add Product" else "Edit Store")
                    }
                }
            }

            if (ownMerchant == null || editingStoreOnly) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("merchant_store_name_input")
                )
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    label = { Text("Store Bio / Specialty") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeLocation,
                    onValueChange = { storeLocation = it },
                    label = { Text("City / Country") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Once created, your store can list products immediately. Your Verified Merchant badge is reviewed and granted exclusively by the Momi admin.",
                    fontSize = 12.sp,
                    color = Color(0xFF6B6B6B)
                )
                Button(
                    enabled = storeName.isNotBlank() && !isBusy,
                    onClick = {
                        isBusy = true
                        onSaveMerchant(storeName, tagline, productCategory, storeLocation) { ok ->
                            isBusy = false
                            if (ok) editingStoreOnly = false
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF191919)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isBusy) "Saving..." else "Save Merchant Account", fontWeight = FontWeight.Bold)
                }
            } else {
                // Multi-photo & video pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            multiPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos (${selectedImagePaths.size}/5)", maxLines = 1)
                    }
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            videoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (localVideoPath != null) "Video Added" else "Add Video", maxLines = 1)
                    }
                }

                if (selectedImagePaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedImagePaths) { path ->
                            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) {
                                PostMediaImage(
                                    imageKey = "img_post_coastal",
                                    localImagePath = path,
                                    filterTag = "NONE",
                                    contentDescription = "Selected product photo",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable { selectedImagePaths = selectedImagePaths - path },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = productTitle,
                    onValueChange = { productTitle = it },
                    label = { Text("Product Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("market_product_title_input")
                )
                OutlinedTextField(
                    value = productDesc,
                    onValueChange = { productDesc = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (USD) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = originalPriceText,
                        onValueChange = { originalPriceText = it },
                        label = { Text("Was (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MarketCategories.drop(1)) { cat ->
                        val active = productCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) Color(0xFF191919) else Color(0xFFEDEDED))
                                .clickable { productCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else Color(0xFF191919)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Offer Free Shipping badge", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(checked = freeShipping, onCheckedChange = { freeShipping = it })
                }

                val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                val parsedOrig = originalPriceText.toDoubleOrNull() ?: 0.0
                val parsedStock = stockText.toLongOrNull() ?: 1L
                Button(
                    enabled = productTitle.isNotBlank() && parsedPrice > 0.0 && !isBusy,
                    onClick = {
                        isBusy = true
                        onPublishProduct(
                            ownMerchant,
                            productTitle,
                            productDesc,
                            productCategory,
                            parsedPrice,
                            parsedOrig,
                            freeShipping,
                            selectedImagePaths,
                            localVideoPath,
                            videoDurationMs,
                            parsedStock
                        ) { ok ->
                            isBusy = false
                            if (ok) onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF191919)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Publish Product", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketOrdersSheet(
    currentUserId: String,
    orders: List<MarketOrder>,
    onUpdateOrderStatus: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Marketplace Orders",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191919)
            )
            if (orders.isEmpty()) {
                Text(
                    text = "No orders placed or received yet.",
                    color = Color(0xFF6B6B6B),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders, key = { it.id }) { order ->
                        val isSeller = order.sellerUid == currentUserId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.75.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = order.productTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatUsd(order.priceUsd),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = if (isSeller) "Buyer: @${order.buyerUsername}" else "Store: ${order.sellerStoreName}",
                                fontSize = 12.sp,
                                color = Color(0xFF6B6B6B)
                            )
                            Text(
                                text = "Ship to: ${order.shippingAddress}",
                                fontSize = 12.sp,
                                color = Color(0xFF6B6B6B)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status: ${order.status}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3665F3)
                                )
                                if (isSeller && order.status != "DELIVERED") {
                                    val next = if (order.status == "PLACED") "SHIPPED" else "DELIVERED"
                                    TextButton(onClick = { onUpdateOrderStatus(order.id, next) }) {
                                        Text("Mark $next")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminVerifyMerchantsSheet(
    merchants: List<MerchantAccount>,
    onSetVerified: (merchantUid: String, verified: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF3665F3))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verify Merchant Accounts (Admin)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191919)
                )
            }
            Text(
                text = "Approve or revoke merchant verification badges across Momi Marketplace.",
                fontSize = 12.sp,
                color = Color(0xFF6B6B6B)
            )
            if (merchants.isEmpty()) {
                Text(
                    text = "No merchant accounts registered yet.",
                    color = Color(0xFF6B6B6B),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(merchants, key = { it.userId }) { merchant ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LumaAvatar(
                                username = merchant.ownerUsername,
                                avatarKey = merchant.avatarKey,
                                avatarData = merchant.avatarData,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = merchant.storeName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${merchant.ownerUsername} · ${merchant.category}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B6B6B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = merchant.isVerified,
                                onCheckedChange = { next ->
                                    onSetVerified(merchant.userId, next)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
