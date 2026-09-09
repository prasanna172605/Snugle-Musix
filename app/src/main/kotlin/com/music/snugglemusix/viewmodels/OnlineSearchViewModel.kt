

package com.snuggle.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import com.snuggle.music.constants.HideExplicitKey
import com.snuggle.music.constants.HideVideoSongsKey
import com.snuggle.music.constants.HideYoutubeShortsKey
import com.snuggle.music.models.ItemsPage
import com.snuggle.music.utils.dataStore
import com.snuggle.music.utils.get
import com.snuggle.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
    val providerRegistry: com.snuggle.music.providers.ProviderRegistry,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    if (summaryPage == null) {
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

                        // Fetch YouTube summary
                        val ytSummary = YouTube.searchSummary(query).getOrNull()
                        val saavnSongs = providerRegistry.jioSaavnProvider.search(query, com.snuggle.music.providers.ProviderSearchFilter.SONGS)
                            .getOrNull()?.items.orEmpty()

                        val summariesList = mutableListOf<com.music.innertube.pages.SearchSummary>()
                        if (saavnSongs.isNotEmpty()) {
                            summariesList.add(com.music.innertube.pages.SearchSummary("JioSaavn", saavnSongs))
                        }
                        if (ytSummary != null) {
                            summariesList.addAll(ytSummary.summaries)
                        }

                        if (summariesList.isNotEmpty()) {
                            summaryPage = SearchSummaryPage(summariesList)
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                        }
                    }
                } else {
                    if (filter.value == FILTER_JIOSAAVN.value) {
                        if (viewStateMap[filter.value] == null) {
                            val saavnResult = providerRegistry.jioSaavnProvider.search(query, com.snuggle.music.providers.ProviderSearchFilter.SONGS, page = 1)
                                .getOrNull()
                            val items = saavnResult?.items.orEmpty()
                            viewStateMap[filter.value] = ItemsPage(items, saavnResult?.continuation)
                        }
                    } else if (viewStateMap[filter.value] == null) {
                        YouTube
                            .search(query, filter)
                            .onSuccess { result ->
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                                viewStateMap[filter.value] =
                                    ItemsPage(
                                        result.items
                                            .distinctBy { it.id }
                                            .filterExplicit(
                                                hideExplicit,
                                            )
                                            .let { items ->
                                                if (filter.value == YouTube.SearchFilter.FILTER_VIDEO.value) items
                                                else items.filterVideoSongs(hideVideoSongs)
                                            }
                                            .filterYoutubeShorts(hideYoutubeShorts),
                                        result.continuation,
                                    )
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val filterVal = filter.value?.value
        viewModelScope.launch {
            if (filterVal == null) return@launch
            val viewState = viewStateMap[filterVal] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                if (filterVal == FILTER_JIOSAAVN.value) {
                    val nextPage = continuation.toIntOrNull() ?: 2
                    val saavnResult = providerRegistry.jioSaavnProvider.search(
                        query = query,
                        filter = com.snuggle.music.providers.ProviderSearchFilter.SONGS,
                        page = nextPage
                    ).getOrNull()
                    val newItems = saavnResult?.items.orEmpty()
                    viewStateMap[filterVal] = ItemsPage(
                        (viewState.items + newItems).distinctBy { it.id },
                        saavnResult?.continuation
                    )
                    return@launch
                }

                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .let { items ->
                        if (filterVal == YouTube.SearchFilter.FILTER_VIDEO.value) items
                        else items.filterVideoSongs(hideVideoSongs)
                    }
                    .filterYoutubeShorts(hideYoutubeShorts)
                viewStateMap[filterVal] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }

    companion object {
        val FILTER_JIOSAAVN = YouTube.SearchFilter("filter_jiosaavn")
    }
}
