package com.snuggle.music.personalization

enum class RegionProfile(
    val regionCode: String,
    val displayName: String,
    val languageDisplayName: String,
    val primaryLanguages: Map<MusicLanguage, Float>,
    val trendingStarterQuery: String,
    val starterPlaylistQueries: List<String>
) {
    TAMIL_NADU(
        regionCode = "IN-TN",
        displayName = "Tamil Nadu (Tamil)",
        languageDisplayName = "Tamil",
        primaryLanguages = mapOf(
            MusicLanguage.TAMIL to 0.85f,
            MusicLanguage.MALAYALAM to 0.25f,
            MusicLanguage.TELUGU to 0.20f,
            MusicLanguage.HINDI to 0.15f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Tamil Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Tamil 2026", "Latest Tamil Hits 2026", "Trending Tamil Songs", "Kollywood Hits 2026")
    ),
    KERALA(
        regionCode = "IN-KL",
        displayName = "Kerala (Malayalam)",
        languageDisplayName = "Malayalam",
        primaryLanguages = mapOf(
            MusicLanguage.MALAYALAM to 0.85f,
            MusicLanguage.TAMIL to 0.40f,
            MusicLanguage.HINDI to 0.15f,
            MusicLanguage.ENGLISH to 0.45f
        ),
        trendingStarterQuery = "Malayalam Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Malayalam 2026", "Latest Malayalam Hits 2026", "Trending Malayalam Songs", "Mollywood Hits 2026")
    ),
    KARNATAKA(
        regionCode = "IN-KA",
        displayName = "Karnataka (Kannada)",
        languageDisplayName = "Kannada",
        primaryLanguages = mapOf(
            MusicLanguage.KANNADA to 0.85f,
            MusicLanguage.TELUGU to 0.30f,
            MusicLanguage.TAMIL to 0.30f,
            MusicLanguage.HINDI to 0.25f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Kannada Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Kannada 2026", "Latest Kannada Hits 2026", "Trending Kannada Songs", "Sandalwood Hits 2026")
    ),
    ANDHRA_PRADESH(
        regionCode = "IN-AP",
        displayName = "Andhra Pradesh & Telangana (Telugu)",
        languageDisplayName = "Telugu",
        primaryLanguages = mapOf(
            MusicLanguage.TELUGU to 0.85f,
            MusicLanguage.TAMIL to 0.25f,
            MusicLanguage.HINDI to 0.25f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Telugu Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Telugu 2026", "Latest Telugu Hits 2026", "Trending Telugu Songs", "Tollywood Hits 2026")
    ),
    MAHARASHTRA(
        regionCode = "IN-MH",
        displayName = "Maharashtra (Marathi / Hindi)",
        languageDisplayName = "Marathi",
        primaryLanguages = mapOf(
            MusicLanguage.MARATHI to 0.75f,
            MusicLanguage.HINDI to 0.70f,
            MusicLanguage.ENGLISH to 0.45f
        ),
        trendingStarterQuery = "Marathi Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Marathi 2026", "Latest Marathi Hits 2026", "Trending Marathi Songs", "Bollywood Hits 2026")
    ),
    WEST_BENGAL(
        regionCode = "IN-WB",
        displayName = "West Bengal (Bengali)",
        languageDisplayName = "Bengali",
        primaryLanguages = mapOf(
            MusicLanguage.BENGALI to 0.80f,
            MusicLanguage.HINDI to 0.40f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Bengali Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Bengali 2026", "Latest Bengali Hits 2026", "Trending Bengali Songs", "Bangla Hits 2026")
    ),
    PUNJAB(
        regionCode = "IN-PB",
        displayName = "Punjab (Punjabi)",
        languageDisplayName = "Punjabi",
        primaryLanguages = mapOf(
            MusicLanguage.PUNJABI to 0.85f,
            MusicLanguage.HINDI to 0.40f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Punjabi Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Punjabi 2026", "Latest Punjabi Hits 2026", "Trending Punjabi Songs", "Punjabi Top 50 2026")
    ),
    GUJARAT(
        regionCode = "IN-GJ",
        displayName = "Gujarat (Gujarati / Hindi)",
        languageDisplayName = "Gujarati",
        primaryLanguages = mapOf(
            MusicLanguage.GUJARATI to 0.80f,
            MusicLanguage.HINDI to 0.55f,
            MusicLanguage.ENGLISH to 0.40f
        ),
        trendingStarterQuery = "Gujarati Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Gujarati 2026", "Latest Gujarati Hits 2026", "Trending Gujarati Songs", "Garba Hits 2026")
    ),
    NORTH_INDIA(
        regionCode = "IN-NORTH",
        displayName = "North / Central India (Hindi)",
        languageDisplayName = "Hindi",
        primaryLanguages = mapOf(
            MusicLanguage.HINDI to 0.85f,
            MusicLanguage.PUNJABI to 0.40f,
            MusicLanguage.ENGLISH to 0.45f
        ),
        trendingStarterQuery = "Hindi Top Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in Hindi 2026", "Latest Hindi Hits 2026", "Bollywood Trending 2026", "Hindi Top 50 2026")
    ),
    GLOBAL(
        regionCode = "GLOBAL",
        displayName = "Global (International / English)",
        languageDisplayName = "English",
        primaryLanguages = mapOf(
            MusicLanguage.ENGLISH to 0.85f,
            MusicLanguage.GLOBAL to 0.60f
        ),
        trendingStarterQuery = "Today's Top Hits 2026",
        starterPlaylistQueries = listOf("Top Songs in English 2026", "Global Top 50 2026", "Latest English Hits 2026", "Trending Pop 2026")
    );

    companion object {
        fun fromCode(code: String?): RegionProfile {
            if (code.isNullOrBlank()) return TAMIL_NADU
            return entries.firstOrNull { it.regionCode.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: TAMIL_NADU
        }
    }
}
