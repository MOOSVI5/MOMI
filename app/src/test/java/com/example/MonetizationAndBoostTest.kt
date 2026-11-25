package com.example

import com.example.data.model.AdMonetizationConfig
import com.example.data.model.CreditTransaction
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.Post
import com.example.data.model.sortFeedWithBoostPriority
import com.example.data.supabase.MomiSupabaseSchema
import com.example.ui.theme.AppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationAndBoostTest {

    @Test
    fun sortFeedWithBoostPriorityPlacesRecommendedThenSuperBoostThenSpotlight() {
        val regular = Post(id = "post_1", caption = "regular")
        val spotlight = Post(id = "post_2", isBoosted = true, boostTier = "SPOTLIGHT")
        val superBoost = Post(id = "post_3", isBoosted = true, boostTier = "SUPER_BOOST")
        val recommended = Post(
            id = "post_4",
            isBoosted = true,
            boostTier = "OWNER_RECOMMENDED",
            recommendedLabel = "Recommended by Momi"
        )
        val showcaseFakeBoost = Post(
            id = "showcase_post_5",
            isBoosted = true,
            boostTier = "OWNER_RECOMMENDED"
        )

        val sorted = sortFeedWithBoostPriority(
            listOf(showcaseFakeBoost, regular, spotlight, superBoost, recommended)
        )

        assertEquals(
            listOf("post_4", "post_3", "post_2", "showcase_post_5", "post_1"),
            sorted.map { it.id }
        )
    }

    @Test
    fun colorSchemesAreStrictlyLimitedToThreeApprovedPalettes() {
        assertEquals(
            listOf(
                AppColorScheme.WHITE_GREY,
                AppColorScheme.EMERALD_MAROON,
                AppColorScheme.GOLD_EMERALD
            ),
            AppColorScheme.entries
        )
        assertEquals(86_400_000L, MomiSupabaseSchema.STORY_TTL_MS)
    }

    @Test
    fun boostOptionsMatchAllowedCreditCosts() {
        val allowedCosts = setOf(100L, 250L, 500L)
        assertTrue(MomiCreditCatalog.boostOptions.isNotEmpty())
        MomiCreditCatalog.boostOptions.forEach { option ->
            assertTrue(option.creditsCost in allowedCosts)
            assertTrue(option.badgeLabel.isNotBlank())
        }
    }

    @Test
    fun perkLookupAndTransactionTypesStayConsistent() {
        assertNotNull(MomiCreditCatalog.perkByKey("SUPPORTER_FRAME"))
        assertNotNull(MomiCreditCatalog.perkByKey("VIP_PATRON_PASS"))
        assertEquals("POST_BOOST", CreditTransaction.TYPE_POST_BOOST)
        assertEquals("OWNER_GRANT", CreditTransaction.TYPE_OWNER_GRANT)
        assertEquals("OWNER_ADMIN", CreditTransaction.METHOD_OWNER_ADMIN)

        val defaultConfig = AdMonetizationConfig()
        assertTrue(defaultConfig.admobEnabled)
        assertTrue(defaultConfig.startIoEnabled)
        assertEquals("2.3", defaultConfig.latestVersionName)
        assertFalse(defaultConfig.partnerPromoTitle.isBlank())
    }
}
