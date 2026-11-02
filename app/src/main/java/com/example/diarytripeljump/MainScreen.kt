package com.example.diarytripeljump

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Маршруты приложения. */
object Routes {
    const val HOME = "home"
    const val TRAININGS = "trainings"
    const val COMPETITIONS = "competitions"
    const val RECORDS = "records"
    const val CATALOG = "catalog"
    const val PROFILE = "profile"
}

/** Пункты нижней навигации (вкладки верхнего уровня). */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(Routes.HOME, "Главная", Icons.Outlined.Home, Icons.Filled.Home),
    TRAININGS(Routes.TRAININGS, "Тренировки", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    COMPETITIONS(Routes.COMPETITIONS, "Старты", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    RECORDS(Routes.RECORDS, "Рекорды", Icons.Outlined.MilitaryTech, Icons.Filled.MilitaryTech),
    CATALOG(Routes.CATALOG, "Каталог", Icons.Outlined.Category, Icons.Filled.Category)
}

/** Переход между вкладками: одна запись в стеке, состояние вкладок сохраняется. */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// Короткий перекрёстный fade без задержки и масштабирования: раньше переход занимал 330 мс
// (90 мс исчезновения + 240 мс появления после паузы), а scaleIn заставлял перерисовывать весь экран слоем.
private const val TAB_ENTER_MS = 150
private const val TAB_EXIT_MS = 100

@Composable
fun MainScreen(
    viewModel: DiaryViewModel,
    isDemo: Boolean,
    onSwitchDiary: (demo: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val currentTop = TopLevelDestination.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.route == top.route } == true
    }
    val showNavigation = currentTop != null
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    val onSelectTab: (TopLevelDestination) -> Unit = { navController.navigateTopLevel(it.route) }
    val onNavigate: (String) -> Unit = { route -> navController.navigateTopLevel(route) }
    val onOpenProfile: () -> Unit = {
        navController.navigate(Routes.PROFILE) { launchSingleTop = true }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // Полоса демо-режима всегда на виду, чтобы демо-записи не путали с настоящими
                if (isDemo) DemoModeBar(onExit = { onSwitchDiary(false) })
                AnimatedVisibility(
                    visible = showNavigation && !isWide,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    AppNavigationBar(current = currentTop, onSelect = onSelectTab)
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (showNavigation && isWide) {
                AppNavigationRail(current = currentTop, onSelect = onSelectTab)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    enterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                    exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                    popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                    popExitTransition = { fadeOut(tween(TAB_EXIT_MS)) }
                ) {
                    composable(Routes.HOME) {
                        DiaryScreen(
                            viewModel = viewModel,
                            onNavigate = onNavigate,
                            onOpenProfile = onOpenProfile
                        )
                    }
                    composable(Routes.TRAININGS) {
                        TrainingsScreen(viewModel = viewModel, onOpenProfile = onOpenProfile)
                    }
                    composable(Routes.COMPETITIONS) {
                        CompetitionsScreen(viewModel = viewModel, onOpenProfile = onOpenProfile)
                    }
                    composable(Routes.RECORDS) {
                        ResultsScreen(viewModel = viewModel, onOpenProfile = onOpenProfile)
                    }
                    composable(Routes.CATALOG) {
                        CatalogScreen(viewModel = viewModel, onOpenProfile = onOpenProfile)
                    }
                    composable(
                        route = Routes.PROFILE,
                        // Профиль — «push»-экран: въезжает справа, назад — уезжает вправо
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(250)
                            ) + fadeIn(tween(250))
                        },
                        exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                        popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(250)
                            ) + fadeOut(tween(250))
                        }
                    ) {
                        ProfileScreen(
                            viewModel = viewModel,
                            isDemo = isDemo,
                            onSwitchDiary = onSwitchDiary,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

/** Тонкая полоса над навигацией: открыт демо-дневник, одно нажатие — вернуться к своим данным. */
@Composable
private fun DemoModeBar(onExit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = "Демо-режим: данные для примера",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            TextButton(onClick = onExit) { Text("Мои данные") }
        }
    }
}

@Composable
private fun AppNavigationBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { item ->
            val selected = item == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** На планшетах и в ландшафте нижняя панель заменяется боковой навигацией. */
@Composable
private fun AppNavigationRail(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { item ->
            val selected = item == current
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label, fontSize = 12.sp, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
