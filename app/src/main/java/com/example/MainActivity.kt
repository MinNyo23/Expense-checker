package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AddExpenseScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RomanticAppBackground
import com.example.ui.theme.RomanticLoveBrush
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RosePrimary
import com.example.ui.viewmodel.CoupleExpenseViewModel

import android.view.WindowManager
import coil.Coil
import com.example.data.model.AuthState
import com.example.ui.components.PermissionRequestDialog
import com.example.ui.screens.AccountPendingScreen
import com.example.ui.screens.PinLockScreen
import com.example.util.CoupleImageLoader
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

enum class AppDestination(val title: String) {
    HOME("Home"),
    HISTORY("History"),
    ADD("Add"),
    MAP("Map"),
    PROFILE("Profile")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        Coil.setImageLoader(CoupleImageLoader.get(applicationContext))
        setContent {
            MyApplicationTheme {
                val viewModel: CoupleExpenseViewModel = viewModel()
                val userSession by viewModel.userSession.collectAsStateWithLifecycle()

                LaunchedEffect(userSession.isScreenSecurityEnabled) {
                    if (userSession.isScreenSecurityEnabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: CoupleExpenseViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isSessionInitializing by viewModel.isSessionInitializing.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAuthenticatedCouple = authState is AuthState.SignedIn

    var currentScreen by remember { mutableStateOf(AppDestination.HOME) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(PermissionHelper.hasLocationPermission(context))
    }
    var attemptedLocationRequest by remember { mutableStateOf(false) }
    var dismissInitialPermissionPrompt by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = PermissionHelper.hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val initialLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        attemptedLocationRequest = true
        val granted = PermissionHelper.hasLocationPermission(context)
        hasLocationPermission = granted
        viewModel.markPermissionsPrompted()
        dismissInitialPermissionPrompt = true
        if (granted) {
            viewModel.onLocationPermissionAvailable()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location permission granted! Live map tracking active.")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location skipped. You can grant it anytime in Date Map or Settings.")
            }
        }
    }

    LaunchedEffect(hasLocationPermission, isAuthenticatedCouple, userSession.isPinSet, isAppUnlocked) {
        if (hasLocationPermission && isAuthenticatedCouple && (!userSession.isPinSet || isAppUnlocked)) {
            viewModel.onLocationPermissionAvailable()
        }
    }

    LaunchedEffect(isAuthenticatedCouple) {
        if (!isAuthenticatedCouple) {
            currentScreen = AppDestination.HOME
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    if (isSessionInitializing) {
        RomanticAppBackground(isEntryScreen = true) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    shadowElevation = 10.dp,
                    color = Color(0xFFFEDADE),
                    modifier = Modifier
                        .size(104.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(30.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_couple_heart_emblem_1794818360746),
                        contentDescription = "Couple Expense App Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(30.dp))
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Couple Expense",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Shared budgets, memories & live date map",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = RoseContainer,
                        shape = RoundedCornerShape(999.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Restoring your couple session...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else if (userSession.isPinSet && !isAppUnlocked && isAuthenticatedCouple) {
        PinLockScreen(
            onPinEntered = { enteredPin ->
                viewModel.unlockApp(enteredPin)
            }
        )
    } else if (authState is AuthState.AccountNotLinked) {
        val pending = authState as AuthState.AccountNotLinked
        AccountPendingScreen(
            email = pending.email,
            message = pending.message,
            isChecking = isRefreshing,
            onRecheckProfileLink = { viewModel.recheckAccountProfileLink() },
            onSignOut = { viewModel.logout() }
        )
    } else if (!isAuthenticatedCouple) {
        PairingScreen(
            viewModel = viewModel,
            onOpenSettings = { isSettingsOpen = true }
        )
    } else {
        // Preserve scroll positions across bottom-nav tab switches
        val homeListState = rememberLazyListState()
        val historyListState = rememberLazyListState()
        val profileListState = rememberLazyListState()

        // Back navigation handler
        BackHandler(enabled = currentScreen != AppDestination.HOME) {
            currentScreen = AppDestination.HOME
        }

        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = RosePrimary,
            selectedTextColor = RosePrimary,
            indicatorColor = RoseContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RomanticAppBackground(isEntryScreen = false) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 0.75.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        )
                        NavigationBar(
                            containerColor = Color(0xFCFFFFFF),
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == AppDestination.HOME,
                                onClick = { currentScreen = AppDestination.HOME },
                                colors = navItemColors,
                                icon = { Icon(Icons.Default.Favorite, contentDescription = "Home & Love") },
                                label = { Text("Love Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("nav_item_home")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppDestination.HISTORY,
                                onClick = { currentScreen = AppDestination.HISTORY },
                                colors = navItemColors,
                                icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "History") },
                                label = { Text("Records", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("nav_item_history")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppDestination.ADD,
                                onClick = { currentScreen = AppDestination.ADD },
                                colors = navItemColors,
                                icon = {
                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = "Add Expense",
                                        tint = if (currentScreen == AppDestination.ADD) RosePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = { Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_item_add")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppDestination.MAP,
                                onClick = { currentScreen = AppDestination.MAP },
                                colors = navItemColors,
                                icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                                label = { Text("Date Map", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("nav_item_map")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppDestination.PROFILE,
                                onClick = { currentScreen = AppDestination.PROFILE },
                                colors = navItemColors,
                                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                label = { Text("Profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("nav_item_profile")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentScreen) {
                        AppDestination.HOME -> HomeScreen(
                            viewModel = viewModel,
                            listState = homeListState,
                            onNavigateToAdd = { currentScreen = AppDestination.ADD },
                            onNavigateToHistory = { currentScreen = AppDestination.HISTORY },
                            onNavigateToMap = { currentScreen = AppDestination.MAP },
                            onNavigateToProfile = { currentScreen = AppDestination.PROFILE },
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
                            listState = historyListState,
                            onNavigateToMap = { currentScreen = AppDestination.MAP }
                        )
                        AppDestination.PROFILE -> ProfileScreen(
                            viewModel = viewModel,
                            listState = profileListState,
                            onNavigateBack = { currentScreen = AppDestination.HOME }
                        )
                    }
                }
            }
        }
    }

    val isReadyForPermissionsPrompt = !isSessionInitializing &&
        (!userSession.isPinSet || isAppUnlocked) &&
        isAuthenticatedCouple &&
        !hasLocationPermission &&
        !userSession.hasPromptedPermissions &&
        !dismissInitialPermissionPrompt &&
        !isSettingsOpen

    if (isReadyForPermissionsPrompt) {
        val permanentlyDenied = attemptedLocationRequest && !PermissionHelper.shouldShowLocationRationale(context)
        PermissionRequestDialog(
            hasLocationPermission = hasLocationPermission,
            permanentlyDenied = permanentlyDenied,
            onRequestLocation = {
                initialLocationLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
            },
            onOpenSettings = {
                viewModel.markPermissionsPrompted()
                dismissInitialPermissionPrompt = true
                PermissionHelper.openAppSettings(context)
            },
            onDismiss = {
                viewModel.markPermissionsPrompted()
                dismissInitialPermissionPrompt = true
            }
        )
    }

    if (isSettingsOpen) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { isSettingsOpen = false }
        )
    }
}
