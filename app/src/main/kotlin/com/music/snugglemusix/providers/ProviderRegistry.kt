package com.snuggle.music.providers

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRegistry @Inject constructor(
    val youTubeProvider: YouTubeProvider,
    val jioSaavnProvider: JioSaavnProvider,
    val regionalPreferenceManager: com.snuggle.music.personalization.RegionalPreferenceManager,
    val tasteProfileEngine: com.snuggle.music.personalization.TasteProfileEngine,
    val recommendationScorer: com.snuggle.music.personalization.RecommendationScorer
) {
    val providers: List<MusicProvider> = listOf(youTubeProvider)

    init {
        activeInstance = this
    }

    fun getProvider(providerId: String): MusicProvider {
        return providers.firstOrNull { it.id == providerId } ?: youTubeProvider
    }

    fun getProviderForMediaId(mediaId: String): MusicProvider {
        return youTubeProvider
    }

    companion object {
        var activeInstance: ProviderRegistry? = null
            private set
    }
}
