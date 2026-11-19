package com.example

import com.example.data.model.CreditTransaction
import com.example.data.model.LiveFrame
import com.example.data.model.LiveStream
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.UserWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletAndLiveStreamTest {

    @Test
    fun creditPackagesMatchAllowedFirestoreRuleDenominations() {
        val allowedAmounts = setOf(50L, 200L, 500L, 1200L, 3000L)
        assertEquals(allowedAmounts.size, MomiCreditCatalog.packages.size)
        MomiCreditCatalog.packages.forEach { pkg ->
            assertTrue("Package credits ${pkg.credits} must match rules", pkg.credits in allowedAmounts)
            assertNotNull(MomiCreditCatalog.packageByProductId(pkg.productId))
            assertTrue(pkg.fallbackPriceUsd > 0.0)
        }
    }

    @Test
    fun giftsAndPerksStayWithinTransactionBounds() {
        MomiCreditCatalog.gifts.forEach { gift ->
            assertTrue("Gift ${gift.key} cost must be in [1..5000]", gift.creditsCost in 1L..5000L)
        }
        MomiCreditCatalog.perks.forEach { perk ->
            assertTrue("Perk ${perk.key} cost must be in [1..5000]", perk.creditsCost in 1L..5000L)
        }
    }

    @Test
    fun accountTierProgressesWithPurchasesAndUnlockedPerks() {
        assertEquals(
            MomiCreditCatalog.TIER_STANDARD,
            MomiCreditCatalog.tierForTotalPurchased(50L, emptyList())
        )
        assertEquals(
            MomiCreditCatalog.TIER_SUPPORTER,
            MomiCreditCatalog.tierForTotalPurchased(200L, emptyList())
        )
        assertEquals(
            MomiCreditCatalog.TIER_CREATOR_PRO,
            MomiCreditCatalog.tierForTotalPurchased(0L, listOf("CREATOR_BOOST"))
        )
        assertEquals(
            MomiCreditCatalog.TIER_VIP,
            MomiCreditCatalog.tierForTotalPurchased(0L, listOf("VIP_PATRON_PASS"))
        )
    }

    @Test
    fun verificationTierPreservesVerifiedAndElevatesForPriorityPerks() {
        assertEquals(
            MomiCreditCatalog.VERIFICATION_NONE,
            MomiCreditCatalog.verificationTierForPerks(MomiCreditCatalog.VERIFICATION_NONE, emptyList())
        )
        assertEquals(
            MomiCreditCatalog.VERIFICATION_CREATOR_PRO,
            MomiCreditCatalog.verificationTierForPerks(
                MomiCreditCatalog.VERIFICATION_NONE,
                listOf("CREATOR_BOOST")
            )
        )
        assertEquals(
            MomiCreditCatalog.VERIFICATION_PENDING_PERK,
            MomiCreditCatalog.verificationTierForPerks(
                MomiCreditCatalog.VERIFICATION_NONE,
                listOf("PRIORITY_VERIFICATION")
            )
        )
        assertEquals(
            MomiCreditCatalog.VERIFICATION_VERIFIED,
            MomiCreditCatalog.verificationTierForPerks(
                MomiCreditCatalog.VERIFICATION_VERIFIED,
                listOf("PRIORITY_VERIFICATION")
            )
        )
    }

    @Test
    fun liveStreamAndWalletDefaultsAreSafeAndConsistent() {
        val stream = LiveStream(id = "u1", userId = "u1")
        assertTrue(stream.isLive)
        assertEquals(0L, stream.giftsCredits)

        val frame = LiveFrame(id = "latest", userId = "u1")
        assertTrue(frame.cameraEnabled)
        assertFalse(frame.micMuted)
        assertEquals(0L, frame.seq)

        val wallet = UserWallet(id = "u1", userId = "u1")
        assertEquals(0L, wallet.balance)
        assertEquals(MomiCreditCatalog.TIER_STANDARD, wallet.accountTier)
        assertEquals(MomiCreditCatalog.VERIFICATION_NONE, wallet.verificationTier)

        val tx = CreditTransaction(id = "tx1", txId = "tx1")
        assertEquals(CreditTransaction.TYPE_TOP_UP, tx.type)
        assertEquals(CreditTransaction.METHOD_GOOGLE_PLAY, tx.paymentMethod)
    }
}
