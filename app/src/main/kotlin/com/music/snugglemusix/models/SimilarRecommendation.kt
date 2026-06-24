

package com.snuggle.music.models

import com.music.innertube.models.YTItem
import com.snuggle.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
