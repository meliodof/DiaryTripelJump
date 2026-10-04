package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) {
            when (selectedTab) {
                0 -> DiaryScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { selectedTab = it },
                    onNavigateToProfile = { selectedTab = 5 } // Навигация в отдельный Профиль
                )
                1 -> TrainingsScreen(viewModel = viewModel)
                2 -> CompetitionsScreen(viewModel = viewModel)
                3 -> ResultsScreen(viewModel = viewModel)
                4 -> AnalyticsScreen(viewModel = viewModel)
                5 -> ProfileScreen(viewModel = viewModel)
            }
        }

        // Floating Bottom Navigation Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = Icons.Default.Home,
                    label = "Главная"
                )
                NavItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = Icons.Default.FitnessCenter,
                    label = "Тренировки"
                )
                NavItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = Icons.Default.EmojiEvents,
                    label = "Старты"
                )
                NavItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = Icons.Default.MilitaryTech,
                    label = "Рекорды"
                )
                NavItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = Icons.Default.BarChart,
                    label = "Аналитика"
                )
            }
        }
    }
}

@Composable
fun NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val pillBg = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(if (selected) pillBg else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor,
            fontSize = 10.sp
        )
    }
}
