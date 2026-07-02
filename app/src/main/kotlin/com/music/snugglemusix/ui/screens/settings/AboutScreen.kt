@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.snuggle.music.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import com.snuggle.music.BuildConfig
import com.snuggle.music.LocalPlayerAwareWindowInsets
import com.snuggle.music.R
import com.snuggle.music.ui.component.IconButton
import com.snuggle.music.ui.utils.backToMain

import androidx.compose.ui.platform.LocalContext
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
highlightKey: String? = null) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack?.invoke() ?: navController.navigateUp() },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AboutAppCard() }

            item {
                DeveloperProfileCard()
            }
            
            item {
                AboutSectionCard(title = "Links") {
                    AboutActionRow(
                        icon = painterResource(R.drawable.github),
                        title = "GitHub",
                        subtitle = "prasanna172605/Snugle-Musix",
                        onClick = { uriHandler.openUri("https://github.com/prasanna172605/Snugle-Musix") },
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = painterResource(R.drawable.website),
                        title = "Portfolio",
                        subtitle = "prasanna0705.netlify.app",
                        onClick = { uriHandler.openUri("https://prasanna0705.netlify.app/Snuggle-Musix") },
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = painterResource(R.drawable.ic_instagram_new),
                        title = "Instagram",
                        subtitle = "@__prasannaaah__",
                        onClick = { uriHandler.openUri("https://www.instagram.com/__prasannaaah__/?__pwa=1") },
                    )
                }
            }
            
            item {
                AboutSectionCard(title = "Information") {
                    AboutActionRow(
                        icon = painterResource(R.drawable.list),
                        title = "Changelog",
                        onClick = { uriHandler.openUri("https://github.com/prasanna172605/Snugle-Musix/releases") },
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = painterResource(R.drawable.lock),
                        title = "Privacy Policy",
                        onClick = { uriHandler.openUri("https://github.com/prasanna172605/Snugle-Musix#privacy") },
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = painterResource(R.drawable.info),
                        title = "Licenses",
                        onClick = { navController.navigate("settings/about/licenses") },
                    )
                }
            }

        }
    }
}

@Composable
private fun AboutAppCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            
            var isEasterEggActive by remember { mutableStateOf(false) }
            val rotation by animateFloatAsState(
                targetValue = if (isEasterEggActive) 180f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "flip"
            )
            
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        scaleX = scale
                        scaleY = scale
                        cameraDistance = 12f * density
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { isEasterEggActive = !isEasterEggActive }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (rotation <= 90f) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_nobg),
                        contentDescription = "Snuggle Musix Logo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_nobg),
                        contentDescription = "Snuggle Musix Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .graphicsLayer { rotationY = 180f }
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = if (rotation <= 90f) "Snuggle Musix" else "Developed by Abberant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (BuildConfig.DEBUG) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = "DEBUG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = BuildConfig.ARCHITECTURE.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "rowScale",
    )
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.10f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = tint,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun DeveloperProfileCard() {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "devCardScale"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Developer",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { }
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.developer_abberant),
                    contentDescription = "Abberant",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Abberant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Lead Developer & Designer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
