package com.example

import com.example.data.model.AdMonetizationConfig
import com.example.data.model.CreditTransaction
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.Post
import com.example.data.model.normalizeMonetagDirectUrl
import com.example.data.model.resolveGamezopLaunchUrl
import com.example.data.model.sanitizeGamezopPublisherId
import com.example.data.model.sortFeedWithBoostPriority
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
    fun normalizeMonetagDirectUrlEnforcesHttpsAndTrims() {
        assertEquals(
            "https://otieu.com/4/9001234",
            normalizeMonetagDirectUrl("  https://otieu.com/4/9001234  ")
        )
        assertEquals("", normalizeMonetagDirectUrl("http://insecure.example.com"))
        assertEquals("", normalizeMonetagDirectUrl("javascript:alert(1)"))
        assertEquals("", normalizeMonetagDirectUrl(null))
    }

    @Test
    fun resolveGamezopLaunchUrlHandlesPortalUrlAndPublisherIdSafely() {
        assertEquals("", resolveGamezopLaunchUrl(AdMonetizationConfig()))

        val withPubIdOnly = AdMonetizationConfig(
            gamezopEnabled = true,
            gamezopPublisherId = " 9012;bad "
        )
        assertEquals("9012bad", sanitizeGamezopPublisherId(" 9012;bad "))
        assertEquals("https://www.gamezop.com/?id=9012bad", resolveGamezopLaunchUrl(withPubIdOnly))

        val withPortalOverride = AdMonetizationConfig(
            gamezopEnabled = true,
            gamezopPublisherId = "9012",
            gamezopPortalUrl = "https://4829.play.gamezop.com"
        )
        assertEquals("https://4829.play.gamezop.com", resolveGamezopLaunchUrl(withPortalOverride))

        val withInsecurePortal = AdMonetizationConfig(
            gamezopEnabled = true,
            gamezopPublisherId = "9012",
            gamezopPortalUrl = "http://insecure.gamezop.com"
        )
        assertEquals("https://www.gamezop.com/?id=9012", resolveGamezopLaunchUrl(withInsecurePortal))
    }

    @Test
    fun boostOptionsMatchFirestoreAllowedCreditCosts() {
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
        assertTrue(defaultConfig.monetagEnabled)
        assertFalse(defaultConfig.gamezopEnabled)
        assertFalse(defaultConfig.partnerPromoTitle.isBlank())
    }
}
