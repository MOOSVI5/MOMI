package com.example

import com.example.data.model.AdMonetizationConfig
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.data.supabase.MomiSupabaseClient
import com.example.data.supabase.MomiSupabaseSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseMigrationTest {

    @Test
    fun normalizeSupabaseBaseUrlEnforcesHttpsAndBlocksUnsafeHosts() {
        assertEquals(
            "https://abcdefghijk.supabase.co",
            MomiSupabaseSchema.normalizeSupabaseBaseUrl("  https://ABCDEFGHIJK.supabase.co/  ")
        )
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("http://abcdefghijk.supabase.co"))
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("https://localhost:54321"))
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("https://127.0.0.1"))
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("https://192.168.1.5"))
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("https://user:pass@evil.supabase.co"))
        assertEquals("", MomiSupabaseSchema.normalizeSupabaseBaseUrl("https://ok.supabase.co?x=1"))
    }

    @Test
    fun safeClientAnonKeyRejectsSecretKeysAndBlanks() {
        assertFalse(MomiSupabaseSchema.isSafeClientAnonKey(""))
        assertFalse(MomiSupabaseSchema.isSafeClientAnonKey("short"))
        assertFalse(MomiSupabaseSchema.isSafeClientAnonKey("sb_secret_1234567890abcdef"))
        assertTrue(MomiSupabaseSchema.isSafeClientAnonKey("sb_publishable_1234567890abcdef"))
        assertTrue(MomiSupabaseSchema.isSafeClientAnonKey("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"))

        val unconfigured = MomiSupabaseClient(rawBaseUrl = "", rawAnonKey = "")
        assertFalse(unconfigured.isConfigured())

        val configured = MomiSupabaseClient(
            rawBaseUrl = "https://demo-project.supabase.co",
            rawAnonKey = "sb_publishable_demo_key_123456"
        )
        assertTrue(configured.isConfigured())

        val pubHeaders = MomiSupabaseSchema.buildAuthHeaders("sb_publishable_LbrQBb1234567890")
        assertEquals("sb_publishable_LbrQBb1234567890", pubHeaders["apikey"])
        assertFalse(pubHeaders.containsKey("Authorization"))

        val pubWithSession = MomiSupabaseSchema.buildAuthHeaders(
            anonKey = "sb_publishable_LbrQBb1234567890",
            accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.user_payload.sig"
        )
        assertEquals("sb_publishable_LbrQBb1234567890", pubWithSession["apikey"])
        assertEquals(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.user_payload.sig",
            pubWithSession["Authorization"]
        )

        val legacyHeaders = MomiSupabaseSchema.buildAuthHeaders("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.anon_payload.sig")
        assertEquals(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.anon_payload.sig",
            legacyHeaders["Authorization"]
        )
    }

    @Test
    fun migrationBundleMapsFirestoreEntitiesToPostgresSchemaColumns() {
        val profile = UserProfile(
            id = "uid_1",
            userId = "uid_1",
            username = "ali.momi",
            displayName = "Ali",
            partnerConsent = true,
            isVerified = true
        )
        val realPost = Post(
            id = "post_1",
            userId = "uid_1",
            authorUsername = "ali.momi",
            authorDisplayName = "Ali",
            caption = "Hello \"Supabase\"\n#momi",
            isBoosted = true,
            boostTier = "OWNER_RECOMMENDED",
            recommendedLabel = "Recommended by Momi"
        )
        val showcasePost = Post(
            id = "showcase_elena_1",
            userId = "uid_1",
            authorUsername = "elena.maris",
            authorDisplayName = "Elena"
        )
        val story = Story(
            id = "story_1",
            userId = "uid_1",
            username = "ali.momi",
            caption = "Story frame"
        )
        val showcaseStory = Story(
            id = "showcase_story_1",
            userId = "uid_1",
            username = "elena.maris"
        )
        val merchant = MerchantAccount(
            id = "uid_1",
            userId = "uid_1",
            ownerUsername = "ali.momi",
            storeName = "Ali Studio",
            isVerified = true
        )
        val product = MarketProduct(
            id = "prod_1",
            sellerUid = "uid_1",
            sellerUsername = "ali.momi",
            storeName = "Ali Studio",
            title = "Camera Strap",
            priceUsd = 29.99,
            isFeatured = true
        )
        val wallet = UserWallet(
            id = "uid_1",
            userId = "uid_1",
            balance = 500L,
            accountTier = "CREATOR_PRO",
            unlockedPerks = listOf("CREATOR_BOOST")
        )
        val cfg = AdMonetizationConfig(
            monetagEnabled = true,
            monetagDirectLinkUrl = "https://otieu.com/4/9001",
            gamezopEnabled = true,
            gamezopPublisherId = "9012"
        )

        val bundle = MomiSupabaseSchema.buildMigrationBundleMap(
            profiles = listOf(profile),
            posts = listOf(realPost, showcasePost),
            stories = listOf(story, showcaseStory),
            merchants = listOf(merchant),
            products = listOf(product),
            wallet = wallet,
            monetizationConfig = cfg
        )

        assertEquals("momi_supabase_v1", bundle["schemaVersion"])
        @Suppress("UNCHECKED_CAST")
        val tables = bundle["tables"] as Map<String, List<Map<String, Any?>>>
        assertEquals(1, tables["profiles"]?.size)
        assertEquals(1, tables["posts"]?.size)
        assertEquals(1, tables["stories"]?.size)
        assertEquals(1, tables["merchants"]?.size)
        assertEquals(1, tables["market_products"]?.size)
        assertEquals(1, tables["wallets"]?.size)
        assertEquals(1, tables["app_config"]?.size)

        val postRow = tables["posts"]!!.first()
        assertEquals("post_1", postRow["id"])
        assertEquals("OWNER_RECOMMENDED", postRow["boost_tier"])

        val appCfgRow = tables["app_config"]!!.first()
        assertEquals("monetization", appCfgRow["id"])
        assertEquals("9012", appCfgRow["gamezop_publisher_id"])

        val jsonText = MomiSupabaseSchema.buildMigrationBundleJson(
            profiles = listOf(profile),
            posts = listOf(realPost, showcasePost),
            stories = listOf(story, showcaseStory),
            merchants = listOf(merchant),
            products = listOf(product),
            wallet = wallet,
            monetizationConfig = cfg
        )
        assertTrue(jsonText.contains("\"schemaVersion\": \"momi_supabase_v1\""))
        assertTrue(jsonText.contains("Hello \\\"Supabase\\\"\\n#momi"))
        assertFalse(jsonText.contains("showcase_elena_1"))
        assertFalse(jsonText.contains("showcase_story_1"))
        assertTrue(MomiSupabaseSchema.TABLES.containsAll(listOf("profiles", "posts", "wallets", "app_config")))
    }
}
