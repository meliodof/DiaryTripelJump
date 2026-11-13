package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.CoachLayer
import androidx.compose.runtime.CompositionLocalProvider
import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.ui.components.LocalOpenSettings
import com.example.diarytripeljump.ui.components.LocalCoach
import com.example.diarytripeljump.ui.components.CoachOverlay
import com.example.diarytripeljump.ui.components.CoachController
import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.example.diarytripeljump.data.RecordInsights
import com.example.diarytripeljump.data.RecordSummary
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
    const val SETTINGS = "settings"
    const val ANALYTICS = "analytics"
    const val VISUALIZATION = "analytics/visualization"
    const val ADVICE = "analytics/advice"
    const val ASSISTANT = "analytics/assistant"
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
    ANALYTICS(Routes.ANALYTICS, "Аналитика", Icons.Outlined.Insights, Icons.Filled.Insights),
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
    // кнопка «Новая тренировка» на главной сразу открывает окно на вкладке тренировок
    var openNewTraining by remember { mutableStateOf(false) }

    // Новый рекорд празднуется один раз; первое обновление только запоминает текущие значения
    val records by viewModel.records.collectAsState()
    var knownBest by remember(viewModel) { mutableStateOf<Map<String, Double>?>(null) }
    var celebrated by remember { mutableStateOf<RecordSummary?>(null) }
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            RecordInsights.newRecord(knownBest, records)?.let { celebrated = it }
            knownBest = records.associate { it.key to it.best }
        }
    }

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
    val onOpenSettings: () -> Unit = {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    // подсказки: подсветка элементов; обучение идёт по разделам и само открывает нужный экран
    val coach = remember { CoachController() }
    val currentRoute = destination?.route
    LaunchedEffect(coach.current, coach.navigates) {
        val step = coach.current ?: return@LaunchedEffect
        if (coach.navigates && currentRoute != step.route) {
            if (TopLevelDestination.entries.any { it.route == step.route }) navController.navigateTopLevel(step.route)
            else navController.navigate(step.route) { launchSingleTop = true }
        }
    }
    val finishTour: () -> Unit = { if (viewModel.finishTutorial()) onSwitchDiary(false) }
    val showTour: (Int) -> Unit = { start ->
        coach.show(HelpTopic.tour, start, navigates = true, onStep = { viewModel.saveTutorialStep(it) }, onFinish = finishTour)
    }
    // обучение идёт на демо-дневнике: если открыт свой, сначала переключаемся, и обучение продолжится после перезапуска экрана
    val startTour: () -> Unit = { if (viewModel.beginTutorial(isDemo)) onSwitchDiary(true) else showTour(0) }
    LaunchedEffect(Unit) { if (viewModel.tutorialActive) showTour(viewModel.tutorialStep) }
    var showWelcome by remember { mutableStateOf(!viewModel.tutorialSeen && !viewModel.tutorialActive) }

    CompositionLocalProvider(LocalCoach provides coach, LocalOpenSettings provides onOpenSettings) {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // Полоса демо-режима всегда на виду, чтобы демо-записи не путали с настоящими
                if (isDemo && coach.current == null) DemoModeBar(onExit = { onSwitchDiary(false) })
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
                            onOpenProfile = onOpenProfile,
                            onNewTraining = {
                                openNewTraining = true
                                navController.navigateTopLevel(Routes.TRAININGS)
                            }
                        )
                    }
                    composable(Routes.TRAININGS) {
                        TrainingsScreen(
                            viewModel = viewModel,
                            onOpenProfile = onOpenProfile,
                            openNewTraining = openNewTraining,
                            onNewTrainingOpened = { openNewTraining = false }
                        )
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
                    composable(Routes.ANALYTICS) {
                        AnalyticsScreen(
                            viewModel = viewModel,
                            onOpenProfile = onOpenProfile,
                            onOpenVisualization = { navController.navigate(Routes.VISUALIZATION) { launchSingleTop = true } },
                            onOpenAdvice = { navController.navigate(Routes.ADVICE) { launchSingleTop = true } }
                        )
                    }
                    composable(
                        route = Routes.ADVICE,
                        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250)) },
                        exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                        popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250)) }
                    ) {
                        AdviceScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { route ->
                                if (TopLevelDestination.entries.any { it.route == route }) navController.navigateTopLevel(route)
                                else navController.navigate(route) { launchSingleTop = true }
                            },
                            onOpenAssistant = { navController.navigate(Routes.ASSISTANT) { launchSingleTop = true } }
                        )
                    }
                    composable(
                        route = Routes.ASSISTANT,
                        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250)) },
                        exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                        popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250)) }
                    ) {
                        AssistantScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { route ->
                                if (TopLevelDestination.entries.any { it.route == route }) navController.navigateTopLevel(route)
                                else navController.navigate(route) { launchSingleTop = true }
                            }
                        )
                    }
                    composable(
                        route = Routes.VISUALIZATION,
                        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250)) },
                        exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                        popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250)) }
                    ) {
                        VisualizationScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
                    composable(
                        route = Routes.SETTINGS,
                        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250)) },
                        exitTransition = { fadeOut(tween(TAB_EXIT_MS)) },
                        popEnterTransition = { fadeIn(tween(TAB_ENTER_MS)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250)) }
                    ) {
                        SettingsScreen(
                            viewModel = viewModel,
                            isDemo = isDemo,
                            onBack = { navController.popBackStack() },
                            onStartTutorial = startTour,
                            onOpenTopic = { topic -> coach.show(topic.tourSteps, navigates = true) },
                            onLeaveDemo = { onSwitchDiary(false) },
                            onOpenDemo = { onSwitchDiary(true) }
                        )
                    }
                }
            }
        }
    }
    CoachLayer(coach)
    }
    }

    celebrated?.let { record ->
        RecordCelebration(record = record, onDismiss = { celebrated = null })
    }

    // при первом входе предлагаем обучение; отказаться можно, пройти — позже в настройках
    if (showWelcome) {
        WelcomeDialog(
            onStart = {
                showWelcome = false
                startTour()
            },
            onSkip = {
                showWelcome = false
                viewModel.skipTutorial()
            }
        )
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

/** Плавающая панель: цветная подложка перетекает к выбранной вкладке, нажатие отдаётся лёгкой вибрацией. */
@Composable
private fun AppNavigationBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val items = TopLevelDestination.entries
    val shape = RoundedCornerShape(26.dp)
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .coachTarget("nav")
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(66.dp),
        shape = shape,
        color = cs.surface,
        shadowElevation = 10.dp
    ) {
        // выбранная вкладка шире остальных и показывает подпись: шесть вкладок помещаются без мелкого текста
        Row(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            items.forEach { item ->
                val selected = item == current
                val weight by animateFloatAsState(
                    targetValue = if (selected) 2.4f else 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                    label = "navWeight"
                )
                val background by animateColorAsState(if (selected) cs.primary else Color.Transparent, label = "navBackground")
                val color by animateColorAsState(if (selected) cs.onPrimary else cs.onSurfaceVariant, label = "navColor")
                Row(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(21.dp))
                        .background(background)
                        .selectable(selected = selected, role = Role.Tab, onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(item)
                        }),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                    AnimatedVisibility(visible = selected) {
                        Text(
                            text = item.label,
                            color = color,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
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
