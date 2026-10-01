package com.example.diarytripeljump

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> DiaryScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { selectedTab = it }
                )
                1 -> ResultsScreen(viewModel = viewModel)
                2 -> TrainingsScreen(viewModel = viewModel)
                3 -> CompetitionsScreen(viewModel = viewModel)
                4 -> ProfileScreen(viewModel = viewModel)
            }
        }

        // Bottom Navigation с иконками
        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.Home, contentDescription = "Дневник") },
                label = { Text("Дневник") }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Результаты") },
                label = { Text("Результаты") }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Тренировки") },
                label = { Text("Тренировки") }
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = { Icon(Icons.Default.Event, contentDescription = "Соревнования") },
                label = { Text("Соревнования") }
            )
            NavigationBarItem(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                label = { Text("Профиль") }
            )
        }
    }
}
