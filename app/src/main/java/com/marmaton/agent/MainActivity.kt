package com.marmaton.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.marmaton.agent.llm.BackendConfig
import com.marmaton.agent.llm.SettingsPersistence
import com.marmaton.agent.ui.*
import com.marmaton.agent.ui.theme.IonVioletTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val persistence = SettingsPersistence(this)

        setContent {
            IonVioletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var configState by remember { mutableStateOf<BackendConfig?>(null) }
                    LaunchedEffect(Unit) {
                        configState = persistence.configFlow.first()
                    }

                    val config = configState
                    if (config == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        val navController = rememberNavController()
                        val startDestination = if (config.isOnboardingCompleted) {
                            "home"
                        } else {
                            "onboarding"
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onFinished = {
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                MainBottomNavigationWrapper(
                                    currentTab = "home",
                                    onTabSelected = { tab ->
                                        if (tab != "home") {
                                            navController.navigate(tab) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                ) {
                                    HomeControlScreen()
                                }
                            }

                            composable("activity") {
                                MainBottomNavigationWrapper(
                                    currentTab = "activity",
                                    onTabSelected = { tab ->
                                        if (tab != "activity") {
                                            navController.navigate(tab) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                ) {
                                    ActivityLogScreen()
                                }
                            }

                            composable("backends") {
                                MainBottomNavigationWrapper(
                                    currentTab = "backends",
                                    onTabSelected = { tab ->
                                        if (tab != "backends") {
                                            navController.navigate(tab) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                ) {
                                    BackendsListScreen(
                                        onNavigateToLocalDetail = { navController.navigate("local_detail") },
                                        onNavigateToOllamaDetail = { navController.navigate("ollama_detail") },
                                        onNavigateToCloudDetail = { navController.navigate("cloud_detail") }
                                    )
                                }
                            }

                            composable("local_detail") {
                                LocalBackendDetailScreen(onBack = { navController.popBackStack() })
                            }

                            composable("ollama_detail") {
                                OllamaBackendDetailScreen(onBack = { navController.popBackStack() })
                            }

                            composable("cloud_detail") {
                                CloudBackendDetailScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainBottomNavigationWrapper(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == "home",
                    onClick = { onTabSelected("home") },
                    icon = {
                        BadgedBox(badge = {}) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                    },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = currentTab == "activity",
                    onClick = { onTabSelected("activity") },
                    icon = {
                        BadgedBox(badge = {}) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity")
                        }
                    },
                    label = { Text(stringResource(R.string.nav_activity)) }
                )
                NavigationBarItem(
                    selected = currentTab == "backends",
                    onClick = { onTabSelected("backends") },
                    icon = {
                        BadgedBox(badge = {}) {
                            Icon(Icons.Default.Settings, contentDescription = "Backends")
                        }
                    },
                    label = { Text(stringResource(R.string.nav_backends)) }
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
