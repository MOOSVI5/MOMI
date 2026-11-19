package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.example.data.model.MomiCreditCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayPurchaseEvent {
    data class Credited(
        val productId: String,
        val orderId: String,
        val purchaseToken: String
    ) : PlayPurchaseEvent

    data class Pending(val productId: String) : PlayPurchaseEvent
    data class Cancelled(val message: String) : PlayPurchaseEvent
    data class Failed(val message: String) : PlayPurchaseEvent
}

data class PlayBillingUiState(
    val isConnecting: Boolean = true,
    val isPlayBillingReady: Boolean = false,
    val localizedPrices: Map<String, String> = emptyMap(),
    val loadedProductIds: Set<String> = emptySet(),
    val statusMessage: String = "Connecting to Google Play Billing..."
)

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlayBillingUiState())
    val uiState: StateFlow<PlayBillingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlayPurchaseEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlayPurchaseEvent> = _events.asSharedFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private val processingTokens = mutableSetOf<String>()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refreshCatalogAndPendingPurchases()
            return
        }
        _uiState.value = _uiState.value.copy(
            isConnecting = true,
            statusMessage = "Connecting to Google Play Billing..."
        )
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        isPlayBillingReady = true,
                        statusMessage = "Google Play Billing connected"
                    )
                    refreshCatalogAndPendingPurchases()
                } else {
                    Log.w(TAG, "Play Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        isPlayBillingReady = false,
                        statusMessage = "Google Play Store unavailable (${billingResult.responseCode})"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isPlayBillingReady = false,
                    statusMessage = "Google Play service disconnected — auto-reconnecting"
                )
            }
        })
    }

    fun refreshCatalogAndPendingPurchases() {
        if (!billingClient.isReady) return
        queryInAppProducts()
        queryUnconsumedPurchases()
    }

    private fun queryInAppProducts() {
        val products = MomiCreditCatalog.packages.map { pkg ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(pkg.productId)
                .setProductType(ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList
                val prices = mutableMapOf<String, String>()
                val loadedIds = mutableSetOf<String>()
                synchronized(productDetailsMap) {
                    productDetailsMap.clear()
                    list.forEach { details ->
                        productDetailsMap[details.productId] = details
                        loadedIds.add(details.productId)
                        val formatted = details.oneTimePurchaseOfferDetailsList
                            ?.firstOrNull()
                            ?.formattedPrice
                        if (!formatted.isNullOrBlank()) {
                            prices[details.productId] = formatted
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isPlayBillingReady = true,
                    localizedPrices = prices,
                    loadedProductIds = loadedIds,
                    statusMessage = if (loadedIds.isNotEmpty()) {
                        "Google Play Store pricing ready (${loadedIds.size} packs)"
                    } else {
                        "Play Store connected · Publish credit SKUs in Play Console for live checkout"
                    }
                )
            } else {
                Log.w(TAG, "queryProductDetailsAsync error ${billingResult.responseCode}: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryUnconsumedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                handlePurchaseList(purchases)
            }
        }
    }

    fun launchCreditPurchase(
        activity: Activity?,
        productId: String,
        accountUid: String
    ): Boolean {
        if (activity == null) {
            _events.tryEmit(PlayPurchaseEvent.Failed("Unable to locate foreground screen for checkout."))
            return false
        }
        if (!billingClient.isReady) {
            _events.tryEmit(PlayPurchaseEvent.Failed("Google Play Billing is not ready on this device."))
            return false
        }
        val details = synchronized(productDetailsMap) { productDetailsMap[productId] }
        if (details == null) {
            _events.tryEmit(
                PlayPurchaseEvent.Failed(
                    "Credit pack \"$productId\" is not published in Google Play Console yet."
                )
            )
            return false
        }
        val offerToken = details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                if (!offerToken.isNullOrBlank()) {
                    setOfferToken(offerToken)
                }
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .apply {
                if (accountUid.isNotBlank()) {
                    setObfuscatedAccountId(accountUid.take(64))
                }
            }
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            _events.tryEmit(
                PlayPurchaseEvent.Failed(
                    "Google Play checkout could not start (${result.responseCode})."
                )
            )
            return false
        }
        return true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    handlePurchaseList(purchases)
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                _events.tryEmit(PlayPurchaseEvent.Cancelled("Purchase cancelled."))
            }
            else -> {
                _events.tryEmit(
                    PlayPurchaseEvent.Failed(
                        billingResult.debugMessage.ifBlank { "Google Play purchase error (${billingResult.responseCode})." }
                    )
                )
            }
        }
    }

    private fun handlePurchaseList(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val token = purchase.purchaseToken
                    val alreadyInFlight = synchronized(processingTokens) {
                        !processingTokens.add(token)
                    }
                    if (!alreadyInFlight) {
                        consumeAndEmitPurchase(purchase)
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    val productId = purchase.products.firstOrNull().orEmpty()
                    if (productId.isNotBlank()) {
                        _events.tryEmit(PlayPurchaseEvent.Pending(productId))
                    }
                }
                else -> Unit
            }
        }
    }

    private fun consumeAndEmitPurchase(purchase: Purchase) {
        scope.launch {
            try {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val consumeResult = withContext(Dispatchers.IO) {
                    billingClient.consumePurchase(consumeParams)
                }
                if (consumeResult.billingResult.responseCode == BillingResponseCode.OK) {
                    val productId = purchase.products.firstOrNull().orEmpty()
                    val orderId = purchase.orderId?.takeIf { it.isNotBlank() }
                        ?: "GPA.${purchase.purchaseToken.take(20)}"
                    _events.tryEmit(
                        PlayPurchaseEvent.Credited(
                            productId = productId,
                            orderId = orderId,
                            purchaseToken = purchase.purchaseToken
                        )
                    )
                } else {
                    _events.tryEmit(
                        PlayPurchaseEvent.Failed(
                            "Could not finalize consumable Play purchase (${consumeResult.billingResult.responseCode})."
                        )
                    )
                }
            } finally {
                synchronized(processingTokens) {
                    processingTokens.remove(purchase.purchaseToken)
                }
            }
        }
    }

    fun release() {
        runCatching { billingClient.endConnection() }
        scope.cancel()
    }

    private companion object {
        const val TAG = "MomiPlayBilling"
    }
}
