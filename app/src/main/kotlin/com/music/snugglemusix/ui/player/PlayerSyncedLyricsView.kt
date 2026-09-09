package com.snuggle.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import com.snuggle.music.LocalDatabase
import com.snuggle.music.LocalPlayerConnection
import com.snuggle.music.constants.PlayerHorizontalPadding
import com.snuggle.music.db.entities.LyricsEntity
import com.snuggle.music.di.LyricsHelperEntryPoint
import com.snuggle.music.lyrics.LyricsUtils.parseLyrics
import com.snuggle.music.models.MediaMetadata
import com.snuggle.music.ui.component.shimmer.ShimmerHost
import com.snuggle.music.ui.component.shimmer.TextPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun PlayerSyncedLyricsView(
    mediaMetadata: MediaMetadata?,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val existing = database.lyrics(mediaMetadata.id).firstOrNull()
                    if (existing != null) return@launch
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    // Ignore failures
                }
            }
        }
    }
    
    val lines = remember(currentLyrics) {
        val lyricsText = currentLyrics?.lyrics?.trim()
        if (lyricsText.isNullOrEmpty() || !lyricsText.startsWith("[")) return@remember emptyList()
        parseLyrics(lyricsText).filter { it.text.isNotBlank() }
    }

    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        if (currentLyrics == null) {
            // Loading skeleton
            ShimmerHost(
                modifier = Modifier.fillMaxWidth()
            ) {
                TextPlaceholder(
                    height = 20.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
            }
        } else if (lines.isEmpty()) {
            // No synced lyrics found - just empty state, show nothing
        } else {
            val effectivePosition = positionProvider()
            val currentLineIndex = remember(effectivePosition, lines) {
                val index = lines.indexOfLast { it.time <= effectivePosition }
                if (index >= 0) index else 0
            }
            val currentLine = lines.getOrNull(currentLineIndex)?.text ?: ""

            AnimatedContent(
                targetState = currentLine,
                transitionSpec = {
                    (fadeIn() + slideInVertically { height -> height }).togetherWith(
                        fadeOut() + slideOutVertically { height -> -height }
                    ).using(SizeTransform(clip = false))
                },
                label = "SyncedLyrics"
            ) { lineText ->
                Text(
                    text = lineText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
