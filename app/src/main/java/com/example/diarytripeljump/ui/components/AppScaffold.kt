package com.example.diarytripeljump.ui.components

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight

/**
 * Каркас вкладки верхнего уровня: сворачивающийся крупный заголовок, аватар профиля справа
 * и слот для FAB. Список внутри должен быть единственным вертикально прокручиваемым контейнером.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    avatarUri: String?,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    /** Кнопка «?» с подсказками по экрану; null — кнопки нет. */
    onHelp: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    if (onHelp != null) {
                        androidx.compose.material3.IconButton(onClick = onHelp, modifier = Modifier.coachTarget("top.help")) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Подсказки",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LocalOpenSettings.current?.let { openSettings ->
                        androidx.compose.material3.IconButton(onClick = openSettings, modifier = Modifier.coachTarget("top.settings")) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ProfileAvatarButton(avatarUri = avatarUri, onClick = onOpenProfile)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            androidx.compose.foundation.layout.Box(modifier = Modifier.coachTarget("fab")) { floatingActionButton() }
        },
        content = content
    )
}
