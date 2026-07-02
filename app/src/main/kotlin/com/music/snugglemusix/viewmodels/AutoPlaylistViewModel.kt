

package com.snuggle.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuggle.music.constants.ExportedSongIdsKey
import com.snuggle.music.constants.HideExplicitKey
import com.snuggle.music.constants.HideVideoSongsKey
import com.snuggle.music.constants.SongSortDescendingKey
import com.snuggle.music.constants.SongSortType
import com.snuggle.music.constants.SongSortTypeKey
import com.snuggle.music.db.MusicDatabase
import com.snuggle.music.extensions.filterExplicit
import com.snuggle.music.extensions.filterVideoSongs
import com.snuggle.music.extensions.toEnum
import com.snuggle.music.utils.SyncUtils
import com.snuggle.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false
                    ),
                    it[ExportedSongIdsKey] ?: "",
                    Unit
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (triple, exportedSongIds, _) ->
                val (sortDesc, hideExplicit, hideVideoSongs) = triple
                val (sortType, descending) = sortDesc
                when (playlist) {
                    "liked" -> database.likedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "downloaded" -> database.downloadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "uploaded" -> database.uploadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "exported" -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids)
                            .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }

                    else -> kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}
