package com.snuggle.music.personalization

import com.snuggle.music.db.MusicDatabase
import com.snuggle.music.db.entities.Song
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class UserExperienceLevel {
    COLD_START,      // 0 - 5 plays
    EARLY_EXPLORER,  // 6 - 25 plays
    ESTABLISHED,     // 26 - 100 plays
    MATURING         // > 100 plays
}

data class TasteProfile(
    val experienceLevel: UserExperienceLevel,
    val totalPlays: Int,
    val languageAffinities: Map<MusicLanguage, Float>,
    val topArtistIds: Set<String>,
    val topArtistNames: Set<String>,
    val recentSeedSongs: List<Song>
)

@Singleton
class TasteProfileEngine @Inject constructor(
    private val database: MusicDatabase
) {

    /**
     * Builds the current taste profile by analyzing local listening events,
     * play times, liked songs, and artist frequencies with time-decay weighting.
     */
    suspend fun computeTasteProfile(): TasteProfile {
        val allEvents = database.events().first()
        val totalPlays = allEvents.size

        val experienceLevel = when {
            totalPlays < 5 -> UserExperienceLevel.COLD_START
            totalPlays <= 25 -> UserExperienceLevel.EARLY_EXPLORER
            totalPlays <= 100 -> UserExperienceLevel.ESTABLISHED
            else -> UserExperienceLevel.MATURING
        }

        val topArtists = database.mostPlayedArtists(15).first()
        val topArtistIds = topArtists.map { it.id }.toSet()
        val topArtistNames = topArtists.map { it.title.lowercase() }.toSet()

        val recentSongs = database.mostPlayedSongs(
            fromTimeStamp = System.currentTimeMillis() - 86400000L * 30,
            limit = 20
        ).first()

        val rawLanguageScores = mutableMapOf<MusicLanguage, Float>()
        var totalWeight = 0f

        val now = System.currentTimeMillis()
        val halfLifeDays = 14.0
        val lambda = kotlin.math.ln(2.0) / (halfLifeDays * 86400000.0)

        // 1. Analyze events with exponential decay
        for (event in allEvents.take(150)) {
            val song = event.song
            val lang = MusicLanguageClassifier.classify(song)

            val eventTime = event.event.timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val ageMs = (now - eventTime).coerceAtLeast(0)
            val decayWeight = kotlin.math.exp(-lambda * ageMs).toFloat()

            val playTimeFactor = (event.event.playTime / 60000f).coerceIn(0.5f, 3.0f)
            val score = decayWeight * playTimeFactor

            rawLanguageScores[lang] = (rawLanguageScores[lang] ?: 0f) + score
            totalWeight += score
        }

        // 2. Factor in liked songs as strong positive signals
        val likedSongs = database.likedSongsByNameAsc().first()
        for (song in likedSongs) {
            val lang = MusicLanguageClassifier.classify(song)
            val likeBonus = 2.5f
            rawLanguageScores[lang] = (rawLanguageScores[lang] ?: 0f) + likeBonus
            totalWeight += likeBonus
        }

        // 3. Normalize affinities between 0.0 and 1.0
        val normalizedAffinities = if (totalWeight > 0f) {
            rawLanguageScores.mapValues { (_, score) -> (score / totalWeight).coerceIn(0f, 1f) }
        } else {
            emptyMap()
        }

        return TasteProfile(
            experienceLevel = experienceLevel,
            totalPlays = totalPlays,
            languageAffinities = normalizedAffinities,
            topArtistIds = topArtistIds,
            topArtistNames = topArtistNames,
            recentSeedSongs = recentSongs
        )
    }
}
