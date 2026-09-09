package com.snuggle.music.providers

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.jiosaavn.JioSaavnClient
import com.music.jiosaavn.JioSaavnCrypto
import com.music.jiosaavn.models.SaavnAlbum
import com.music.jiosaavn.models.SaavnPlaylist
import com.music.jiosaavn.models.SaavnSong
import com.snuggle.music.constants.AudioQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnProvider @Inject constructor() : MusicProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "JioSaavn"

    companion object {
        const val PROVIDER_ID = "jiosaavn"
        const val PREFIX = "js:"
        const val ALBUM_PREFIX = "js:album:"
        const val PLAYLIST_PREFIX = "js:playlist:"

        fun isJioSaavnId(id: String): Boolean = id.startsWith(PREFIX)
        fun extractRawId(id: String): String = id.removePrefix(PREFIX).removePrefix("album:").removePrefix("playlist:")
    }

    override suspend fun search(
        query: String,
        filter: ProviderSearchFilter,
        page: Int
    ): Result<ProviderSearchResult> = runCatching {
        when (filter) {
            ProviderSearchFilter.ALL, ProviderSearchFilter.SONGS -> {
                val songs = JioSaavnClient.searchSongs(query, page = page).getOrThrow()
                ProviderSearchResult(
                    items = songs.results.map { it.toSongItem() },
                    continuation = if (songs.results.isNotEmpty()) (page + 1).toString() else null
                )
            }
            ProviderSearchFilter.ALBUMS -> {
                val albums = JioSaavnClient.searchAlbums(query, page = page).getOrThrow()
                ProviderSearchResult(
                    items = albums.results.map { it.toAlbumItem() },
                    continuation = if (albums.results.isNotEmpty()) (page + 1).toString() else null
                )
            }
            ProviderSearchFilter.PLAYLISTS -> {
                val playlists = JioSaavnClient.searchPlaylists(query, page = page).getOrThrow()
                ProviderSearchResult(
                    items = playlists.results.map { it.toPlaylistItem() },
                    continuation = if (playlists.results.isNotEmpty()) (page + 1).toString() else null
                )
            }
            ProviderSearchFilter.ARTISTS -> {
                val artists = JioSaavnClient.searchArtists(query, page = page).getOrThrow()
                ProviderSearchResult(
                    items = artists.results.mapNotNull { artist ->
                        val name = artist.name ?: return@mapNotNull null
                        val thumbnail = JioSaavnCrypto.getHighQualityThumbnail(artist.image)
                            ?.takeIf { !it.contains("artist-default") } ?: ""
                        ArtistItem(
                            id = "$PREFIX${artist.id ?: name}",
                            title = cleanHtmlEntities(name),
                            thumbnail = thumbnail,
                            channelId = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        )
                    },
                    continuation = if (artists.results.isNotEmpty()) (page + 1).toString() else null
                )
            }
        }
    }

    /**
     * Resolve high-resolution official artist cover thumbnail from JioSaavn
     */
    suspend fun getArtistCover(artistName: String): String? = runCatching {
        if (artistName.isBlank()) return@runCatching null
        val res = JioSaavnClient.searchArtists(artistName, page = 1, limit = 5).getOrNull()
        val match = res?.results?.firstOrNull { artist ->
            val name = artist.name ?: ""
            name.equals(artistName, ignoreCase = true) ||
            name.contains(artistName, ignoreCase = true) ||
            artistName.contains(name, ignoreCase = true)
        } ?: res?.results?.firstOrNull()

        match?.image?.let { img ->
            JioSaavnCrypto.getHighQualityThumbnail(img)?.takeIf { !it.contains("artist-default") }
        }
    }.getOrNull()

    override suspend fun searchSuggestions(query: String): Result<List<String>> = runCatching {
        val songs = JioSaavnClient.searchSongs(query, page = 1, limit = 5).getOrNull()
        songs?.results?.mapNotNull { it.title }?.distinct() ?: emptyList()
    }

    override suspend fun getSong(id: String): Result<SongItem> = runCatching {
        val rawId = extractRawId(id)
        val song = JioSaavnClient.getSongDetails(rawId).getOrThrow()
        song.toSongItem()
    }

    /**
     * Get related/recommended songs for a JioSaavn song
     */
    suspend fun getRecommendations(id: String): Result<List<SongItem>> = runCatching {
        val rawId = extractRawId(id)
        val recoSongs = JioSaavnClient.getRecommendations(rawId).getOrThrow()
        recoSongs.map { it.toSongItem() }
    }

    override suspend fun getAlbum(id: String): Result<Pair<AlbumItem, List<SongItem>>> = runCatching {
        val rawId = extractRawId(id)
        val albumDetails = JioSaavnClient.getAlbumDetails(rawId).getOrThrow()
        val songs = albumDetails.songs.map { it.toSongItem() }
        val albumItem = AlbumItem(
            browseId = "$ALBUM_PREFIX${albumDetails.id}",
            playlistId = "$ALBUM_PREFIX${albumDetails.id}",
            title = albumDetails.title ?: albumDetails.name ?: "Unknown Album",
            artists = albumDetails.primary_artists?.split(",")?.map { Artist(name = it.trim(), id = null) },
            year = albumDetails.year?.toIntOrNull(),
            thumbnail = JioSaavnCrypto.getHighQualityThumbnail(albumDetails.image) ?: "",
            explicit = false,
            description = albumDetails.release_date
        )
        albumItem to songs
    }

    /**
     * Get JioSaavn charts/trending editorial playlists, optionally filtered by languages
     */
    suspend fun getCharts(languages: List<String> = emptyList()): Result<List<PlaylistItem>> = runCatching {
        val charts = JioSaavnClient.getCharts(languages).getOrThrow()
        charts.map { chart ->
            PlaylistItem(
                id = "$PLAYLIST_PREFIX${chart.id}",
                title = cleanHtmlEntities(chart.title ?: "Top Chart"),
                author = Artist(name = "JioSaavn", id = null),
                songCountText = chart.count?.let { "$it songs" },
                thumbnail = JioSaavnCrypto.getHighQualityThumbnail(chart.image),
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
        }
    }

    /**
     * Get trending search terms, optionally filtered by languages
     */
    suspend fun getTrendingQueries(languages: List<String> = emptyList()): Result<List<String>> = runCatching {
        JioSaavnClient.getTopSearches(languages).getOrThrow()
    }

    override suspend fun getPlaylist(id: String): Result<Pair<PlaylistItem, List<SongItem>>> = runCatching {
        val rawId = extractRawId(id)
        val playlistDetails = JioSaavnClient.getPlaylistDetails(rawId).getOrThrow()
        val songs = playlistDetails.list.map { it.toSongItem() }
        val playlistItem = PlaylistItem(
            id = "$PLAYLIST_PREFIX${playlistDetails.id}",
            title = playlistDetails.title ?: playlistDetails.listname ?: "JioSaavn Playlist",
            author = Artist(name = "JioSaavn", id = null),
            songCountText = playlistDetails.list_count?.let { "$it songs" } ?: "${songs.size} songs",
            thumbnail = JioSaavnCrypto.getHighQualityThumbnail(playlistDetails.image),
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
        playlistItem to songs
    }

    override suspend fun resolveStream(id: String, quality: AudioQuality): Result<StreamResolution> = runCatching {
        val rawId = extractRawId(id)
        val song = JioSaavnClient.getSongDetails(rawId).getOrThrow()
        val encryptedUrl = song.more_info?.encryptedMediaUrl
            ?: throw IllegalStateException("No encrypted_media_url found for JioSaavn song $rawId")

        val decryptedUrl = JioSaavnCrypto.decryptMediaUrl(encryptedUrl)
            ?: throw IllegalStateException("Failed to decrypt media url for JioSaavn song $rawId")

        val targetBitrate = when (quality) {
            AudioQuality.LOSSLESS, AudioQuality.SAAVN, AudioQuality.MP4_LATM -> 320
            AudioQuality.OPUS -> 160
        }

        val finalStreamUrl = JioSaavnCrypto.getMediaUrlWithQuality(decryptedUrl, targetBitrate)

        StreamResolution(
            mediaUrl = finalStreamUrl,
            mimeType = "audio/mp4",
            bitrate = targetBitrate * 1000,
            codecs = "mp4a.40.2",
            sampleRate = 44100,
            contentLength = 0L,
            expiresInSeconds = 86400L * 7L // Saavn CDN URLs have long lifetimes
        )
    }

    private fun SaavnSong.toSongItem(): SongItem {
        val cleanTitle = cleanHtmlEntities(title ?: "Unknown Title")
        val info = more_info
        val artistsList = info?.artistMap?.primary_artists?.map {
            Artist(name = cleanHtmlEntities(it.name ?: "Unknown Artist"), id = null)
        }?.takeIf { it.isNotEmpty() } ?: subtitle?.split(",")?.map {
            Artist(name = cleanHtmlEntities(it.trim()), id = null)
        } ?: emptyList()

        val albumItem = info?.album?.let {
            Album(name = cleanHtmlEntities(it), id = "$ALBUM_PREFIX${info.albumId.orEmpty()}")
        }

        val highResThumb = JioSaavnCrypto.getHighQualityThumbnail(image) ?: ""
        val durationSec = info?.duration?.toIntOrNull()

        return SongItem(
            id = "$PREFIX$id",
            title = cleanTitle,
            artists = artistsList,
            album = albumItem,
            duration = durationSec,
            thumbnail = highResThumb,
            explicit = explicitContent == "1",
            endpoint = null
        )
    }

    private fun SaavnAlbum.toAlbumItem(): AlbumItem {
        val cleanTitle = cleanHtmlEntities(title ?: "Unknown Album")
        val artistsList = subtitle?.split(",")?.map {
            Artist(name = cleanHtmlEntities(it.trim()), id = null)
        }
        val highResThumb = JioSaavnCrypto.getHighQualityThumbnail(image) ?: ""

        return AlbumItem(
            browseId = "$ALBUM_PREFIX$id",
            playlistId = "$ALBUM_PREFIX$id",
            title = cleanTitle,
            artists = artistsList,
            year = year?.toIntOrNull(),
            thumbnail = highResThumb,
            explicit = explicitContent == "1"
        )
    }

    private fun SaavnPlaylist.toPlaylistItem(): PlaylistItem {
        val cleanTitle = cleanHtmlEntities(title ?: "Unknown Playlist")
        val highResThumb = JioSaavnCrypto.getHighQualityThumbnail(image)

        return PlaylistItem(
            id = "$PLAYLIST_PREFIX$id",
            title = cleanTitle,
            author = subtitle?.let { Artist(name = cleanHtmlEntities(it), id = null) },
            songCountText = more_info?.song_count?.let { "$it songs" },
            thumbnail = highResThumb,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
    }

    private fun cleanHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
