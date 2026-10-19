

package com.snuggle.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import com.snuggle.music.constants.HideExplicitKey
import com.snuggle.music.constants.CustomUsernameKey
import com.snuggle.music.constants.HideVideoSongsKey
import com.snuggle.music.constants.HideYoutubeShortsKey
import com.snuggle.music.constants.InnerTubeCookieKey
import com.snuggle.music.constants.QuickPicks
import com.snuggle.music.constants.QuickPicksKey
import com.snuggle.music.db.MusicDatabase
import com.snuggle.music.db.entities.Album
import com.snuggle.music.db.entities.LocalItem
import com.snuggle.music.db.entities.Song
import com.snuggle.music.db.entities.SpeedDialItem
import com.snuggle.music.extensions.filterVideoSongs
import com.snuggle.music.extensions.toEnum
import com.snuggle.music.models.SimilarRecommendation
import com.snuggle.music.models.toMediaMetadata
import com.snuggle.music.db.entities.SongEntity
import com.snuggle.music.utils.SyncUtils
import com.snuggle.music.utils.dataStore
import com.snuggle.music.utils.get
import com.snuggle.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random
import com.snuggle.music.personalization.MusicLanguage
import com.snuggle.music.personalization.MusicLanguageClassifier
import com.snuggle.music.personalization.RecommendationScorer
import com.snuggle.music.personalization.RegionalPreferenceManager
import com.snuggle.music.personalization.TasteProfileEngine
import com.snuggle.music.personalization.UserExperienceLevel
import javax.inject.Inject

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val regionalPreferenceManager: RegionalPreferenceManager,
    val tasteProfileEngine: TasteProfileEngine,
    val recommendationScorer: RecommendationScorer,
    val providerRegistry: com.snuggle.music.providers.ProviderRegistry
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val hasEnoughListeningHistory: StateFlow<Boolean> =
        database.eventCount()
            .map { it >= 10 }
            .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            hasEnoughListeningHistory
        ) { pinned, keepListening, hasHistory ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            // Only populate speed dial from listening history if user has genuine listening history
            if (hasHistory && filled.size < targetSize) {
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)

        
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                
                val recentSong = database.events().first().firstOrNull()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.songs.take(15).forEach { ytSong ->
                                val localSong = database.song(ytSong.id).first()
                                if (localSong != null) {
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                } else {
                                    val newSong = Song(
                                        song = com.snuggle.music.db.entities.SongEntity(
                                            id = ytSong.id,
                                            title = ytSong.title,
                                            duration = ytSong.duration ?: -1,
                                            thumbnailUrl = ytSong.thumbnail,
                                            albumId = ytSong.album?.id,
                                            albumName = ytSong.album?.name
                                        ),
                                        artists = ytSong.artists.map { com.snuggle.music.db.entities.ArtistEntity(id = it.id ?: "", name = it.name) },
                                        album = ytSong.album?.let { com.snuggle.music.db.entities.AlbumEntity(id = it.id ?: "", title = it.name ?: "", songCount = 0, duration = 0) }
                                    )
                                    ytSimilarSongs.add(newSong)
                                }
                            }
                        }
                    }
                }

                
                val combined = (ytSimilarSongs + relatedSongs + forgotten)
                    .distinctBy { it.id }
                    .take(20)

                if (combined.isNotEmpty()) {
                    quickPicks.value = combined
                } else if (relatedSongs.isNotEmpty()) {
                    quickPicks.value = relatedSongs.shuffled().take(20)
                } else {
                    // Fallback to most played or any songs in database so suggestions always show
                    val allSongs = database.allSongs().first()
                    if (allSongs.isNotEmpty()) {
                        quickPicks.value = allSongs.shuffled().take(20)
                    }
                }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val region = regionalPreferenceManager.getActiveRegion()
        val tasteProfile = tasteProfileEngine.computeTasteProfile()
        val isColdStart = tasteProfile.experienceLevel == com.snuggle.music.personalization.UserExperienceLevel.COLD_START

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            if (artistSeeds.isNotEmpty() || songSeeds.isNotEmpty()) {
                artistSeeds.map { seed ->
                    launch(Dispatchers.IO) {
                        YouTube.artist(seed.id).onSuccess { page ->
                            page.sections.forEach { section ->
                                section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                    if (playlist.author?.name != "YouTube Music" &&
                                        playlist.author?.name != "YouTube" &&
                                        playlist.author?.name != "Playlist" &&
                                        playlist.author?.name != seed.artist.name &&
                                        !playlist.id.startsWith("RD") &&
                                        !playlist.id.startsWith("OLAK")
                                    ) {
                                        candidatePlaylists.add(playlist)
                                    }
                                }
                            }
                        }
                    }
                }

                songSeeds.map { seed ->
                    launch(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                        if (endpoint != null) {
                            YouTube.related(endpoint).onSuccess { page ->
                                page.playlists.forEach { playlist ->
                                    if (playlist.author?.name != "YouTube Music" &&
                                        playlist.author?.name != "YouTube" &&
                                        playlist.author?.name != "Playlist" &&
                                        !playlist.id.startsWith("RD") &&
                                        !playlist.id.startsWith("OLAK")
                                    ) {
                                        candidatePlaylists.add(playlist)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Cold-start fallback: query regional community playlists
                region.starterPlaylistQueries.take(3).map { query ->
                    launch(Dispatchers.IO) {
                        YouTube.searchSummary(query).onSuccess { searchSummary ->
                            searchSummary.summaries.forEach { summary ->
                                summary.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                    if (!playlist.id.startsWith("RD") && !playlist.id.startsWith("OLAK")) {
                                        candidatePlaylists.add(playlist)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val rankedCandidates = recommendationScorer.rankAndDiversify(
            items = candidatePlaylists.distinctBy { it.id },
            region = region,
            tasteProfile = tasteProfile
        ).take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            // Fetch YouTube candidate playlists
            val ytJobs = rankedCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }

            // Fetch JioSaavn charts & playlists aligned with region prior (cold-start) or evolving taste
            val targetLanguages = if (isColdStart) {
                region.primaryLanguages.entries
                    .sortedByDescending { it.value }
                    .filter { it.key != MusicLanguage.GLOBAL }
                    .map { it.key.name.lowercase() }
            } else {
                val dominantLangs = tasteProfile.languageAffinities.entries
                    .sortedByDescending { it.value }
                    .filter { it.key != MusicLanguage.GLOBAL }
                    .take(3)
                    .map { it.key.name.lowercase() }
                if (dominantLangs.isNotEmpty()) dominantLangs else {
                    region.primaryLanguages.entries
                        .sortedByDescending { it.value }
                        .filter { it.key != MusicLanguage.GLOBAL }
                        .map { it.key.name.lowercase() }
                }
            }

            ytJobs.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        getQuickPicks()

        forgottenFavorites.value = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled()
                .take(3)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val page = YouTube.artist(artist.id).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = artist,
                            items = page.sections
                                .flatMap { it.items }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .shuffled()
                .take(3)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = song,
                            items = page.songs
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = database.mostPlayedAlbums(fromTimeStamp, limit = 15).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled()
                .take(3)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            YouTube.artist(artistId).onSuccess { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            similarRecommendations.value = results.filterNotNull().shuffled()
        }
    }

    
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        val activeRegion = regionalPreferenceManager.getActiveRegion()
        val tasteProfile = tasteProfileEngine.computeTasteProfile()

        coroutineScope {
            launch(Dispatchers.IO) { getDailyDiscover() }
            launch(Dispatchers.IO) { getCommunityPlaylists() }
            launch(Dispatchers.IO) { loadSimilarRecommendations() }
            launch(Dispatchers.IO) {
                // Fetch official home page feed
                val homeResult = YouTube.home()

                // Determine dynamic starter query using two-stage personalization
                val topArtists = database.mostPlayedArtists(10).first()
                val isColdStart = tasteProfile.experienceLevel == UserExperienceLevel.COLD_START

                val starterQuery = if (isColdStart) {
                    activeRegion.trendingStarterQuery
                } else {
                    val primaryArtist = topArtists.firstOrNull()?.title
                    if (!primaryArtist.isNullOrBlank()) {
                        "$primaryArtist Hits"
                    } else {
                        // Blend dominant taste language with trending query
                        val dominantLang = tasteProfile.languageAffinities.maxByOrNull { it.value }?.key
                        if (dominantLang != null && dominantLang != MusicLanguage.GLOBAL) {
                            "${dominantLang.displayName} Hits"
                        } else {
                            activeRegion.trendingStarterQuery
                        }
                    }
                }

                val customTasteSections = starterQuery.let { query ->
                    val searchRes = YouTube.searchSummary(query).getOrNull()
                    searchRes?.summaries?.mapNotNull { summary ->
                        if (summary.items.isEmpty()) null else {
                            val rankedItems = recommendationScorer.rankAndDiversify(
                                items = summary.items
                                    .filterExplicit(hideExplicit)
                                    .filterVideoSongs(hideVideoSongs)
                                    .filterYoutubeShorts(hideYoutubeShorts),
                                region = activeRegion,
                                tasteProfile = tasteProfile
                            )
                            if (rankedItems.isEmpty()) null else {
                                val sectionTitle = if (isColdStart) {
                                    "${summary.title} in ${activeRegion.languageDisplayName}"
                                } else {
                                    "${summary.title} For You"
                                }
                                com.music.innertube.pages.HomePage.Section(
                                    title = sectionTitle,
                                    label = if (isColdStart) "Regional Trending" else "Personalized",
                                    thumbnail = null,
                                    endpoint = null,
                                    items = rankedItems
                                )
                            }
                        }
                    }
                } ?: emptyList()

                // If quickPicks is empty, seed it from ranked candidates
                if (quickPicks.value.isNullOrEmpty()) {
                    val candidateSongs = customTasteSections.flatMap { it.items.filterIsInstance<SongItem>() }
                    if (candidateSongs.isNotEmpty()) {
                        quickPicks.value = candidateSongs.map { s ->
                            val entity = SongEntity(
                                id = s.id,
                                title = s.title,
                                duration = s.duration ?: -1,
                                thumbnailUrl = s.thumbnail,
                                explicit = s.explicit,
                                isVideo = false
                            )
                            Song(
                                song = entity,
                                artists = s.artists.map { com.snuggle.music.db.entities.ArtistEntity(id = it.id ?: it.name, name = it.name) },
                                album = null
                            )
                        }.take(20)
                    }
                }

                homeResult.onSuccess { page ->
                    val filteredSections = page.sections.mapNotNull { section ->
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts)
                        if (filteredItems.isEmpty()) null else {
                            val rankedItems = recommendationScorer.rankAndDiversify(
                                items = filteredItems,
                                region = activeRegion,
                                tasteProfile = tasteProfile
                            )
                            section.copy(items = rankedItems)
                        }
                    }
                    // Insert taste/starter sections along with all official YouTube home page feed sections
                    homePage.value = page.copy(
                        sections = customTasteSections + filteredSections
                    )

                    if (quickPicks.value.isNullOrEmpty()) {
                        val allHomeSongs = (customTasteSections + filteredSections).flatMap { it.items.filterIsInstance<SongItem>() }
                        if (allHomeSongs.isNotEmpty()) {
                            quickPicks.value = allHomeSongs.map { s ->
                                val entity = SongEntity(
                                    id = s.id,
                                    title = s.title,
                                    duration = s.duration ?: -1,
                                    thumbnailUrl = s.thumbnail,
                                    explicit = s.explicit,
                                    isVideo = false
                                )
                                Song(
                                    song = entity,
                                    artists = s.artists.map { com.snuggle.music.db.entities.ArtistEntity(id = it.id ?: it.name, name = it.name) },
                                    album = null
                                )
                            }.take(20)
                        }
                    }
                }.onFailure {
                    reportException(it)
                    if (customTasteSections.isNotEmpty()) {
                        homePage.value = com.music.innertube.pages.HomePage(
                            chips = emptyList(),
                            sections = customTasteSections,
                            continuation = null
                        )
                    }
                }
            }
            launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    val filteredNewReleases = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    val rankedNewReleases = recommendationScorer.rankAndDiversify(
                        items = filteredNewReleases,
                        region = activeRegion,
                        tasteProfile = tasteProfile
                    )
                    explorePage.value = page.copy(
                        newReleaseAlbums = rankedNewReleases
                    )
                }.onFailure { reportException(it) }
            }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true

        
        loadLocalDataPhase()
        isLoading.value = false

        
        loadNetworkDataPhase()
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            var currentContinuation = continuation
            var hasNewItems = false

            while (currentContinuation != null && !hasNewItems) {
                val nextSections = YouTube.home(currentContinuation).getOrNull() ?: break
                currentContinuation = nextSections.continuation

                val newSections = nextSections.sections.mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }

                if (newSections.isNotEmpty()) {
                    hasNewItems = true
                }

                homePage.value = nextSections.copy(
                    chips = homePage.value?.chips,
                    continuation = currentContinuation,
                    sections = homePage.value?.sections.orEmpty() + newSections
                )
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { preferences ->
                    Pair(preferences[InnerTubeCookieKey], preferences[CustomUsernameKey])
                }
                .distinctUntilChanged()
                .collect { (cookie, customUsername) ->
                    
                    if (isProcessingAccountData) return@collect

                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            
                            YouTube.cookie = cookie

                            
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                                accountName.value = if (!customUsername.isNullOrBlank()) customUsername else "Guest"
                            }
                        } else {
                            accountName.value = if (!customUsername.isNullOrBlank()) customUsername else "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}

