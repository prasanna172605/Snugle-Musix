package com.snuggle.music.personalization

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.snuggle.music.db.entities.Album
import com.snuggle.music.db.entities.Artist
import com.snuggle.music.db.entities.Song

object MusicLanguageClassifier {

    /**
     * Detects primary music language based on Unicode block analysis and title/artist/author heuristics.
     */
    fun classify(
        title: String?,
        artists: List<String> = emptyList(),
        extraText: String? = null
    ): MusicLanguage {
        val combined = buildString {
            if (!title.isNullOrBlank()) append(title).append(" ")
            if (artists.isNotEmpty()) append(artists.joinToString(" ")).append(" ")
            if (!extraText.isNullOrBlank()) append(extraText)
        }.lowercase()

        if (combined.isBlank()) return MusicLanguage.GLOBAL

        // 1. Unicode Script Detection
        var tamilCount = 0
        var malayalamCount = 0
        var teluguCount = 0
        var kannadaCount = 0
        var devanagariCount = 0
        var bengaliCount = 0
        var gurmukhiCount = 0
        var gujaratiCount = 0

        for (char in combined) {
            val block = Character.UnicodeBlock.of(char)
            when (block) {
                Character.UnicodeBlock.TAMIL -> tamilCount++
                Character.UnicodeBlock.MALAYALAM -> malayalamCount++
                Character.UnicodeBlock.TELUGU -> teluguCount++
                Character.UnicodeBlock.KANNADA -> kannadaCount++
                Character.UnicodeBlock.DEVANAGARI -> devanagariCount++
                Character.UnicodeBlock.BENGALI -> bengaliCount++
                Character.UnicodeBlock.GURMUKHI -> gurmukhiCount++
                Character.UnicodeBlock.GUJARATI -> gujaratiCount++
                else -> {}
            }
        }

        if (tamilCount > 2) return MusicLanguage.TAMIL
        if (malayalamCount > 2) return MusicLanguage.MALAYALAM
        if (teluguCount > 2) return MusicLanguage.TELUGU
        if (kannadaCount > 2) return MusicLanguage.KANNADA
        if (gurmukhiCount > 2) return MusicLanguage.PUNJABI
        if (bengaliCount > 2) return MusicLanguage.BENGALI
        if (gujaratiCount > 2) return MusicLanguage.GUJARATI
        if (devanagariCount > 2) return MusicLanguage.HINDI

        // 2. Exact keyword and tag heuristics
        val words = combined.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }.toSet()

        if (words.contains("tamil") || words.contains("kollywood")) return MusicLanguage.TAMIL
        if (words.contains("malayalam") || words.contains("mollywood")) return MusicLanguage.MALAYALAM
        if (words.contains("telugu") || words.contains("tollywood")) return MusicLanguage.TELUGU
        if (words.contains("kannada") || words.contains("sandalwood")) return MusicLanguage.KANNADA
        if (words.contains("punjabi") || words.contains("punjab")) return MusicLanguage.PUNJABI
        if (words.contains("marathi")) return MusicLanguage.MARATHI
        if (words.contains("bengali") || words.contains("bangla")) return MusicLanguage.BENGALI
        if (words.contains("gujarati") || words.contains("garba")) return MusicLanguage.GUJARATI
        if (words.contains("hindi") || words.contains("bollywood")) return MusicLanguage.HINDI

        // 3. Artist / Composer Keyword Matching
        for (lang in MusicLanguage.entries) {
            if (lang == MusicLanguage.GLOBAL) continue
            for (kw in lang.searchKeywords) {
                if (combined.contains(kw)) {
                    return lang
                }
            }
        }

        // 4. Default to English / Global for Latin characters
        return MusicLanguage.ENGLISH
    }

    fun classify(item: YTItem): MusicLanguage {
        return when (item) {
            is SongItem -> classify(item.title, item.artists.map { it.name })
            is AlbumItem -> classify(item.title, item.artists?.map { it.name }.orEmpty(), item.year?.toString())
            is PlaylistItem -> classify(item.title, listOfNotNull(item.author?.name), item.songCountText)
            is ArtistItem -> classify(item.title, listOf(item.title))
        }
    }

    fun classify(song: Song): MusicLanguage {
        return classify(
            title = song.song.title,
            artists = song.artists.map { it.name },
            extraText = song.album?.title
        )
    }

    fun classify(media: com.snuggle.music.models.MediaMetadata): MusicLanguage {
        return classify(
            title = media.title,
            artists = media.artists.map { it.name },
            extraText = media.album?.title
        )
    }

    fun classify(album: Album): MusicLanguage {
        return classify(
            title = album.album.title,
            artists = album.artists.map { it.name },
            extraText = album.album.year?.toString()
        )
    }

    fun classify(artist: Artist): MusicLanguage {
        return classify(
            title = artist.artist.name,
            artists = listOf(artist.artist.name)
        )
    }
}
