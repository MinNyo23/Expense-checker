package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.LiveLocation
import com.example.ui.components.HistoryTimelineStripCard
import com.example.ui.components.LeafletMapWebView
import com.example.ui.components.LocationHistoryModalSheet
import com.example.ui.components.MapFocusRequest
import com.example.ui.components.MapProfileSelectorBar
import com.example.ui.components.SelectedHistoryNodeCard
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import com.example.ui.viewmodel.CoupleExpenseViewModel
import com.example.util.FormatUtils
import com.example.util.HistoryVisitNode
import com.example.util.LocationHistoryAnalyzer
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: CoupleExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val partnerProfile by viewModel.partnerProfile.collectAsStateWithLifecycle()
    val liveLocations by viewModel.liveLocations.collectAsStateWithLifecycle()
    val partnerLiveLocation by viewModel.partnerLiveLocation.collectAsStateWithLifecycle()
    val myLiveLocation by viewModel.myLiveLocation.collectAsStateWithLifecycle()
    val locationHistory by viewModel.locationHistory.collectAsStateWithLifecycle()
    val liveUserCoords by viewModel.currentUserCoordinates.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val myDisplayName = myProfile?.user_name?.ifBlank { userSession.userName }
        ?: userSession.userName.ifBlank { "You" }
    val myAvatarUrl = myProfile?.avatar_url?.ifBlank { userSession.avatarPath }
        ?: userSession.avatarPath
    val partnerDisplayName = partnerProfile?.user_name?.ifBlank { userSession.partnerName }
        ?: userSession.partnerName.ifBlank { "Partner" }
    val partnerAvatarUrl = partnerProfile?.avatar_url?.ifBlank { userSession.partnerAvatarUrl }
        ?: userSession.partnerAvatarUrl

    // Map view & filter states
    var isHistoryMode by remember { mutableStateOf(false) }
    var filterUser by remember { mutableStateOf("ALL") } // "ALL", "ME", "PARTNER"
    var historyDateFilter by remember { mutableStateOf("ALL") } // "ALL" or "yyyy-MM-dd"

    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var selectedLiveLocation by remember { mutableStateOf<LiveLocation?>(null) }
    var selectedHistoryNode by remember { mutableStateOf<HistoryVisitNode?>(null) }

    var isLocating by remember { mutableStateOf(false) }
    var centerOnUserTrigger by remember { mutableStateOf(0L) }
    var focusRequest by remember { mutableStateOf<MapFocusRequest?>(null) }
    var fitHistoryTrigger by remember { mutableStateOf(0L) }

    var showHistorySheet by remember { mutableStateOf(false) }
    var showAddCheckpointDialog by remember { mutableStateOf(false) }
    var checkpointNoteText by remember { mutableStateOf("") }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(PermissionHelper.hasLocationPermission(context))
    }
    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }
    var showPermissionDeniedBanner by remember { mutableStateOf(false) }
    var hasAttemptedPermissionRequest by remember { mutableStateOf(false) }

    // Effective live coordinates of signed-in user
    val effectiveUserLocation: Pair<Double, Double>? = remember(liveUserCoords, myLiveLocation) {
        liveUserCoords ?: myLiveLocation?.let { it.latitude to it.longitude }
    }

    // Optional notification permission launcher (non-blocking for background foreground-service status)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Refresh permissions on ON_RESUME
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionHelper.hasLocationPermission(context)
                hasLocationPermission = granted
                if (granted) {
                    showPermissionDeniedBanner = false
                    viewModel.onLocationPermissionAvailable()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Automatically acquire default current location & start background live tracking on Map open
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.onLocationPermissionAvailable()
            isLocating = true
            val coords = viewModel.refreshCurrentUserCoordinates(highAccuracy = true)
            isLocating = false
            if (coords != null && centerOnUserTrigger == 0L && !isHistoryMode) {
                centerOnUserTrigger = System.currentTimeMillis()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAttemptedPermissionRequest = true
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            PermissionHelper.hasLocationPermission(context)
        hasLocationPermission = granted
        val action = pendingPermissionAction
        pendingPermissionAction = null

        if (granted) {
            showPermissionDeniedBanner = false
            viewModel.onLocationPermissionAvailable()
            when (action) {
                "SHARING" -> {
                    viewModel.setLocationSharing(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    coroutineScope.launch {
                        isLocating = true
                        viewModel.broadcastCurrentLocation(highAccuracy = true)
                        centerOnUserTrigger = System.currentTimeMillis()
                        isLocating = false
                    }
                }
                "CHECKPOINT" -> {
                    showAddCheckpointDialog = true
                }
                else -> {
                    coroutineScope.launch {
                        isLocating = true
                        viewModel.broadcastCurrentLocation(highAccuracy = true)
                        centerOnUserTrigger = System.currentTimeMillis()
                        isLocating = false
                    }
                }
            }
        } else {
            showPermissionDeniedBanner = true
        }
    }

    // Filtered expenses based on selected profile
    val filteredExpenses = remember(expenses, filterUser, userSession.userName) {
        val withCoords = expenses.filter { it.latitude != null && it.longitude != null }
        when (filterUser) {
            "ME" -> withCoords.filter { it.user_name.equals(userSession.userName, ignoreCase = true) }
            "PARTNER" -> withCoords.filter { !it.user_name.equals(userSession.userName, ignoreCase = true) }
            else -> withCoords
        }
    }

    // Filtered live markers (shows all by default, or emphasizes selected user)
    val visibleLiveLocations = remember(liveLocations, filterUser, userSession.userName) {
        when (filterUser) {
            "ME" -> liveLocations.filter { it.user_name.equals(userSession.userName, ignoreCase = true) }
            "PARTNER" -> liveLocations.filter { !it.user_name.equals(userSession.userName, ignoreCase = true) }
            else -> liveLocations
        }
    }

    // Available dates recorded in location history
    val availableHistoryDates = remember(locationHistory) {
        locationHistory
            .map { it.date }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
    }
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Filtered history items -> analyzed into exact-time Going Dots & Stay Points
    val analyzedAllNodes = remember(locationHistory, userSession.userName) {
        LocationHistoryAnalyzer.buildTimeline(locationHistory, userSession.userName)
    }

    val filteredHistoryNodes = remember(analyzedAllNodes, filterUser, historyDateFilter) {
        analyzedAllNodes.filter { node ->
            val matchesUser = when (filterUser) {
                "ME" -> node.isCurrentUser
                "PARTNER" -> !node.isCurrentUser
                else -> true
            }
            val matchesDate = historyDateFilter == "ALL" || node.date == historyDateFilter
            matchesUser && matchesDate
        }
    }

    // Helper: Focus on Partner's ("Girlfriend's") current live location or latest spot
    fun focusPartnerCurrentLocation() {
        filterUser = "PARTNER"
        selectedExpense = null
        selectedHistoryNode = null
        val pLive = partnerLiveLocation
        if (pLive != null) {
            selectedLiveLocation = pLive
            focusRequest = MapFocusRequest(
                latitude = pLive.latitude,
                longitude = pLive.longitude,
                label = pLive.user_name
            )
            return
        }
        val latestPartnerHistory = analyzedAllNodes.firstOrNull { !it.isCurrentUser }
        if (latestPartnerHistory != null) {
            val fallbackLive = LiveLocation(
                id = "history-fallback-${latestPartnerHistory.id}",
                couple_id = userSession.coupleId,
                user_name = latestPartnerHistory.userName,
                latitude = latestPartnerHistory.latitude,
                longitude = latestPartnerHistory.longitude,
                is_sharing = false,
                place_name = latestPartnerHistory.placeName,
                timestamp = "${latestPartnerHistory.arrivalTimeExact} (${latestPartnerHistory.date})",
                updated_at = latestPartnerHistory.departureMillis
            )
            selectedLiveLocation = fallbackLive
            focusRequest = MapFocusRequest(
                latitude = latestPartnerHistory.latitude,
                longitude = latestPartnerHistory.longitude,
                label = latestPartnerHistory.userName
            )
            return
        }
        val partnerExp = expenses.firstOrNull {
            !it.user_name.equals(userSession.userName, ignoreCase = true) && it.latitude != null && it.longitude != null
        }
        if (partnerExp != null && partnerExp.latitude != null && partnerExp.longitude != null) {
            selectedExpense = partnerExp
            focusRequest = MapFocusRequest(
                latitude = partnerExp.latitude,
                longitude = partnerExp.longitude,
                label = partnerExp.user_name
            )
        } else {
            selectedLiveLocation = null
            viewModel.showMessage("Waiting for $partnerDisplayName to share live location or pin a spot.")
        }
    }

    // Helper: Focus on Signed-in User's current location
    fun focusMyCurrentLocation() {
        filterUser = "ME"
        selectedExpense = null
        selectedHistoryNode = null
        val coords = effectiveUserLocation
        if (coords != null) {
            selectedLiveLocation = myLiveLocation ?: LiveLocation(
                id = "self-live",
                couple_id = userSession.coupleId,
                user_name = myDisplayName,
                latitude = coords.first,
                longitude = coords.second,
                is_sharing = userSession.isLocationSharingEnabled,
                place_name = "Current GPS Position",
                timestamp = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
            )
            centerOnUserTrigger = System.currentTimeMillis()
        } else if (hasLocationPermission) {
            coroutineScope.launch {
                isLocating = true
                val fresh = viewModel.refreshCurrentUserCoordinates(highAccuracy = true)
                isLocating = false
                if (fresh != null) {
                    centerOnUserTrigger = System.currentTimeMillis()
                } else {
                    viewModel.showMessage("Acquiring GPS signal...")
                }
            }
        } else {
            pendingPermissionAction = "LOCATE"
            permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
        }
    }

    val hasBottomDetailCard = selectedExpense != null || selectedLiveLocation != null || selectedHistoryNode != null
    val hasHistoryStrip = isHistoryMode && selectedHistoryNode == null && selectedLiveLocation == null && selectedExpense == null
    val fabBottomPadding = when {
        hasBottomDetailCard -> 168.dp
        hasHistoryStrip -> 166.dp
        else -> 24.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Leaflet OpenStreetMap WebView (100% free, no API key)
        LeafletMapWebView(
            expenses = if (isHistoryMode) emptyList() else filteredExpenses,
            currentUserName = userSession.userName,
            userLocation = effectiveUserLocation,
            centerOnUserTrigger = centerOnUserTrigger,
            liveLocations = visibleLiveLocations,
            historyNodes = filteredHistoryNodes,
            showHistoryTrail = isHistoryMode,
            fitHistoryTrigger = fitHistoryTrigger,
            focusRequest = focusRequest,
            onMarkerClick = { exp ->
                selectedExpense = exp
                selectedLiveLocation = null
                selectedHistoryNode = null
            },
            onLiveMarkerClick = { live ->
                selectedLiveLocation = live
                selectedExpense = null
                selectedHistoryNode = null
            },
            onHistoryNodeClick = { node ->
                selectedHistoryNode = node
                selectedExpense = null
                selectedLiveLocation = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Header, Mode Switcher, Clickable Profiles & Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header + Live/History Mode Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isHistoryMode) "Location History Map" else "Couple Live Map",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (userSession.isLocationSharingEnabled) {
                                    "Background Live Active • Free OSM"
                                } else {
                                    "Leaflet • Free OpenStreetMap"
                                },
                                fontSize = 10.sp,
                                color = if (userSession.isLocationSharingEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode Pills: Live vs History
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (!isHistoryMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable {
                                        isHistoryMode = false
                                        selectedHistoryNode = null
                                    }
                                    .testTag("map_mode_live")
                            ) {
                                Text(
                                    text = "Live",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isHistoryMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isHistoryMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable {
                                        isHistoryMode = true
                                        selectedExpense = null
                                        selectedLiveLocation = null
                                        fitHistoryTrigger = System.currentTimeMillis()
                                    }
                                    .testTag("map_mode_history")
                            ) {
                                Text(
                                    text = "History",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHistoryMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Clickable Profile Selector Bar: Both / You / Girlfriend (Partner)
            MapProfileSelectorBar(
                selectedFilter = filterUser,
                myName = myDisplayName,
                myAvatarUrl = myAvatarUrl,
                myLiveLocation = myLiveLocation,
                hasLocalGps = effectiveUserLocation != null,
                partnerName = partnerDisplayName,
                partnerAvatarUrl = partnerAvatarUrl,
                partnerLiveLocation = partnerLiveLocation,
                onSelectAll = {
                    filterUser = "ALL"
                    selectedExpense = null
                    selectedLiveLocation = null
                    selectedHistoryNode = null
                    if (isHistoryMode) {
                        fitHistoryTrigger = System.currentTimeMillis()
                    }
                },
                onSelectMe = {
                    if (isHistoryMode) {
                        filterUser = "ME"
                        selectedHistoryNode = null
                        fitHistoryTrigger = System.currentTimeMillis()
                    } else {
                        focusMyCurrentLocation()
                    }
                },
                onSelectPartner = {
                    if (isHistoryMode) {
                        filterUser = "PARTNER"
                        selectedHistoryNode = null
                        fitHistoryTrigger = System.currentTimeMillis()
                    } else {
                        focusPartnerCurrentLocation()
                    }
                }
            )

            // Contextual Bar: Live Sharing Switch (Live Mode) OR Date Filters + Legend (History Mode)
            if (!isHistoryMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (userSession.isLocationSharingEnabled) Color(0xFF2E7D32) else Color(0xFF9E9E9E))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (userSession.isLocationSharingEnabled) "Live Background Sharing Active" else "Background Sharing Paused",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (userSession.isLocationSharingEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (userSession.isLocationSharingEnabled) {
                                            "Updating live even in background • Tap $partnerDisplayName above to locate"
                                        } else {
                                            "Turn on to stream live spot & route history"
                                        },
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = userSession.isLocationSharingEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (PermissionHelper.hasLocationPermission(context)) {
                                            viewModel.setLocationSharing(true)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            pendingPermissionAction = "SHARING"
                                            permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                                        }
                                    } else {
                                        viewModel.setLocationSharing(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("map_live_sharing_switch")
                            )
                        }
                    }
                }
            } else {
                // History Mode Date Filter Chips + Going/Stay Legend
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = historyDateFilter == "ALL",
                                    onClick = {
                                        historyDateFilter = "ALL"
                                        fitHistoryTrigger = System.currentTimeMillis()
                                    },
                                    label = { Text("All Days", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = historyDateFilter == todayDate,
                                    onClick = {
                                        historyDateFilter = todayDate
                                        fitHistoryTrigger = System.currentTimeMillis()
                                    },
                                    label = { Text("Today", fontSize = 11.sp) }
                                )
                            }
                            items(availableHistoryDates.filter { it != todayDate }) { dateStr ->
                                FilterChip(
                                    selected = historyDateFilter == dateStr,
                                    onClick = {
                                        historyDateFilter = dateStr
                                        fitHistoryTrigger = System.currentTimeMillis()
                                    },
                                    label = { Text(dateStr, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "● Going Dot   📌 Stay Point (time stayed)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${filteredHistoryNodes.size} points",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!hasLocationPermission && showPermissionDeniedBanner) {
                val permanentlyDenied = hasAttemptedPermissionRequest && !PermissionHelper.shouldShowLocationRationale(context)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_location_permission_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (permanentlyDenied) {
                                    "Location blocked in Settings. Enable to show live location."
                                } else {
                                    "Location permission needed for live tracking & GPS."
                                },
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    if (permanentlyDenied) {
                                        PermissionHelper.openAppSettings(context)
                                    } else {
                                        pendingPermissionAction = "LOCATE"
                                        permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (permanentlyDenied) "Settings" else "Allow",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            IconButton(
                                onClick = { showPermissionDeniedBanner = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Floating Action Buttons: History List, Pin Checkpoint, GPS Current Location
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = fabBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(
                onClick = { showHistorySheet = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.testTag("map_history_timeline_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Location History",
                    modifier = Modifier.size(20.dp)
                )
            }

            SmallFloatingActionButton(
                onClick = {
                    if (PermissionHelper.hasLocationPermission(context)) {
                        showAddCheckpointDialog = true
                    } else {
                        pendingPermissionAction = "CHECKPOINT"
                        permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.testTag("map_add_checkpoint_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = "Pin Checkpoint",
                    modifier = Modifier.size(20.dp)
                )
            }

            // GPS My Current Location FAB
            FloatingActionButton(
                onClick = {
                    if (PermissionHelper.hasLocationPermission(context)) {
                        coroutineScope.launch {
                            isLocating = true
                            val coords = if (userSession.isLocationSharingEnabled) {
                                viewModel.broadcastCurrentLocation(highAccuracy = true)
                            } else {
                                viewModel.refreshCurrentUserCoordinates(highAccuracy = true)
                            }
                            isLocating = false
                            if (coords != null) {
                                selectedExpense = null
                                selectedLiveLocation = null
                                selectedHistoryNode = null
                                centerOnUserTrigger = System.currentTimeMillis()
                            } else {
                                viewModel.showMessage("Could not acquire GPS signal yet.")
                            }
                        }
                    } else {
                        pendingPermissionAction = "LOCATE"
                        permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                    }
                },
                modifier = Modifier.testTag("map_my_location_button"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Current GPS Location",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom Interactive Route & Stay Timeline Strip in History Mode
        AnimatedVisibility(
            visible = hasHistoryStrip,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            HistoryTimelineStripCard(
                nodes = filteredHistoryNodes,
                selectedNodeId = selectedHistoryNode?.id,
                onSelectNode = { node ->
                    selectedHistoryNode = node
                    focusRequest = MapFocusRequest(
                        latitude = node.latitude,
                        longitude = node.longitude,
                        label = node.placeName
                    )
                },
                onOpenFullSheet = { showHistorySheet = true }
            )
        }

        // Bottom Selected History Node Card (Exact Time + Stay Duration)
        AnimatedVisibility(
            visible = selectedHistoryNode != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedHistoryNode?.let { node ->
                SelectedHistoryNodeCard(
                    node = node,
                    onClose = { selectedHistoryNode = null },
                    onFocusOnMap = {
                        focusRequest = MapFocusRequest(
                            latitude = node.latitude,
                            longitude = node.longitude,
                            label = node.placeName
                        )
                    }
                )
            }
        }

        // Bottom Selected Live Location Card
        AnimatedVisibility(
            visible = selectedLiveLocation != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val live = selectedLiveLocation
            if (live != null) {
                val isMe = live.user_name.equals(userSession.userName, ignoreCase = true)
                val color = if (isMe) PartnerAColor else PartnerBColor

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_selected_live_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = live.user_name.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isMe) "Your Live Location" else "${live.user_name}'s Current Location",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = (if (live.is_sharing) "🟢 Live Now" else "📍 Last Known") +
                                            " • ${live.timestamp}" +
                                            (if (live.battery_level != null) " • 🔋${live.battery_level}%" else ""),
                                        fontSize = 12.sp,
                                        color = if (live.is_sharing) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedLiveLocation = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (live.place_name.isNotBlank()) {
                                    Text(
                                        text = "📍 ${live.place_name}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.5f, %.5f", live.latitude, live.longitude),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    selectedLiveLocation = null
                                    filterUser = if (isMe) "ME" else "PARTNER"
                                    isHistoryMode = true
                                    fitHistoryTrigger = System.currentTimeMillis()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Selected Expense Card
        AnimatedVisibility(
            visible = selectedExpense != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val exp = selectedExpense
            if (exp != null) {
                val category = ExpenseCategory.fromString(exp.category)
                val isMe = exp.user_name.equals(userSession.userName, ignoreCase = true)
                val color = if (isMe) PartnerAColor else PartnerBColor

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_selected_expense_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(category.color.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = exp.note.ifBlank { category.title },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${category.title} • ${exp.date}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedExpense = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = color.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isMe) "Paid by You" else "Paid by ${exp.user_name}",
                                    color = color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = FormatUtils.formatMmk(exp.amount),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Location History Timeline Full Sheet
    if (showHistorySheet) {
        LocationHistoryModalSheet(
            nodes = filteredHistoryNodes,
            selectedProfileFilter = filterUser,
            myName = myDisplayName,
            partnerName = partnerDisplayName,
            onSelectProfileFilter = { nextFilter ->
                filterUser = nextFilter
                isHistoryMode = true
                fitHistoryTrigger = System.currentTimeMillis()
            },
            onSelectNodeOnMap = { node ->
                showHistorySheet = false
                isHistoryMode = true
                selectedHistoryNode = node
                selectedLiveLocation = null
                selectedExpense = null
                focusRequest = MapFocusRequest(
                    latitude = node.latitude,
                    longitude = node.longitude,
                    label = node.placeName
                )
            },
            onPinCheckpoint = {
                showHistorySheet = false
                if (PermissionHelper.hasLocationPermission(context)) {
                    showAddCheckpointDialog = true
                } else {
                    pendingPermissionAction = "CHECKPOINT"
                    permissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                }
            },
            onClearHistory = { showClearHistoryConfirm = true },
            onDismiss = { showHistorySheet = false }
        )
    }

    // Add Checkpoint Dialog
    if (showAddCheckpointDialog) {
        AlertDialog(
            onDismissRequest = { showAddCheckpointDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pin Current Checkpoint")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Add a label or memory note for this location checkpoint:",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = checkpointNoteText,
                        onValueChange = { checkpointNoteText = it },
                        placeholder = { Text("e.g., Bella Italia Cafe, Inya Lake date") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val note = checkpointNoteText.trim()
                        viewModel.recordManualLocationCheckpoint(note)
                        checkpointNoteText = ""
                        showAddCheckpointDialog = false
                    }
                ) {
                    Text("Save Pin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCheckpointDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear History Confirmation Dialog
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear Location History?") },
            text = { Text("This will remove all recorded location checkpoints and trails for this couple group.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLocationHistory()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
