package com.example.data.model

data class ShowcasePersona(
    val username: String,
    val displayName: String,
    val avatarKey: String,
    val imageKey: String,
    val location: String,
    val category: String,
    val filterTag: String,
    val aspectRatio: String,
    val hashtags: List<String>,
    val caption: String
)

object ShowcaseCatalog {
    val PERSONAS = listOf(
        ShowcasePersona(
            username = "elena.maris",
            displayName = "Elena Maris",
            avatarKey = "avatar_elena",
            imageKey = "img_post_coastal",
            location = "Positano, Amalfi Coast",
            category = "Travel",
            filterTag = "NONE",
            aspectRatio = "1:1",
            hashtags = listOf("amalfi", "coastal", "momi"),
            caption = "Golden hour spilling over the cliffs before the ferries drift back in. #amalfi #coastal #momi"
        ),
        ShowcasePersona(
            username = "matcha.ritual",
            displayName = "Matcha Ritual",
            avatarKey = "avatar_matcha",
            imageKey = "img_post_coffee",
            location = "Lisbon, Chiado",
            category = "Coffee",
            filterTag = "WARM",
            aspectRatio = "4:5",
            hashtags = listOf("matcha", "cafeculture", "lisbon"),
            caption = "Morning ritual: stone-ground Uji matcha over crystal ice and warm pastry. #matcha #cafeculture #lisbon"
        ),
        ShowcasePersona(
            username = "atelier.nord",
            displayName = "Atelier Nord",
            avatarKey = "avatar_nord",
            imageKey = "img_post_architecture",
            location = "Copenhagen, Denmark",
            category = "Architecture",
            filterTag = "CINEMA",
            aspectRatio = "4:5",
            hashtags = listOf("architecture", "minimal", "nordic"),
            caption = "When raw concrete meets warm white oak and four o'clock sunbeams. #architecture #minimal #nordic"
        ),
        ShowcasePersona(
            username = "kenji.lens",
            displayName = "Kenji Takahashi",
            avatarKey = "avatar_kenji",
            imageKey = "img_post_neon_street",
            location = "Shinjuku, Tokyo",
            category = "Night",
            filterTag = "BLUE",
            aspectRatio = "1:1",
            hashtags = listOf("tokyo", "nightphotography", "street"),
            caption = "Silk blue & maroon neon reflections after a sudden midnight downpour. #tokyo #nightphotography #street"
        ),
        ShowcasePersona(
            username = "sora.frames",
            displayName = "Sora Nakamura",
            avatarKey = "avatar_sora",
            imageKey = "img_post_coffee",
            location = "Kyoto, Gion",
            category = "Food",
            filterTag = "VINTAGE",
            aspectRatio = "4:5",
            hashtags = listOf("kyoto", "espresso", "aesthetic"),
            caption = "Pour-over clouds and analog film grain at dawn. @atelier.nord #kyoto #espresso #aesthetic"
        ),
        ShowcasePersona(
            username = "layla.travels",
            displayName = "Layla Haddad",
            avatarKey = "avatar_layla",
            imageKey = "img_post_coastal",
            location = "Santorini, Greece",
            category = "Travel",
            filterTag = "PASTEL",
            aspectRatio = "16:9",
            hashtags = listOf("santorini", "sunset", "wanderlust"),
            caption = "Aegean blues fading into rose gold right on cue. @elena.maris #santorini #sunset #wanderlust"
        )
    )

    val COMMENTS = listOf(
        "Love the color tone on this shot!",
        "The lighting feels so calm and crisp.",
        "Saving this spot for my next trip.",
        "Framing and texture look incredible here.",
        "Cleanest shot on the feed today!"
    )
}
