

package com.snuggle.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import androidx.datastore.preferences.core.edit
import com.music.innertube.pages.ArtistPage
import com.snuggle.music.constants.HideExplicitKey
import com.snuggle.music.constants.HideVideoSongsKey
import com.snuggle.music.constants.HideYoutubeShortsKey
import com.snuggle.music.db.MusicDatabase
import com.snuggle.music.extensions.filterExplicit
import com.snuggle.music.extensions.filterExplicitAlbums
import com.snuggle.music.utils.dataStore
import com.snuggle.music.utils.get
import com.snuggle.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.snuggle.music.extensions.filterVideoSongs as filterVideoSongsLocal
import com.snuggle.music.artistvideo.ArtistVideoCanvasProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val providerRegistry: com.snuggle.music.providers.ProviderRegistry,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)

    private val _jioSaavnArtistCover = MutableStateFlow<String?>(null)
    val jioSaavnArtistCover: StateFlow<String?> = _jioSaavnArtistCover
    
    private val _artistVideoUrl = MutableStateFlow<String?>(null)
    val artistVideoUrl: StateFlow<String?> = _artistVideoUrl

    private val _artistVideoSong = MutableStateFlow<com.music.innertube.models.SongItem?>(null)
    val artistVideoSong: StateFlow<com.music.innertube.models.SongItem?> = _artistVideoSong
    
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            YouTube.artist(artistId)
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    artistPage = page.copy(sections = filteredSections)

                    // Fetch official high-res artist cover from JioSaavn
                    val artistName = page.artist?.title
                    if (!artistName.isNullOrBlank()) {
                        launch(Dispatchers.IO) {
                            val saavnCover = providerRegistry.jioSaavnProvider.getArtistCover(artistName)
                            if (saavnCover != null) {
                                _jioSaavnArtistCover.value = saavnCover
                            }
                        }
                    }
                    
                    val topSongsSection = page.sections.find { it.items.firstOrNull() is com.music.innertube.models.SongItem }
                    topSongsSection?.items?.forEach { item ->
                        if (item is com.music.innertube.models.SongItem) {
                            val canvas = ArtistVideoCanvasProvider.getBySongArtist(
                                song = item.title,
                                artist = page.artist?.title ?: ""
                            )
                            if (canvas?.preferredAnimationUrl != null) {
                                _artistVideoUrl.value = canvas.preferredAnimationUrl
                                _artistVideoSong.value = item
                                return@forEach
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }

    val isBlocked = context.dataStore.data.map {
        it[com.snuggle.music.constants.BlockedArtistsKey]?.contains(artistId) ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun toggleBlockArtist() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                val current = preferences[com.snuggle.music.constants.BlockedArtistsKey] ?: emptySet()
                if (current.contains(artistId)) {
                    preferences[com.snuggle.music.constants.BlockedArtistsKey] = current - artistId
                } else {
                    preferences[com.snuggle.music.constants.BlockedArtistsKey] = current + artistId
                }
            }
        }
    }
}
