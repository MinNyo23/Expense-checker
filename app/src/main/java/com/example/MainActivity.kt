package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AddExpenseScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CoupleExpenseViewModel

enum class AppDestination(val title: String) {
    HOME("Home"),
    ADD("Add"),
    MAP("Map"),
    HISTORY("History")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: CoupleExpenseViewModel = viewModel()
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: CoupleExpenseViewModel) {
    val userSession by viewModel.userSession.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by remember { mutableStateOf(AppDestination.HOME) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    if (!userSession.isPaired) {
        PairingScreen(
            viewModel = viewModel,
            onOpenSettings = { isSettingsOpen = true }
        )
    } else {
        // Back navigation handler
        BackHandler(enabled = currentScreen != AppDestination.HOME) {
            currentScreen = AppDestination.HOME
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                    NavigationBarItem(
                        selected = currentScreen == AppDestination.HOME,
                        onClick = { currentScreen = AppDestination.HOME },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppDestination.ADD,
                        onClick = { currentScreen = AppDestination.ADD },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Add") },
                        label = { Text("Add") },
                        modifier = Modifier.testTag("nav_item_add")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppDestination.MAP,
                        onClick = { currentScreen = AppDestination.MAP },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                        label = { Text("Map") },
                        modifier = Modifier.testTag("nav_item_map")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppDestination.HISTORY,
                        onClick = { currentScreen = AppDestination.HISTORY },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "History") },
                        label = { Text("History") },
                        modifier = Modifier.testTag("nav_item_history")
                    )
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentScreen,
                modifier = Modifier.padding(innerPadding),
                label = "screen_transition"
            ) { destination ->
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToAdd = { currentScreen = AppDestination.ADD },
                        onNavigateToHistory = { currentScreen = AppDestination.HISTORY },
                        onNavigateToMap = { currentScreen = AppDestination.MAP },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                    AppDestination.ADD -> AddExpenseScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = AppDestination.HOME }
                    )
                    AppDestination.MAP -> MapScreen(
                        viewModel = viewModel
                    )
                    AppDestination.HISTORY -> HistoryScreen(
                        viewModel = viewModel,
                        onNavigateToMap = { currentScreen = AppDestination.MAP }
                    )
                }
            }
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { isSettingsOpen = false }
        )
    }
}
