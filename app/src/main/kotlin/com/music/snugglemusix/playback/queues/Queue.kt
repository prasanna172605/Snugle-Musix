

package com.snuggle.music.playback.queues

import androidx.media3.common.MediaItem
import com.snuggle.music.extensions.metadata
import com.snuggle.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                copy(
                    items = items.filterVideoSongs(true),
                )
            } else {
                this
            }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }

fun List<MediaItem>.filterByQueueLanguage(seedTrack: MediaMetadata?): List<MediaItem> {
    if (seedTrack == null) return this
    val seedText = "${seedTrack.title} ${seedTrack.artists.joinToString { it.name }}".lowercase()
    
    // Check seed track characters to identify script presence
    var hasTamilSeed = false
    var hasHindiSeed = false
    var hasTeluguSeed = false
    
    for (char in seedText) {
        val cp = char.code
        if (cp in 0x0B80..0x0BFF) {
            hasTamilSeed = true
        } else if (cp in 0x0900..0x097F) {
            hasHindiSeed = true
        } else if (cp in 0x0C00..0x0C7F) {
            hasTeluguSeed = true
        }
    }
    
    // Match common language metadata triggers or tags in title/artists
    val isTamilSeedKeyword = hasTamilSeed || seedText.contains("tamil") || seedText.contains("ar rahman") || seedText.contains("anirudh") || seedText.contains("ilaiyaraaja") || seedText.contains("yuvan") || seedText.contains("harris jayaraj") || seedText.contains("sid sriram") || seedText.contains("dhibu ninan thomas")
    val isHindiSeedKeyword = hasHindiSeed || seedText.contains("hindi") || seedText.contains("arijit singh") || seedText.contains("pritam") || seedText.contains("nehakakkar") || seedText.contains("badshah") || seedText.contains("t-series") || seedText.contains("shreya ghoshal")
    val isTeluguSeedKeyword = hasTeluguSeed || seedText.contains("telugu") || seedText.contains("devi sri prasad") || seedText.contains("thaman") || seedText.contains("keeravaani") || seedText.contains("sid sriram telugu") || seedText.contains("spb telugu")
    
    if (!isTamilSeedKeyword && !isHindiSeedKeyword && !isTeluguSeedKeyword) return this
    
    return this.filter { item ->
        val itemMeta = item.metadata ?: return@filter true
        val itemText = "${itemMeta.title} ${itemMeta.artists.joinToString { it.name }}".lowercase()
        
        var hasTamilItem = false
        var hasHindiItem = false
        var hasTeluguItem = false
        
        for (char in itemText) {
            val cp = char.code
            if (cp in 0x0B80..0x0BFF) {
                hasTamilItem = true
            } else if (cp in 0x0900..0x097F) {
                hasHindiItem = true
            } else if (cp in 0x0C00..0x0C7F) {
                hasTeluguItem = true
            }
        }
        
        val isTamilItem = hasTamilItem || itemText.contains("tamil") || itemText.contains("ar rahman") || itemText.contains("anirudh") || itemText.contains("ilaiyaraaja") || itemText.contains("yuvan") || itemText.contains("harris jayaraj") || itemText.contains("sid sriram") || itemText.contains("dhibu ninan thomas")
        val isHindiItem = hasHindiItem || itemText.contains("hindi") || itemText.contains("arijit") || itemText.contains("pritam") || itemText.contains("t-series")
        val isTeluguItem = hasTeluguItem || itemText.contains("telugu") || itemText.contains("devi sri prasad") || itemText.contains("thaman") || itemText.contains("keeravaani")
        
        if (isTamilSeedKeyword) {
            // Disallow Hindi and Telugu songs if playing a Tamil song
            !(isHindiItem || isTeluguItem)
        } else if (isHindiSeedKeyword) {
            // Disallow Tamil and Telugu songs if playing a Hindi song
            !(isTamilItem || isTeluguItem)
        } else if (isTeluguSeedKeyword) {
            // Disallow Tamil and Hindi songs if playing a Telugu song
            !(isTamilItem || isHindiItem)
        } else {
            true
        }
    }
}
