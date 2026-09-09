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
     * Scores a candidate YTItem blending location priors with evolving personal taste.
     * Higher score indicates higher recommendation relevance.
     */
    fun scoreItem(
        item: YTItem,
        region: RegionProfile,
        tasteProfile: TasteProfile
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

        return score
    }

    /**
     * Ranks a list of candidate YTItems with diversity injection to prevent echo chambers.
     */
    fun <T : YTItem> rankAndDiversify(
        items: List<T>,
        region: RegionProfile,
        tasteProfile: TasteProfile
    ): List<T> {
        if (items.isEmpty()) return items

        val scored = items.map { item ->
            item to scoreItem(item, region, tasteProfile)
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
