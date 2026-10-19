package com.snuggle.music.personalization

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationScorer @Inject constructor() {

    /**
     * Scores a candidate YTItem blending location priors with evolving personal taste,
     * and heavily weighting similarity with the seed song currently playing.
     * Higher score indicates higher recommendation relevance.
     */
     fun scoreItem(
         item: YTItem,
         region: RegionProfile,
         tasteProfile: TasteProfile,
         seedItem: com.snuggle.music.models.MediaMetadata? = null
     ): Float {
         val lang = MusicLanguageClassifier.classify(item)
         val locationPrior = region.primaryLanguages[lang] ?: 0.10f

         // Progressive blending weight: in cold start, location prior is 90% (tasteWeight = 0.10)
         // In established/maturing state, tasteWeight reaches 0.85 - 0.95.
         val tasteWeight = when (tasteProfile.experienceLevel) {
             UserExperienceLevel.COLD_START -> 0.05f
             UserExperienceLevel.EARLY_EXPLORER -> 0.40f
             UserExperienceLevel.ESTABLISHED -> 0.75f
             UserExperienceLevel.MATURING -> 0.90f
         }

         val personalLanguageAffinity = tasteProfile.languageAffinities[lang] ?: 0.05f

         // Blended Language Signal
         var score = (locationPrior * (1f - tasteWeight)) + (personalLanguageAffinity * tasteWeight)

         // Artist Affinity Boost
         val itemArtists = when (item) {
             is SongItem -> item.artists.map { it.name.lowercase() }
             is AlbumItem -> item.artists?.map { it.name.lowercase() }.orEmpty()
             is ArtistItem -> listOf(item.title.lowercase())
             is PlaylistItem -> listOfNotNull(item.author?.name?.lowercase())
         }

         val matchesTopArtist = itemArtists.any { it in tasteProfile.topArtistNames }
         if (matchesTopArtist) {
             score += 0.35f
         }

         // Seed Song Coherence (Language & Artist matching with current song)
         if (seedItem != null) {
             val seedLang = MusicLanguageClassifier.classify(seedItem)
             if (seedLang != MusicLanguage.GLOBAL) {
                 if (lang == seedLang) {
                     // Strong boost for matching the language of the song the user chose to play
                     score += 0.60f
                 } else if (lang != MusicLanguage.ENGLISH) {
                     // Penalize different regional language (e.g. playing Tamil, don't recommend Telugu/Hindi)
                     score -= 0.50f
                 }
             }

             // Direct artist match with seed song
             val seedArtists = seedItem.artists.map { it.name.lowercase() }
             if (itemArtists.any { itemArtist -> seedArtists.any { seedArtist -> itemArtist.contains(seedArtist) || seedArtist.contains(itemArtist) } }) {
                 score += 0.50f
             }

             // Title era/keyword heuristic (e.g. 60s, 70s, 80s, retro, classic)
             val retroKeywords = listOf("remix", "edm", "club mix", "lofi", "trap")
             val seedIsRetro = seedItem.title.contains("remix", ignoreCase = true) || seedItem.title.contains("edm", ignoreCase = true)
             val itemTitleLower = when (item) {
                 is SongItem -> item.title.lowercase()
                 is AlbumItem -> item.title.lowercase()
                 is ArtistItem -> item.title.lowercase()
                 is PlaylistItem -> item.title.lowercase()
             }
             if (!seedIsRetro && retroKeywords.any { itemTitleLower.contains(it) }) {
                 score -= 0.30f
             }
         }

         return score
     }

    /**
     * Ranks a list of candidate YTItems with diversity injection to prevent echo chambers.
     */
    fun <T : YTItem> rankAndDiversify(
        items: List<T>,
        region: RegionProfile,
        tasteProfile: TasteProfile,
        seedItem: com.snuggle.music.models.MediaMetadata? = null
    ): List<T> {
        if (items.isEmpty()) return items

        val scored = items.map { item ->
            item to scoreItem(item, region, tasteProfile, seedItem)
        }.sortedByDescending { it.second }

        // Keep top scored with 15% random exploration slots to maintain discovery
        val result = mutableListOf<T>()
        val seenArtists = mutableSetOf<String>()

        for ((item, _) in scored) {
            val primaryArtist = when (item) {
                is SongItem -> item.artists.firstOrNull()?.name
                is AlbumItem -> item.artists?.firstOrNull()?.name
                is ArtistItem -> item.title
                is PlaylistItem -> item.author?.name
            }

            // Cap consecutive items from the exact same artist to 2
            if (primaryArtist != null && seenArtists.count { it == primaryArtist } >= 2 && scored.size > 8) {
                continue
            }
            if (primaryArtist != null) seenArtists.add(primaryArtist)
            result.add(item)
        }

        // Fill remaining items if any were pruned
        if (result.size < items.size) {
            for ((item, _) in scored) {
                if (!result.contains(item)) {
                    result.add(item)
                }
            }
        }

        return result
    }
}
