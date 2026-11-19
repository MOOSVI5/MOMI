package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AccountPerkItem
import com.example.data.model.CreditPackageOption
import com.example.data.model.CreditTransaction
import com.example.data.model.LiveGiftItem
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
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
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WalletRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Wallet.")
    }

    private fun walletDoc(uid: String) = db.collection("wallets").document(uid)
    private fun txCol() = db.collection("creditTransactions")
    private fun liveStreamsCol() = db.collection("liveStreams")
    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeWallet(userId: String): Flow<UserWallet?> {
        val docRef = walletDoc(userId)
        val path = docRef.path
        return docRef.snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserWallet::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.GET, path)
                throw error
            }
    }

    fun observeTransactions(userId: String): Flow<List<CreditTransaction>> {
        val colRef = txCol()
        val path = colRef.path
        return colRef
            .where(
                Filter.or(
                    Filter.equalTo("senderUid", userId),
                    Filter.equalTo("recipientUid", userId)
                )
            )
            .limit(TRANSACTIONS_LIMIT)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(CreditTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<CreditTransaction> { it.createdAt ?: Timestamp(0, 0) }
                            .thenBy { it.txId }
                    )
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUnclaimedGifts(userId: String): Flow<List<CreditTransaction>> {
        val colRef = txCol()
        val path = colRef.path
        return colRef
            .whereEqualTo("recipientUid", userId)
            .whereEqualTo("recipientClaimed", false)
            .limit(UNCLAIMED_GIFTS_LIMIT)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(CreditTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                    .filter { it.type == CreditTransaction.TYPE_GIFT && !it.recipientClaimed }
            }
            .catch { error ->
                logFlowErrorIfSignedIn(userId, error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun ensureWalletInitialized(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = walletDoc(uid)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "balance" to 0L,
                    "totalEarned" to 0L,
                    "totalSpent" to 0L,
                    "totalPurchased" to 0L,
                    "accountTier" to MomiCreditCatalog.TIER_STANDARD,
                    "verificationTier" to MomiCreditCatalog.VERIFICATION_NONE,
                    "unlockedPerks" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun recordCreditTopUp(
        profile: UserProfile,
        pkg: CreditPackageOption,
        paymentMethod: String,
        paymentRef: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        ensureWalletInitialized().getOrThrow()

        val walletRef = walletDoc(uid)
        val txId = generateTxId("topup")
        val txRef = txCol().document(txId)
        val cleanPaymentRef = paymentRef.trim().ifBlank { "REF_${txId}" }.take(200)
        val safeUsername = profile.username.ifBlank { "creator" }.take(50)

        try {
            db.runTransaction { transaction ->
                val walletSnap = transaction.get(walletRef)
                if (!walletSnap.exists()) {
                    throw IllegalStateException("Wallet is not initialized yet.")
                }
                val currentBalance = (walletSnap.getLong("balance") ?: 0L).coerceAtLeast(0L)
                val currentPurchased = (walletSnap.getLong("totalPurchased") ?: 0L).coerceAtLeast(0L)
                @Suppress("UNCHECKED_CAST")
                val unlockedPerks = (walletSnap.get("unlockedPerks") as? List<String>).orEmpty()

                val nextBalance = currentBalance + pkg.credits
                val nextPurchased = currentPurchased + pkg.credits
                val nextTier = MomiCreditCatalog.tierForTotalPurchased(nextPurchased, unlockedPerks)

                val txPayload = mapOf(
                    "txId" to txId,
                    "type" to CreditTransaction.TYPE_TOP_UP,
                    "senderUid" to uid,
                    "senderUsername" to safeUsername,
                    "recipientUid" to uid,
                    "recipientUsername" to safeUsername,
                    "creditsAmount" to pkg.credits,
                    "priceUsd" to pkg.fallbackPriceUsd,
                    "paymentMethod" to paymentMethod,
                    "paymentRef" to cleanPaymentRef,
                    "recipientClaimed" to true,
                    "status" to "COMPLETED",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(txRef, txPayload)
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to nextBalance,
                        "totalPurchased" to nextPurchased,
                        "accountTier" to nextTier,
                        "lastTxId" to txId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            txId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, walletRef.path)
            throw e
        }
    }

    suspend fun sendGift(
        senderProfile: UserProfile,
        recipientUid: String,
        recipientUsername: String,
        gift: LiveGiftItem,
        streamId: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(recipientUid.isNotBlank() && recipientUid != uid) {
            "Choose another Momi creator to send a gift."
        }
        ensureWalletInitialized().getOrThrow()

        val walletRef = walletDoc(uid)
        val txId = generateTxId("gift")
        val txRef = txCol().document(txId)
        val cleanStreamId = streamId?.trim()?.takeIf { it.isNotBlank() }
        val streamRef = cleanStreamId?.let { liveStreamsCol().document(it) }
        val senderName = senderProfile.username.ifBlank { "creator" }.take(50)
        val receiverName = recipientUsername.ifBlank { "creator" }.take(50)

        try {
            db.runTransaction { transaction ->
                val walletSnap = transaction.get(walletRef)
                val streamSnap = if (streamRef != null) transaction.get(streamRef) else null

                if (!walletSnap.exists()) {
                    throw IllegalStateException("Please initialize your Momi wallet first.")
                }
                val currentBalance = (walletSnap.getLong("balance") ?: 0L).coerceAtLeast(0L)
                val currentSpent = (walletSnap.getLong("totalSpent") ?: 0L).coerceAtLeast(0L)
                if (currentBalance < gift.creditsCost) {
                    throw IllegalStateException(
                        "You need ${gift.creditsCost} credits for ${gift.name} (balance: $currentBalance)."
                    )
                }

                val txPayload = buildMap<String, Any> {
                    put("txId", txId)
                    put("type", CreditTransaction.TYPE_GIFT)
                    put("senderUid", uid)
                    put("senderUsername", senderName)
                    put("recipientUid", recipientUid)
                    put("recipientUsername", receiverName)
                    put("creditsAmount", gift.creditsCost)
                    put("priceUsd", 0.0)
                    put("paymentMethod", CreditTransaction.METHOD_WALLET_CREDITS)
                    put("giftKey", gift.key.take(64))
                    put("giftName", gift.name.take(64))
                    if (cleanStreamId != null) put("streamId", cleanStreamId)
                    put("recipientClaimed", false)
                    put("status", "COMPLETED")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                transaction.set(txRef, txPayload)
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to (currentBalance - gift.creditsCost),
                        "totalSpent" to (currentSpent + gift.creditsCost),
                        "lastTxId" to txId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (streamRef != null && streamSnap != null && streamSnap.exists() && streamSnap.getBoolean("isLive") == true) {
                    val currentGifts = (streamSnap.getLong("giftsCredits") ?: 0L).coerceAtLeast(0L)
                    transaction.update(
                        streamRef,
                        mapOf(
                            "giftsCredits" to (currentGifts + gift.creditsCost),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            // Post celebratory gift announcement in live chat & recipient's inbox best-effort
            if (cleanStreamId != null) {
                runCatching {
                    liveStreamsCol().document(cleanStreamId).collection("liveComments").document().set(
                        mapOf(
                            "userId" to uid,
                            "username" to senderName,
                            "avatarKey" to senderProfile.avatarKey.ifBlank { "avatar_aria" }.take(64),
                            "text" to "[GIFT] sent ${gift.emojiLabel} ${gift.name} (+${gift.creditsCost} cr)",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            runCatching {
                userDoc(recipientUid).collection("notifications").document().set(
                    mapOf(
                        "userId" to recipientUid,
                        "actorUid" to uid,
                        "actorUsername" to senderName,
                        "actorAvatarKey" to senderProfile.avatarKey.ifBlank { "avatar_aria" }.take(64),
                        "message" to "sent you a ${gift.emojiLabel} ${gift.name} gift (+${gift.creditsCost} credits).",
                        "timeLabel" to "now",
                        "section" to "Today",
                        "isFollowAction" to false,
                        "isFollowingBack" to false,
                        "isUnread" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            txId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    suspend fun claimGift(txItem: CreditTransaction): Result<Long> = runCatching {
        val uid = requireUserId()
        if (txItem.txId.isBlank() || txItem.recipientUid != uid || txItem.recipientClaimed) {
            return@runCatching 0L
        }
        ensureWalletInitialized().getOrThrow()

        val walletRef = walletDoc(uid)
        val txRef = txCol().document(txItem.txId)

        try {
            db.runTransaction { transaction ->
                val walletSnap = transaction.get(walletRef)
                val txSnap = transaction.get(txRef)
                if (!walletSnap.exists() || !txSnap.exists()) return@runTransaction 0L
                val claimed = txSnap.getBoolean("recipientClaimed") ?: true
                val receiver = txSnap.getString("recipientUid").orEmpty()
                val amount = (txSnap.getLong("creditsAmount") ?: 0L).coerceAtLeast(0L)
                if (claimed || receiver != uid || amount <= 0L) return@runTransaction 0L

                val currentBalance = (walletSnap.getLong("balance") ?: 0L).coerceAtLeast(0L)
                val currentEarned = (walletSnap.getLong("totalEarned") ?: 0L).coerceAtLeast(0L)

                transaction.update(
                    txRef,
                    mapOf(
                        "recipientClaimed" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to (currentBalance + amount),
                        "totalEarned" to (currentEarned + amount),
                        "lastTxId" to txItem.txId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                amount
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, txRef.path)
            throw e
        }
    }

    suspend fun unlockPerk(
        profile: UserProfile,
        perk: AccountPerkItem
    ): Result<String> = runCatching {
        val uid = requireUserId()
        ensureWalletInitialized().getOrThrow()

        val walletRef = walletDoc(uid)
        val txId = generateTxId("perk")
        val txRef = txCol().document(txId)
        val safeUsername = profile.username.ifBlank { "creator" }.take(50)

        try {
            db.runTransaction { transaction ->
                val walletSnap = transaction.get(walletRef)
                if (!walletSnap.exists()) {
                    throw IllegalStateException("Wallet is not ready yet.")
                }
                val currentBalance = (walletSnap.getLong("balance") ?: 0L).coerceAtLeast(0L)
                val currentSpent = (walletSnap.getLong("totalSpent") ?: 0L).coerceAtLeast(0L)
                val currentPurchased = (walletSnap.getLong("totalPurchased") ?: 0L).coerceAtLeast(0L)
                val currentVerification = walletSnap.getString("verificationTier")
                    ?: MomiCreditCatalog.VERIFICATION_NONE
                @Suppress("UNCHECKED_CAST")
                val existingPerks = (walletSnap.get("unlockedPerks") as? List<String>).orEmpty()

                if (perk.key in existingPerks) {
                    throw IllegalStateException("${perk.title} is already unlocked on your account.")
                }
                if (currentBalance < perk.creditsCost) {
                    throw IllegalStateException(
                        "You need ${perk.creditsCost} credits to unlock ${perk.title}."
                    )
                }

                val nextPerks = (existingPerks + perk.key).distinct()
                val nextTier = MomiCreditCatalog.tierForTotalPurchased(currentPurchased, nextPerks)
                val nextVerification = MomiCreditCatalog.verificationTierForPerks(currentVerification, nextPerks)

                transaction.set(
                    txRef,
                    mapOf(
                        "txId" to txId,
                        "type" to CreditTransaction.TYPE_PERK_UNLOCK,
                        "senderUid" to uid,
                        "senderUsername" to safeUsername,
                        "recipientUid" to uid,
                        "recipientUsername" to safeUsername,
                        "creditsAmount" to perk.creditsCost,
                        "priceUsd" to 0.0,
                        "paymentMethod" to CreditTransaction.METHOD_WALLET_CREDITS,
                        "perkKey" to perk.key,
                        "recipientClaimed" to true,
                        "status" to "COMPLETED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to (currentBalance - perk.creditsCost),
                        "totalSpent" to (currentSpent + perk.creditsCost),
                        "accountTier" to nextTier,
                        "verificationTier" to nextVerification,
                        "unlockedPerks" to nextPerks,
                        "lastTxId" to txId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            txId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, walletRef.path)
            throw e
        }
    }

    private fun generateTxId(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(20)
        return "tx_${prefix}_$suffix"
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

    companion object {
        const val TRANSACTIONS_LIMIT = 60L
        const val UNCLAIMED_GIFTS_LIMIT = 20L
    }
}
