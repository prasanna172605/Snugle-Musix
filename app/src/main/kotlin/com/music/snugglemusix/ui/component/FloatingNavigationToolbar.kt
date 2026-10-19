@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.snuggle.music.ui.component

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay


import com.snuggle.music.ui.component.LiquidGlassTabBar
import com.snuggle.music.ui.component.LocalAppBackdrop
import androidx.compose.ui.graphics.isSpecified
import com.snuggle.music.ui.component.rememberGlassInteraction
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.IntBuffer
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.isSystemInDarkTheme


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.draw.blur
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.IconButton
import coil3.compose.AsyncImage
import com.snuggle.music.models.MediaMetadata
import com.snuggle.music.R
import com.snuggle.music.ui.screens.Screens
import com.snuggle.music.utils.rememberPreference
import com.snuggle.music.constants.UseLiquidGlassUiKey
import com.snuggle.music.ui.theme.liquidGlass


@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    onFabClick: (() -> Unit)? = null,
    fabIconRes: Int? = null,
    fabContentDescription: String = "",
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onSettingsClick: (() -> Unit)? = null,
    settingsIconRes: Int? = null,
    settingsContentDescription: String = "",
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    isInline: Boolean = false,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onHomeDoubleClick: (() -> Unit)? = null,
    onSearchDoubleClick: (() -> Unit)? = null,
    onSearchLongClick: (() -> Unit)? = null,
    mediaMetadata: MediaMetadata? = null,
    isPlaying: Boolean = false,
    onMiniPlayerClick: (() -> Unit)? = null,
    onMiniPlayerPlayPause: (() -> Unit)? = null,
) {
    val toolbarContainerColor = floatingToolbarContainerColor(pureBlack = pureBlack)
    val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = toolbarContainerColor,
    )
    val hasShuffleAction = (onShuffleClick != null && shuffleIconRes != null) || onMusicRecognitionClick != null
    val hasFabAction = onFabClick != null && fabIconRes != null

    val useLiquidGlassUi by rememberPreference(UseLiquidGlassUiKey, defaultValue = true)
    val glassConfig = LocalGlassEffectConfig.current
    val isGlassActive = (glassConfig.isEnabledFor(GlassComponent.NAV_BAR) || (useLiquidGlassUi && glassConfig.globalEnabled))
    val searchFabInteraction = rememberGlassInteraction()
    val luminanceAnimation = remember { Animatable(0.5f) }
    val isDark = isSystemInDarkTheme()
    val layer = rememberGraphicsLayer()

    LaunchedEffect(layer) {
        val buffer = IntBuffer.allocate(25)
        while (isActive) {
            try {
                withContext(Dispatchers.IO) {
                    val imageBitmap = layer.toImageBitmap()
                    val thumbnail =
                        imageBitmap
                            .asAndroidBitmap()
                            .scale(5, 5, false)
                            .copy(Bitmap.Config.ARGB_8888, false)
                    buffer.rewind()
                    thumbnail.copyPixelsToBuffer(buffer)
                }
                val averageLuminance =
                    (0 until 25).sumOf { index ->
                        val color = buffer.get(index)
                        val r = (color shr 16 and 0xFF) / 255f
                        val g = (color shr 8 and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f
                        0.2126 * r + 0.7152 * g + 0.0722 * b
                    } / 25
                luminanceAnimation.animateTo(
                    averageLuminance.coerceIn(0.3, 0.8).toFloat(),
                    tween(500),
                )
            } catch (e: Exception) {
                // Background sampling fails gracefully if layer is not yet fully laid out
            }
            delay(1.seconds)
        }
    }

    if (isGlassActive) {
        val homeItem = remember(items) { items.find { it == Screens.Home } }
        val searchItem = remember(items) { items.find { it == Screens.Search } }
        val mainItems = remember(items) { items.filter { it != Screens.Search } }

        androidx.compose.animation.Crossfade(
            targetState = (isInline && mediaMetadata != null && homeItem != null && searchItem != null),
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "NavbarCrossfade"
        ) { isInlineMode ->
            if (isInlineMode) {
                val isHomeSelected = isSelected(homeItem!!)
                val isSearchSelected = isSelected(searchItem!!)
            val homeInteraction = rememberGlassInteraction()
            val miniPlayerInteraction = rememberGlassInteraction()

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Home button on far left
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .liquidGlass(
                              config = glassConfig,
                              shape = CircleShape
                          )
                          .graphicsLayer {
                              val scale = androidx.compose.ui.util.lerp(1f, 0.96f, homeInteraction.pressProgress)
                              scaleX = scale
                              scaleY = scale
                          }
                        .combinedClickable(
                            onClick = { onItemClick(homeItem, isHomeSelected) },
                            onDoubleClick = onHomeDoubleClick,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val homeColor = if (glassConfig.textColor.isSpecified) {
                        glassConfig.textColor
                    } else if (isHomeSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Icon(
                        painter = painterResource(id = if (isHomeSelected) homeItem.iconIdActive else homeItem.iconIdInactive),
                        contentDescription = stringResource(homeItem.titleId),
                        tint = homeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mini Player docked in center
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .liquidGlass(
                              config = glassConfig,
                              shape = RoundedCornerShape(28.dp)
                          )
                          .graphicsLayer {
                              val scale = androidx.compose.ui.util.lerp(1f, 0.96f, miniPlayerInteraction.pressProgress)
                              scaleX = scale
                              scaleY = scale
                          }, contentAlignment = Alignment.CenterStart) { com.snuggle.music.ui.player.FloatingMiniPlayer(isInline = true, contentColor = if (glassConfig.textColor.isSpecified) glassConfig.textColor else MaterialTheme.colorScheme.onSurface, onClick = { onMiniPlayerClick?.invoke() }) }

                // Search button on far right
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .liquidGlass(
                              config = glassConfig,
                              shape = CircleShape
                          )
                          .graphicsLayer {
                              val scale = androidx.compose.ui.util.lerp(1f, 0.96f, searchFabInteraction.pressProgress)
                              scaleX = scale
                              scaleY = scale
                          }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onItemClick(searchItem, isSearchSelected) },
                                onDoubleTap = { onSearchDoubleClick?.invoke() },
                                onLongPress = { onSearchLongClick?.invoke() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val searchColor = if (glassConfig.textColor.isSpecified) {
                               shape = CircleShape
                          )
                          .graphicsLayer {
                              val scale = androidx.compose.ui.util.lerp(1f, 0.96f, searchFabInteraction.pressProgress)
                              scaleX = scale
                              scaleY = scale
                          }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onItemClick(searchItem, isSearchSelected) },
                                onDoubleTap = { onSearchDoubleClick?.invoke() },
                                onLongPress = { onSearchLongClick?.invoke() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val searchColor = if (glassConfig.textColor.isSpecified) {
                        glassConfig.textColor
                    } else if (isSearchSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Icon(
                        painter = painterResource(id = if (isSearchSelected) searchItem.iconIdActive else searchItem.iconIdInactive),
                        contentDescription = "Search",
                        tint = searchColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Recognition pill — liquid glass floating button
            if (onMusicRecognitionClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val recognitionInteraction = rememberGlassInteraction()
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .liquidGlass(
                              config = glassConfig,
                              shape = CircleShape
                          )
                          .graphicsLayer {
                              val scale = androidx.compose.ui.util.lerp(1f, 0.96f, recognitionInteraction.pressProgress)
                              scaleX = scale
                              scaleY = scale
                          }
                        .clickable { onMusicRecognitionClick() },
                    contentAlignment = Alignment.Center
                ) {
                    val recColor = if (glassConfig.textColor.isSpecified) glassConfig.textColor else MaterialTheme.colorScheme.onSurface
                    Icon(
                        painter = painterResource(id = com.snuggle.music.R.drawable.mic),
                        contentDescription = musicRecognitionContentDescription,
                        tint = recColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                }
            }
        }
        }
        return
    }

    if (isInline && mediaMetadata != null) {
        val homeItem = remember(items) { items.find { it == Screens.Home } }
        val searchItem = remember(items) { items.find { it == Screens.Search } }
        if (homeItem != null && searchItem != null) {
            val isHomeSelected = isSelected(homeItem)
            val isSearchSelected = isSelected(searchItem)
            val pillContainerColor = floatingToolbarContainerColor(pureBlack)

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Home button on far left
                Surface(
                    shape = CircleShape,
                    color = if (isHomeSelected) floatingToolbarSelectedItemContainerColor(pureBlack) else pillContainerColor,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { onItemClick(homeItem, isHomeSelected) },
                                onDoubleClick = onHomeDoubleClick,
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isHomeSelected) homeItem.iconIdActive else homeItem.iconIdInactive),
                            contentDescription = stringResource(homeItem.titleId),
                            tint = if (isHomeSelected) floatingToolbarSelectedItemContentColor(pureBlack) else floatingToolbarItemContentColor(pureBlack),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Mini Player docked in center
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = pillContainerColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onMiniPlayerClick?.invoke() }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = mediaMetadata.thumbnailUrl,
                            placeholder = painterResource(R.drawable.music_note),
                            error = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val artistName = mediaMetadata.artists.joinToString { it.name }
                            if (artistName.isNotBlank()) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { onMiniPlayerPlayPause?.invoke() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Search button on far right
                Surface(
                    shape = CircleShape,
                    color = if (isSearchSelected) floatingToolbarSelectedItemContainerColor(pureBlack) else pillContainerColor,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onItemClick(searchItem, isSearchSelected) },
                                    onDoubleTap = { onSearchDoubleClick?.invoke() },
                                    onLongPress = { onSearchLongClick?.invoke() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSearchSelected) searchItem.iconIdActive else searchItem.iconIdInactive),
                            contentDescription = "Search",
                            tint = if (isSearchSelected) floatingToolbarSelectedItemContentColor(pureBlack) else floatingToolbarItemContentColor(pureBlack),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            return
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showSelectedLabels = false

        if (hasShuffleAction) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarShuffleButton(
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleIconRes = shuffleIconRes,
                        shuffleContentDescription = shuffleContentDescription,
                    )
                },
                modifier = Modifier.widthIn(max = 480.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    onMusicRecognitionClick = onMusicRecognitionClick,
                    musicRecognitionContentDescription = musicRecognitionContentDescription,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onHomeDoubleClick = onHomeDoubleClick,
                    onSearchDoubleClick = onSearchDoubleClick,
                    onSearchLongClick = onSearchLongClick,
                )
            }
        } else if (hasFabAction) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarFabAction(
                        pureBlack = pureBlack,
                        onClick = onFabClick,
                        iconRes = fabIconRes,
                        contentDescription = fabContentDescription,
                    )
                },
                modifier = Modifier.widthIn(max = 480.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    onMusicRecognitionClick = onMusicRecognitionClick,
                    musicRecognitionContentDescription = musicRecognitionContentDescription,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onHomeDoubleClick = onHomeDoubleClick,
                    onSearchDoubleClick = onSearchDoubleClick,
                    onSearchLongClick = onSearchLongClick,
                )
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.widthIn(max = 420.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    onMusicRecognitionClick = onMusicRecognitionClick,
                    musicRecognitionContentDescription = musicRecognitionContentDescription,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onHomeDoubleClick = onHomeDoubleClick,
                    onSearchDoubleClick = onSearchDoubleClick,
                    onSearchLongClick = onSearchLongClick,
                )
            }
        }
    }
}

@Composable
private fun ToolbarItemsContainer(
    items: List<Screens>,
    pureBlack: Boolean,
    showSelectedLabels: Boolean,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onHomeDoubleClick: (() -> Unit)? = null,
    onSearchDoubleClick: (() -> Unit)? = null,
    onSearchLongClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val itemWidths = remember { mutableStateMapOf<Screens, Dp>() }
    val itemPositions = remember { mutableStateMapOf<Screens, Dp>() }

    val activeScreen = items.find { isSelected(it) }
    val targetWidth = itemWidths[activeScreen] ?: 0.dp
    val targetPosition = itemPositions[activeScreen] ?: 0.dp

    val slidingPillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillWidth"
    )

    val slidingPillOffset by animateDpAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillOffset"
    )

    Box(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.matchParentSize()) {
            if (targetWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = slidingPillOffset)
                        .width(slidingPillWidth)
                        .fillMaxHeight()
                        .background(
                            color = floatingToolbarSelectedItemContainerColor(pureBlack),
                            shape = RoundedCornerShape(24.dp)
                        )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEach { screen ->
                if (screen == Screens.Library && onMusicRecognitionClick != null) {
                    FloatingNavigationToolbarActionItem(
                        iconRes = R.drawable.mic,
                        contentDescription = musicRecognitionContentDescription,
                        pureBlack = pureBlack,
                        onClick = onMusicRecognitionClick
                    )
                }

                val selected = isSelected(screen)
                val itemDoubleClick = when (screen) {
                    Screens.Home -> onHomeDoubleClick
                    Screens.Search -> onSearchDoubleClick
                    else -> null
                }
                val itemLongClick = when (screen) {
                    Screens.Search -> onSearchLongClick
                    else -> null
                }

                FloatingNavigationToolbarItem(
                    screen = screen,
                    selected = selected,
                    showSelectedLabel = showSelectedLabels,
                    pureBlack = pureBlack,
                    onClick = { onItemClick(screen, selected) },
                    onDoubleClick = itemDoubleClick,
                    onLongClick = itemLongClick,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        itemWidths[screen] = with(density) { coordinates.size.width.toDp() }
                        itemPositions[screen] = with(density) { coordinates.positionInParent().x.toDp() }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingToolbarShuffleButton(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
) {
    if (onShuffleClick == null || shuffleIconRes == null) return

    FloatingToolbarDefaults.VibrantFloatingActionButton(
        onClick = onShuffleClick,
        shape = CircleShape,
        containerColor = floatingToolbarFabContainerColor(pureBlack = pureBlack),
        contentColor = floatingToolbarFabContentColor(pureBlack = pureBlack),
    ) {
        Icon(
            painter = painterResource(shuffleIconRes),
            contentDescription = shuffleContentDescription.ifEmpty { stringResource(R.string.shuffle) },
        )
    }
}

@Composable
private fun FloatingToolbarFabAction(
    pureBlack: Boolean,
    onClick: (() -> Unit)?,
    iconRes: Int?,
    contentDescription: String,
) {
    if (onClick == null || iconRes == null) return

    FloatingToolbarDefaults.VibrantFloatingActionButton(
        onClick = onClick,
        containerColor = floatingToolbarFabContainerColor(pureBlack = pureBlack),
        contentColor = floatingToolbarFabContentColor(pureBlack = pureBlack),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription =
                contentDescription.ifEmpty {
                    stringResource(R.string.create_playlist)
                },
        )
    }
}

@Composable
private fun FloatingNavigationToolbarItem(
    screen: Screens,
    selected: Boolean,
    showSelectedLabel: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val showLabel = selected && showSelectedLabel
    val transition = updateTransition(targetState = selected, label = "navItem_${screen.route}")

    val contentColor by transition.animateColor(
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) },
        label = "contentColor",
    ) { isSelected ->
        if (isSelected) floatingToolbarSelectedItemContentColor(pureBlack)
        else floatingToolbarItemContentColor(pureBlack)
    }

    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "iconScale",
    ) { isSelected -> if (isSelected) 1.12f else 1.0f }

    val horizontalPadding by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "horizontalPadding",
    ) { isSelected ->
        if (isSelected && showSelectedLabel) 16.dp else 12.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    Row(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick,
            )
            .widthIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "iconCrossfade",
            modifier = Modifier.scale(iconScale),
        ) { isSelected ->
            Icon(
                painter = painterResource(if (isSelected) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = stringResource(screen.titleId),
                tint = contentColor,
            )
        }

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + expandHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + shrinkHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(screen.titleId),
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun floatingToolbarContainerColor(pureBlack: Boolean): Color {
    return if (pureBlack) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
}

@Composable
private fun floatingToolbarFabContainerColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun floatingToolbarFabContentColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun floatingToolbarSelectedItemContainerColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarSelectedItemContentColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.onSecondaryContainer
}


@Composable
private fun floatingToolbarItemContentColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color {
    return MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun FloatingNavigationToolbarActionItem(
    iconRes: Int,
    contentDescription: String,
    pureBlack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    Row(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .widthIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = floatingToolbarItemContentColor(pureBlack),
        )
    }
}
