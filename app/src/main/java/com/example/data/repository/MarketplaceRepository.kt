package com.example.data.repository

import com.example.data.model.MarketOrder
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MarketplaceRepository(private val db: FirebaseFirestore) {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before using Momi Marketplace.")
    }

    private fun merchantsCol() = db.collection("merchants")
    private fun productsCol() = db.collection("marketProducts")
    private fun ordersCol() = db.collection("marketOrders")

    fun observeMerchants(currentUid: String): Flow<List<MerchantAccount>> {
        val colRef = merchantsCol()
        return colRef
            .limit(50L)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(MerchantAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<MerchantAccount> { it.isVerified }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.storeName }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeProducts(currentUid: String): Flow<List<MarketProduct>> {
        val colRef = productsCol()
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50L)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(MarketProduct::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<MarketProduct> { it.isFeatured }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeOrdersForUser(currentUid: String): Flow<List<MarketOrder>> {
        val colRef = ordersCol()
        return colRef
            .where(
                Filter.or(
                    Filter.equalTo("buyerUid", currentUid),
                    Filter.equalTo("sellerUid", currentUid)
                )
            )
            .limit(50L)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(MarketOrder::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<MarketOrder> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun createOrUpdateMerchant(
        profile: UserProfile,
        storeName: String,
        tagline: String,
        category: String,
        location: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanStore = storeName.trim().take(80)
        require(cleanStore.isNotEmpty()) { "Store name must not be empty" }
        val docRef = merchantsCol().document(uid)

        val existing = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        try {
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("ownerUsername", profile.username.ifBlank { "merchant" }.take(50))
                    put("storeName", cleanStore)
                    put("tagline", tagline.trim().take(160))
                    put("category", category.trim().ifBlank { "Collectibles" }.take(40))
                    put("location", location.trim().take(120))
                    put("avatarKey", profile.avatarKey.ifBlank { "avatar_aria" }.take(64))
                    if (!profile.avatarData.isNullOrBlank()) put("avatarData", profile.avatarData)
                    put("isVerified", false)
                    put("totalSales", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("storeName", cleanStore)
                    put("tagline", tagline.trim().take(160))
                    put("category", category.trim().ifBlank { "Collectibles" }.take(40))
                    put("location", location.trim().take(120))
                    if (!profile.avatarData.isNullOrBlank()) put("avatarData", profile.avatarData)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun setMerchantVerified(
        targetMerchantUid: String,
        verified: Boolean,
        adminEmail: String = "syali6511@gmail.com"
    ): Result<Unit> = runCatching {
        requireUserId()
        require(targetMerchantUid.isNotBlank()) { "Merchant UID must not be blank" }
        val docRef = merchantsCol().document(targetMerchantUid)
        try {
            val updates = hashMapOf<String, Any?>(
                "isVerified" to verified,
                "verifiedByEmail" to if (verified) adminEmail else null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun publishProduct(
        profile: UserProfile,
        merchant: MerchantAccount,
        title: String,
        description: String,
        category: String,
        priceUsd: Double,
        originalPriceUsd: Double,
        freeShipping: Boolean,
        imageData: String?,
        extraImageDatas: List<String> = emptyList(),
        videoData: String? = null,
        videoChunks: List<String> = emptyList(),
        videoDurationMs: Long = 0L,
        stockCount: Long = 10L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Product title cannot be empty" }
        require(priceUsd > 0.0) { "Product price must be positive" }

        val origPrice = if (originalPriceUsd > priceUsd) originalPriceUsd else priceUsd
        val discount = if (origPrice > priceUsd) {
            (((origPrice - priceUsd) / origPrice) * 100.0).toLong().coerceIn(1L, 95L)
        } else {
            0L
        }

        val docRef = productsCol().document()
        val safeExtras = extraImageDatas.filter { it.isNotBlank() }.take(4)
        val payload = buildMap<String, Any> {
            put("sellerUid", uid)
            put("sellerUsername", profile.username.ifBlank { "merchant" }.take(50))
            put("storeName", merchant.storeName.ifBlank { profile.displayName }.take(80))
            put("title", cleanTitle)
            put("description", description.trim().take(1000))
            put("category", category.trim().ifBlank { "Collectibles" }.take(40))
            put("priceUsd", priceUsd)
            put("originalPriceUsd", origPrice)
            put("discountPercent", discount)
            put("freeShipping", freeShipping)
            put("imageKey", "img_post_coastal")
            if (!imageData.isNullOrBlank()) put("imageData", imageData)
            if (safeExtras.isNotEmpty()) put("extraImageDatas", safeExtras)
            if (!videoData.isNullOrBlank()) put("videoData", videoData)
            if (videoChunks.isNotEmpty()) put("videoChunksCount", videoChunks.size.toLong())
            put("videoDurationMs", videoDurationMs.coerceIn(0L, 3_600_000L))
            put("stockCount", stockCount.coerceIn(1L, 9999L))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            if (videoChunks.isNotEmpty()) {
                try {
                    val chunksCol = docRef.collection("videoChunks")
                    videoChunks.chunked(5).forEachIndexed { groupIdx, slice ->
                        val batch = db.batch()
                        slice.forEachIndexed { offset, data ->
                            val globalIdx = groupIdx * 5 + offset
                            batch.set(
                                chunksCol.document("chunk_${globalIdx.toString().padStart(3, '0')}"),
                                mapOf(
                                    "index" to globalIdx.toLong(),
                                    "data" to data,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        batch.commit().await()
                    }
                } catch (chunkErr: Exception) {
                    runCatching { docRef.delete().await() }
                    throw chunkErr
                }
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> = runCatching {
        requireUserId()
        require(productId.isNotBlank()) { "Product ID must not be blank" }
        val docRef = productsCol().document(productId)
        try {
            runCatching {
                val chunksSnap = docRef.collection("videoChunks").get().await()
                if (!chunksSnap.isEmpty) {
                    chunksSnap.documents.chunked(20).forEach { group ->
                        val batch = db.batch()
                        group.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun setProductFeatured(productId: String, featured: Boolean): Result<Unit> = runCatching {
        requireUserId()
        require(productId.isNotBlank()) { "Product ID must not be blank" }
        val docRef = productsCol().document(productId)
        try {
            docRef.update(
                mapOf(
                    "isFeatured" to featured,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun placeOrder(
        buyerProfile: UserProfile,
        product: MarketProduct,
        shippingAddress: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanAddress = shippingAddress.trim().take(240)
        require(cleanAddress.isNotEmpty()) { "Shipping address is required" }
        require(product.sellerUid.isNotBlank() && product.sellerUid != uid) {
            "You cannot buy your own listing."
        }
        val orderRef = ordersCol().document()
        val payload = buildMap<String, Any> {
            put("buyerUid", uid)
            put("buyerUsername", buyerProfile.username.ifBlank { "buyer" }.take(50))
            put("sellerUid", product.sellerUid)
            put("sellerStoreName", product.storeName.ifBlank { product.sellerUsername }.take(80))
            put("productId", product.id)
            put("productTitle", product.title.take(120))
            put("productImageKey", product.imageKey)
            if (!product.imageData.isNullOrBlank()) put("productImageData", product.imageData)
            put("priceUsd", product.priceUsd)
            put("shippingAddress", cleanAddress)
            put("status", "PLACED")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            orderRef.set(payload).await()
            orderRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, orderRef.path)
            throw e
        }
    }

    suspend fun updateOrderStatus(orderId: String, nextStatus: String): Result<Unit> = runCatching {
        requireUserId()
        val cleanStatus = nextStatus.uppercase()
        require(cleanStatus in setOf("PLACED", "SHIPPED", "DELIVERED")) { "Invalid order status" }
        val docRef = ordersCol().document(orderId)
        try {
            docRef.update(
                mapOf(
                    "status" to cleanStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private fun logFlowErrorIfSignedIn(
        uid: String,
        error: Throwable,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
