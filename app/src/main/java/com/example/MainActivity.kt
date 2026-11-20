package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.configureAppCheck
import com.example.ui.SifatViewModel
import com.example.ui.navigation.Routes
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CommandHistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VoiceAssistantScreen
import com.example.ui.theme.SifatAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (REQUIRED): Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: SifatViewModel = viewModel()
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissions()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            SifatAgentTheme(themeMode = prefs.themeMode) {
                SifatAppRoot(viewModel = viewModel)
            }
        }
    }
}

private data class TopLevelNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelNavItem(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    TopLevelNavItem(Routes.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "tab_chat"),
    TopLevelNavItem(Routes.VOICE, "Voice", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "tab_voice"),
    TopLevelNavItem(Routes.AUTOMATION, "Automate", Icons.Filled.Bolt, Icons.Outlined.Bolt, "tab_automation"),
    TopLevelNavItem(Routes.HISTORY, "History", Icons.Filled.History, Icons.Outlined.History, "tab_history")
)

@Composable
fun SifatAppRoot(viewModel: SifatViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showPrimaryNav = currentRoute in topLevelDestinations.map { it.route }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showPrimaryNav && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showPrimaryNav && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.SPLASH) {
                        SplashScreen(
                            onFinished = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Routes.HOME) {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateChat = { navigateTopLevel(Routes.CHAT) },
                            onNavigateVoice = { navigateTopLevel(Routes.VOICE) },
                            onNavigateAutomation = { navigateTopLevel(Routes.AUTOMATION) },
                            onNavigatePermissions = { navController.navigate(Routes.PERMISSIONS) },
                            onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                            onNavigateHistory = { navigateTopLevel(Routes.HISTORY) }
                        )
                    }

                    composable(Routes.CHAT) {
                        ChatScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopLevel(Routes.HOME) }
                        )
                    }

                    composable(Routes.VOICE) {
                        VoiceAssistantScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopLevel(Routes.HOME) },
                            onOpenChat = { navigateTopLevel(Routes.CHAT) },
                            onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) }
                        )
                    }

                    composable(Routes.AUTOMATION) {
                        AutomationScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopLevel(Routes.HOME) },
                            onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) }
                        )
                    }

                    composable(Routes.HISTORY) {
                        CommandHistoryScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopLevel(Routes.HOME) }
                        )
                    }

                    composable(Routes.PERMISSIONS) {
                        PermissionSetupScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY) }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY) }
                        )
                    }

                    composable(Routes.PRIVACY) {
                        PrivacyPolicyScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
