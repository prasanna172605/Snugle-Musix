package com.snuggle.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.models.SongItem
import com.snuggle.music.extensions.toMediaItem
import com.snuggle.music.models.MediaMetadata
import com.snuggle.music.models.toMediaMetadata
import com.snuggle.music.providers.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class JioSaavnQueue(
    val initialSong: SongItem,
    override val preloadItem: MediaMetadata? = initialSong.toMediaMetadata(),
) : Queue {
    private var hasLoadedNextPage = false

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(Dispatchers.IO) {
            val initialMediaItem = initialSong.toMediaMetadata().toMediaItem()
            try {
                val registry = ProviderRegistry.activeInstance
                val provider = registry?.jioSaavnProvider
                val rawRecommendations = provider?.getRecommendations(initialSong.id)?.getOrNull().orEmpty()
                    .filter { it.id != initialSong.id }

                val finalSongs = if (rawRecommendations.isNotEmpty() && registry != null) {
                    val region = registry.regionalPreferenceManager.getActiveRegion()
                    val taste = registry.tasteProfileEngine.computeTasteProfile()
                    registry.recommendationScorer.rankAndDiversify(
                        items = rawRecommendations,
                        region = region,
                        tasteProfile = taste,
                        seedItem = initialSong.toMediaMetadata()
                    )
                } else {
                    rawRecommendations
                }

                val recommendedMediaItems = finalSongs
                    .map { it.toMediaMetadata().toMediaItem() }

                val allItems = mutableListOf(initialMediaItem).apply {
                    addAll(recommendedMediaItems)
                }
                Queue.Status(
                    title = initialSong.title,
                    items = allItems,
                    mediaItemIndex = 0,
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch JioSaavn recommendations for initial queue")
                Queue.Status(
                    title = initialSong.title,
                    items = listOf(initialMediaItem),
                    mediaItemIndex = 0,
                )
            }
        }
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()

    companion object {
        fun radio(song: MediaMetadata): JioSaavnQueue {
            return JioSaavnQueue(
                initialSong = SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                    album = song.album?.let { com.music.innertube.models.Album(name = it.title, id = it.id) },
                    duration = song.duration,
                    thumbnail = song.thumbnailUrl.orEmpty(),
                    explicit = song.explicit,
                    endpoint = null,
                ),
                preloadItem = song,
            )
        }
    }
}
