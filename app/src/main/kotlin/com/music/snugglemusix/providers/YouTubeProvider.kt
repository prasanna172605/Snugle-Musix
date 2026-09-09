package com.snuggle.music.providers

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.YouTubeClient
import com.snuggle.music.constants.AudioQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeProvider @Inject constructor() : MusicProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "YouTube Music"

    companion object {
        const val PROVIDER_ID = "youtube"
    }

    override suspend fun search(
        query: String,
        filter: ProviderSearchFilter,
        page: Int
    ): Result<ProviderSearchResult> = runCatching {
        val ytFilter = when (filter) {
            ProviderSearchFilter.ALL -> null
            ProviderSearchFilter.SONGS -> YouTube.SearchFilter.FILTER_SONG
            ProviderSearchFilter.ALBUMS -> YouTube.SearchFilter.FILTER_ALBUM
            ProviderSearchFilter.ARTISTS -> YouTube.SearchFilter.FILTER_ARTIST
            ProviderSearchFilter.PLAYLISTS -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
        }

        if (ytFilter == null) {
            val summary = YouTube.searchSummary(query).getOrThrow()
            val allItems = mutableListOf<YTItem>()
            summary.summaries.forEach { s ->
                allItems.addAll(s.items)
            }
            ProviderSearchResult(items = allItems, continuation = null)
        } else {
            val result = YouTube.search(query, ytFilter).getOrThrow()
            ProviderSearchResult(items = result.items, continuation = result.continuation)
        }
    }

    override suspend fun searchSuggestions(query: String): Result<List<String>> = runCatching {
        val suggestions = YouTube.searchSuggestions(query).getOrThrow()
        suggestions.queries
    }

    override suspend fun getSong(id: String): Result<SongItem> = runCatching {
        val queue = YouTube.queue(listOf(id)).getOrThrow()
        queue.firstOrNull() ?: throw NoSuchElementException("Song not found for id $id")
    }

    override suspend fun getAlbum(id: String): Result<Pair<AlbumItem, List<SongItem>>> = runCatching {
        val albumPage = YouTube.album(id).getOrThrow()
        albumPage.album to albumPage.songs
    }

    override suspend fun getPlaylist(id: String): Result<Pair<PlaylistItem, List<SongItem>>> = runCatching {
        val playlistPage = YouTube.playlist(id).getOrThrow()
        playlistPage.playlist to playlistPage.songs
    }

    override suspend fun resolveStream(id: String, quality: AudioQuality): Result<StreamResolution> {
        // YouTube stream resolution is handled through YTPlayerUtils in MusicService
        throw UnsupportedOperationException("YouTube streams are deciphered dynamically via YTPlayerUtils")
    }
}
