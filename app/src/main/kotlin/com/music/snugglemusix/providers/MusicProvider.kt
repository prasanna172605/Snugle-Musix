package com.snuggle.music.providers

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.snuggle.music.constants.AudioQuality

sealed interface ProviderItem {
    val id: String
    val providerId: String
}

enum class ProviderSearchFilter {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}

data class ProviderSearchResult(
    val items: List<YTItem>,
    val continuation: String? = null
)

data class StreamResolution(
    val mediaUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val codecs: String,
    val sampleRate: Int = 44100,
    val contentLength: Long = 0L,
    val expiresInSeconds: Long = 86400L
)

interface MusicProvider {
    val id: String
    val displayName: String

    suspend fun search(query: String, filter: ProviderSearchFilter = ProviderSearchFilter.ALL, page: Int = 1): Result<ProviderSearchResult>
    suspend fun searchSuggestions(query: String): Result<List<String>>
    suspend fun getSong(id: String): Result<SongItem>
    suspend fun getAlbum(id: String): Result<Pair<AlbumItem, List<SongItem>>>
    suspend fun getPlaylist(id: String): Result<Pair<PlaylistItem, List<SongItem>>>
    suspend fun resolveStream(id: String, quality: AudioQuality): Result<StreamResolution>
}
