package com.example

import com.example.data.model.AdMonetizationConfig
import com.example.data.model.MarketProduct
import com.example.data.model.MerchantAccount
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.model.UserWallet
import com.example.data.model.ShowcaseCatalog
import com.example.data.supabase.MomiSupabaseClient
import com.example.data.supabase.MomiSupabaseSchema
import com.example.data.supabase.SupabaseRealtimeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun communityPagesGroupsShowcaseAndAnnouncementFieldsAreSupported() {
        val page = com.example.data.model.MomiPage(
            id = "pg_1",
            ownerUid = "u1",
            ownerUsername = "ali",
            name = "Momi Lens",
            category = "Photography",
            followersCount = 15
        )
        val group = com.example.data.model.MomiGroup(
            id = "gr_1",
            ownerUid = "u1",
            ownerUsername = "ali",
            name = "Film Club",
            privacy = "PUBLIC",
            membersCount = 8
        )
        val starter = com.example.data.model.ShowcaseAccount(
            username = "elena.maris",
            displayName = "Elena Maris",
            isAuthorized = true
        )
        val cfg = AdMonetizationConfig(
            announcementId = "ann_42",
            announcementTitle = "Momi v2.2 In-App Notes",
            announcementBody = "Pages & Groups are live!",
            latestVersionName = "2.2",
            releaseNotes = "Smooth live rooms and starter console"
        )
        val post = Post(
            id = "p_comm",
            userId = "u1",
            authorUsername = "ali",
            authorDisplayName = "Ali",
            pageId = page.id,
            communityName = page.name
        )

        assertEquals("Momi Lens", page.name)
        assertEquals("Film Club", group.name)
        assertTrue(starter.isAuthorized)
        assertEquals("ann_42", cfg.announcementId)
        assertEquals("2.2", cfg.latestVersionName)
        assertEquals("pg_1", post.pageId)
        assertTrue(
            MomiSupabaseSchema.TABLES.containsAll(
                listOf("pages", "page_follows", "groups", "group_members", "showcase_accounts")
            )
        )
    }

    @Test
    fun realtimeWebSocketHelpersAndShowcaseCatalogWork() {
        val wsUrl = SupabaseRealtimeClient.buildRealtimeWebSocketUrl(
            rawBaseUrl = "https://qotuvugkapaspowhbsbf.supabase.co/",
            rawAnonKey = "sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln"
        )
        assertEquals(
            "wss://qotuvugkapaspowhbsbf.supabase.co/realtime/v1/websocket?apikey=sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln&vsn=1.0.0",
            wsUrl
        )
        assertEquals(
            "",
            SupabaseRealtimeClient.buildRealtimeWebSocketUrl(
                rawBaseUrl = "https://qotuvugkapaspowhbsbf.supabase.co",
                rawAnonKey = "sb_secret_forbidden"
            )
        )

        val joinPayload = SupabaseRealtimeClient.buildJoinPayload(ref = "3", accessToken = "jwt_abc")
        assertTrue(joinPayload.contains("\"event\":\"phx_join\""))
        assertTrue(joinPayload.contains("\"topic\":\"realtime:public\""))
        assertTrue(joinPayload.contains("\"access_token\":\"jwt_abc\""))
        assertTrue(joinPayload.contains("\"postgres_changes\""))

        val parsed = SupabaseRealtimeClient.parseRealtimeChangeMessage(
            """
            {
              "topic": "realtime:public",
              "event": "postgres_changes",
              "payload": {
                "data": {
                  "schema": "public",
                  "table": "chat_messages",
                  "type": "INSERT",
                  "record": { "id": "msg_1", "thread_id": "u1__u2" }
                }
              }
            }
            """.trimIndent()
        )
        assertNotNull(parsed)
        assertEquals("chat_messages", parsed?.table)
        assertEquals("INSERT", parsed?.eventType)
        assertEquals("u1__u2", parsed?.recordFields?.get("thread_id"))

        assertEquals(6, ShowcaseCatalog.PERSONAS.size)
        assertTrue(ShowcaseCatalog.COMMENTS.size >= 4)
    }

    @Test
    fun jwtExpiryDetectionIdentifiesExpiredAndFreshTokens() {
        fun encodeB64Url(text: String): String {
            val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.toByteArray(Charsets.UTF_8))
            return encoded
        }
        val header = encodeB64Url("""{"alg":"HS256","typ":"JWT"}""")
        val pastExpSec = 1_700_000_000L
        val futureExpSec = (System.currentTimeMillis() / 1000L) + 3_600L
        val expiredJwt = "$header.${encodeB64Url("""{"sub":"u1","exp":$pastExpSec}""")}.sig"
        val freshJwt = "$header.${encodeB64Url("""{"sub":"u1","exp":$futureExpSec}""")}.sig"

        assertEquals(pastExpSec * 1000L, MomiSupabaseSchema.extractJwtExpiryEpochMs(expiredJwt))
        assertTrue(MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(expiredJwt))
        assertFalse(MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(freshJwt))
        assertFalse(MomiSupabaseSchema.isJwtExpiredOrExpiringSoon(""))
    }
}
