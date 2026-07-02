

package com.snuggle.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.snuggle.music.LocalPlayerAwareWindowInsets
import com.snuggle.music.LocalPlayerConnection
import com.snuggle.music.R
import com.snuggle.music.constants.GridItemSize
import com.snuggle.music.constants.GridItemsSizeKey
import com.snuggle.music.constants.GridThumbnailHeight
import com.snuggle.music.ui.component.IconButton
import com.snuggle.music.ui.component.LocalMenuState
import com.snuggle.music.ui.component.YouTubeGridItem
import com.snuggle.music.ui.component.shimmer.GridItemPlaceHolder
import com.snuggle.music.ui.component.shimmer.ShimmerHost
import com.snuggle.music.ui.menu.YouTubeAlbumMenu
import com.snuggle.music.ui.utils.backToMain
import com.snuggle.music.utils.rememberEnumPreference
import com.snuggle.music.viewmodels.NewReleaseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val newReleaseAlbums by viewModel.newReleaseAlbums.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = newReleaseAlbums.distinctBy { it.id },
            key = { it.id },
        ) { album ->
            YouTubeGridItem(
                item = album,
                isActive = mediaMetadata?.album?.id == album.id,
                isPlaying = isPlaying,
                fillMaxWidth = true,
                coroutineScope = coroutineScope,
                modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${album.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )
        }

        if (newReleaseAlbums.isEmpty()) {
            items(8) {
                ShimmerHost {
                    GridItemPlaceHolder(fillMaxWidth = true)
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.new_release_albums)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
