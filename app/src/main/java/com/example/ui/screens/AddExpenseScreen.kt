package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseCategory
import com.example.data.supabase.SupabaseRepository
import com.example.ui.components.CategoryChip
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import com.example.ui.viewmodel.CoupleExpenseViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    viewModel: CoupleExpenseViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSession by viewModel.userSession.collectAsState()

    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var selectedDate by remember { mutableStateOf(SupabaseRepository.getTodayDate()) }
    var loggedByName by remember { mutableStateOf(userSession.userName.ifBlank { "You" }) }

    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Location Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch {
                isFetchingLocation = true
                val coords = viewModel.locationHelper.getCurrentCoordinates()
                if (coords != null) {
                    latitude = coords.first
                    longitude = coords.second
                }
                isFetchingLocation = false
            }
        } else {
            Toast.makeText(context, "Location permission denied. Expense will be saved without GPS.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-fetch location on entry
    LaunchedEffect(Unit) {
        if (viewModel.locationHelper.hasLocationPermission()) {
            isFetchingLocation = true
            val coords = viewModel.locationHelper.getCurrentCoordinates()
            if (coords != null) {
                latitude = coords.first
                longitude = coords.second
            }
            isFetchingLocation = false
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Date Picker Dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Couple Expense", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Amount Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Amount",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            // Allow valid decimal numbers
                            if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                amountText = input
                            }
                        },
                        placeholder = { Text("0", fontSize = 26.sp) },
                        leadingIcon = {
                            Text(
                                "MMK",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Quick Chips for MMK
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5000.0, 10000.0, 50000.0, 100000.0).forEach { preset ->
                            val label = when (preset) {
                                5000.0 -> "+5K"
                                10000.0 -> "+10K"
                                50000.0 -> "+50K"
                                100000.0 -> "+100K"
                                else -> "+${preset.toInt()}"
                            }
                            SuggestionChip(
                                onClick = {
                                    val current = amountText.toDoubleOrNull() ?: 0.0
                                    amountText = (current + preset).toLong().toString()
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Category Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Category",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpenseCategory.entries.forEach { category ->
                            CategoryChip(
                                category = category,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }

            // Note & Date & Payer
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Who paid toggle
                    Text(
                        text = "Who Paid?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isUserLogged = loggedByName.equals(userSession.userName, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { loggedByName = userSession.userName.ifBlank { "You" } },
                            color = if (isUserLogged) PartnerAColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isUserLogged) PartnerAColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "You (${userSession.userName.ifBlank { "Me" }})",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserLogged) PartnerAColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { loggedByName = "Partner" },
                            color = if (!isUserLogged) PartnerBColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (!isUserLogged) PartnerBColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Partner",
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isUserLogged) PartnerBColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date Picker Trigger
                    Text(
                        text = "Date",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                            .testTag("date_picker_field"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Note
                    Text(
                        text = "Note / Description",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("e.g. Dinner, Movie tickets, Groceries...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Automatic Location Tagging Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "GPS Location Tag",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (isFetchingLocation) {
                                Text(
                                    text = "Acquiring GPS coordinates...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (latitude != null && longitude != null) {
                                Text(
                                    text = String.format(Locale.US, "📍 %.4f, %.4f", latitude, longitude),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "No GPS fix (tap to refresh)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!viewModel.locationHelper.hasLocationPermission()) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                coroutineScope.launch {
                                    isFetchingLocation = true
                                    val coords = viewModel.locationHelper.getCurrentCoordinates()
                                    if (coords != null) {
                                        latitude = coords.first
                                        longitude = coords.second
                                        Toast.makeText(context, "Location updated!", Toast.LENGTH_SHORT).show()
                                    }
                                    isFetchingLocation = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("refresh_gps_button")
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Refresh GPS",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true
                    viewModel.addExpense(
                        amount = amount,
                        category = selectedCategory,
                        note = noteText,
                        date = selectedDate,
                        latitude = latitude,
                        longitude = longitude,
                        loggedBy = loggedByName
                    ) { success ->
                        isSaving = false
                        if (success) {
                            Toast.makeText(context, "Saved expense successfully!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            Toast.makeText(context, "Failed to save expense", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_expense_button"),
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
