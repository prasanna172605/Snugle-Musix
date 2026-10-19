

package com.snuggle.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.pages.MoodAndGenres
import com.snuggle.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.Dispatchers
import com.snuggle.music.personalization.RegionalPreferenceManager

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor(
    private val regionalPreferenceManager: RegionalPreferenceManager
) : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube
                .moodAndGenres()
                .onSuccess { list ->
                    val region = regionalPreferenceManager.getActiveRegion()
                    val primaryLang = region.languageDisplayName.lowercase()
                    
                    val sortedList = list.sortedByDescending {
                        it.title.lowercase().contains(primaryLang) || it.title.lowercase() == primaryLang
                    }
                    moodAndGenres.value = sortedList
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
