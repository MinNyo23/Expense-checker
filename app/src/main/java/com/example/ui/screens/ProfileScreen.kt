package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.components.ForgotPasswordOtpDialog
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import com.example.ui.theme.RomanticLoveBrush
import com.example.ui.viewmodel.CoupleExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: CoupleExpenseViewModel,
    onNavigateBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val partnerProfile by viewModel.partnerProfile.collectAsStateWithLifecycle()
    val deviceSessions by viewModel.deviceSessions.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var partnerLinkCodeInput by remember { mutableStateOf("") }
    var linkFeedback by remember { mutableStateOf<String?>(null) }
    var isLinkSuccess by remember { mutableStateOf(false) }

    var nameInput by remember { mutableStateOf(userSession.userName) }
    var emailInput by remember { mutableStateOf(userSession.userEmail) }
    var dobInput by remember { mutableStateOf(userSession.dateOfBirth) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    // Sync input fields when myProfile or session changes
    LaunchedEffect(myProfile, userSession) {
        if (nameInput.isBlank()) {
            nameInput = myProfile?.user_name?.ifBlank { userSession.userName } ?: userSession.userName
        }
        if (emailInput.isBlank()) {
            emailInput = myProfile?.email?.ifBlank { userSession.userEmail } ?: userSession.userEmail
        }
        if (dobInput.isBlank()) {
            dobInput = myProfile?.date_of_birth?.ifBlank { userSession.dateOfBirth } ?: userSession.dateOfBirth
        }
    }

    // Security PIN State
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isPinSuccess by remember { mutableStateOf(false) }

    // Account Password State (Supabase Auth)
    var newAccountPassword by remember { mutableStateOf("") }
    var confirmAccountPassword by remember { mutableStateOf("") }
    var showAccountPasswords by remember { mutableStateOf(false) }
    var accountPasswordFeedback by remember { mutableStateOf<String?>(null) }
    var isAccountPasswordSuccess by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val signedMemoUrls by viewModel.signedMemoUrls.collectAsStateWithLifecycle()

    // Photo picker launcher (Android Photo Picker - zero permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    val openDobPicker = remember(context, dobInput) {
        {
            runCatching {
                val calendar = Calendar.getInstance()
                if (dobInput.isNotBlank()) {
                    try {
                        val parts = dobInput.split("-")
                        if (parts.size == 3) {
                            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        }
                    } catch (_: Exception) {}
                }
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        dobInput = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            Unit
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Couple Profile & Sync",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xF5FFFFFF)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "profile_header", contentType = "header") {
                Spacer(modifier = Modifier.height(4.dp))
                val isPrivatePath = remember {
                    { path: String ->
                        val p = path.trim()
                        p.isNotBlank() &&
                            !p.startsWith("http://") &&
                            !p.startsWith("https://") &&
                            !p.startsWith("data:") &&
                            !p.startsWith("content:") &&
                            !p.startsWith("file:") &&
                            !p.startsWith("/")
                    }
                }
                val rawMyAvatar = myProfile?.avatar_url?.ifBlank { userSession.avatarPath } ?: userSession.avatarPath
                val rawPartnerAvatar = partnerProfile?.avatar_url?.ifBlank { userSession.partnerAvatarUrl } ?: userSession.partnerAvatarUrl
                if (selectedAvatarUri == null && isPrivatePath(rawMyAvatar)) {
                    LaunchedEffect(rawMyAvatar) { viewModel.resolveMemoImageUrl(rawMyAvatar) }
                }
                if (isPrivatePath(rawPartnerAvatar)) {
                    LaunchedEffect(rawPartnerAvatar) { viewModel.resolveMemoImageUrl(rawPartnerAvatar) }
                }
                val resolvedMyAvatar: Any? = selectedAvatarUri ?: if (isPrivatePath(rawMyAvatar)) signedMemoUrls[rawMyAvatar] else rawMyAvatar
                val resolvedPartnerAvatar: Any? = if (isPrivatePath(rawPartnerAvatar)) signedMemoUrls[rawPartnerAvatar] else rawPartnerAvatar

                // Dual Couple Synchronized Header Card
                DualCoupleHeaderCard(
                    userSessionName = userSession.userName,
                    userAvatar = resolvedMyAvatar,
                    partnerName = partnerProfile?.user_name?.ifBlank { userSession.partnerName } ?: userSession.partnerName,
                    partnerAvatar = resolvedPartnerAvatar,
                    coupleCode = userSession.coupleCode
                )
            }

            item(key = "profile_link_sync", contentType = "card") {
                // Section 0: Unique 6-Digit Account Linking & Supabase Sync Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "6-Digit Account Link & Sync",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (syncStatus.is_partner_linked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = if (syncStatus.is_partner_linked) "2/2 Synced" else "1/2 Waiting",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (syncStatus.is_partner_linked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Active 6-digit code showcase
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Your Unique 6-Digit Link Code",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = userSession.coupleCode.ifBlank { "------" },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (syncStatus.last_synced_at.isNotBlank()) {
                                    Text(
                                        text = "Last Supabase sync: ${syncStatus.last_synced_at}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(ClipData.newPlainText("6-Digit Link Code", userSession.coupleCode))
                                            Toast.makeText(context, "Copied 6-digit code: ${userSession.coupleCode}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Link your Couple Expense account with mine using this 6-digit code: ${userSession.coupleCode}"
                                                )
                                                type = "text/plain"
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(sendIntent, "Share 6-Digit Link Code"))
                                            }.onFailure {
                                                Toast.makeText(context, "No sharing app found on this device", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.generateUniqueSixDigitLinkCode { ok, msg ->
                                                linkFeedback = if (ok) "Generated unique 6-digit code: $msg" else msg
                                                isLinkSuccess = ok
                                            }
                                        },
                                        enabled = !isSyncing,
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("generate_link_code_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Enter partner's 6-digit code to link & merge data
                        Text(
                            text = "Have your partner's 6-digit code? Enter it to link & sync both accounts:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = partnerLinkCodeInput,
                                onValueChange = {
                                    if (it.length <= 6) partnerLinkCodeInput = it.uppercase()
                                    linkFeedback = null
                                },
                                placeholder = { Text("6-digit code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    capitalization = KeyboardCapitalization.Characters
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("link_partner_code_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.linkPartnerWithSixDigitCode(partnerLinkCodeInput) { ok, msg ->
                                        linkFeedback = msg
                                        isLinkSuccess = ok
                                        if (ok) partnerLinkCodeInput = ""
                                    }
                                },
                                enabled = !isSyncing && partnerLinkCodeInput.length >= 4,
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("link_partner_submit_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Link", fontWeight = FontWeight.Bold)
                            }
                        }

                        linkFeedback?.let { msg ->
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLinkSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.syncNow() },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_now_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSyncing) "Synchronizing with Supabase..." else "Sync All Shared Data Now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item(key = "profile_my_details", contentType = "form_card") {
                // Section 1: My Profile Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "My Profile (Synced with Partner)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Updates to photo, name, and birthday sync automatically to your partner's phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Avatar Picker with Camera Badge
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .testTag("profile_avatar_picker")
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val activeAvatarSource = selectedAvatarUri ?: myProfile?.avatar_url?.ifBlank { userSession.avatarPath } ?: userSession.avatarPath

                            if (activeAvatarSource.toString().isNotBlank()) {
                                AsyncImage(
                                    model = activeAvatarSource,
                                    contentDescription = "My Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(PartnerAColor)
                                        .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (nameInput.ifBlank { userSession.userName }).take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Camera Edit Badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(34.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change Photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Tap to choose photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Full Name
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Your Display Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Email
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            placeholder = { Text("e.g. minnyo.work@gmail.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Date of Birth
                        OutlinedTextField(
                            value = dobInput,
                            onValueChange = { dobInput = it },
                            label = { Text("Date of Birth (YYYY-MM-DD)") },
                            leadingIcon = {
                                Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = openDobPicker) {
                                    Icon(Icons.Default.Cake, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDobPicker() }
                                .testTag("profile_dob_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Birthday & Age helper info
                        val ageInfo = remember(dobInput) {
                            if (dobInput.isNotBlank()) calculateAgeAndCountdown(dobInput) else null
                        }
                        if (ageInfo != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "🎂 $ageInfo",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Save Button
                        Button(
                            onClick = {
                                viewModel.updateProfile(
                                    name = nameInput,
                                    email = emailInput,
                                    dob = dobInput,
                                    avatarUri = selectedAvatarUri
                                ) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_profile_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save & Sync to Partner",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            item(key = "profile_partner_live", contentType = "card") {
                // Section 2: Partner's Live Synchronized Profile Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Partner's Live Profile",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Live Synchronized",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val partnerAvatarUrl = partnerProfile?.avatar_url?.ifBlank { userSession.partnerAvatarUrl } ?: userSession.partnerAvatarUrl
                        val partnerName = partnerProfile?.user_name?.ifBlank { userSession.partnerName } ?: userSession.partnerName
                        val partnerEmail = partnerProfile?.email?.ifBlank { userSession.partnerEmail } ?: userSession.partnerEmail
                        val partnerDob = partnerProfile?.date_of_birth?.ifBlank { userSession.partnerDob } ?: userSession.partnerDob

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (partnerAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = partnerAvatarUrl,
                                    contentDescription = "Partner Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(2.5.dp, PartnerBColor, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(PartnerBColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (partnerName.isNotBlank()) partnerName.take(1).uppercase() else "P",
                                        color = Color.White,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = if (partnerName.isNotBlank()) partnerName else "Your Partner",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                if (partnerEmail.isNotBlank()) {
                                    Text(
                                        text = "✉️ $partnerEmail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val partnerAgeInfo = remember(partnerDob) {
                                    if (partnerDob.isNotBlank()) calculateAgeAndCountdown(partnerDob) else null
                                }
                                if (partnerDob.isNotBlank()) {
                                    Text(
                                        text = "🎂 DOB: $partnerDob (${partnerAgeInfo ?: ""})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Birthday not shared yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "profile_security_pin", contentType = "form_card") {
                // Section 3: App Security PIN & Password Change
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Security PIN / Password",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Set a 4-digit PIN shield to keep your couple's finances and private memories safe on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (userSession.isPinSet) {
                            OutlinedTextField(
                                value = oldPinInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) oldPinInput = it },
                                label = { Text("Current 4-Digit PIN") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_old_pin_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                            label = { Text(if (userSession.isPinSet) "New 4-Digit PIN" else "Set 4-Digit PIN") },
                            leadingIcon = {
                                Icon(Icons.Default.LockReset, contentDescription = null)
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_new_pin_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                            label = { Text("Confirm 4-Digit PIN") },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_confirm_pin_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        pinFeedbackMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isPinSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.changePasswordOrPin(
                                        oldPin = oldPinInput,
                                        newPin = newPinInput,
                                        confirmPin = confirmPinInput
                                    ) { success, msg ->
                                        pinFeedbackMessage = msg
                                        isPinSuccess = success
                                        if (success) {
                                            oldPinInput = ""
                                            newPinInput = ""
                                            confirmPinInput = ""
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("change_pin_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (userSession.isPinSet) "Update PIN" else "Set Security PIN",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (userSession.isPinSet) {
                                OutlinedButton(
                                    onClick = {
                                        if (oldPinInput == userSession.appPin) {
                                            viewModel.updateAppPin("")
                                            pinFeedbackMessage = "PIN lock disabled"
                                            isPinSuccess = true
                                            oldPinInput = ""
                                        } else {
                                            pinFeedbackMessage = "Enter correct current PIN to disable"
                                            isPinSuccess = false
                                        }
                                    },
                                    modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Turn Off")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "profile_account_password", contentType = "form_card") {
                // Section 3B: Supabase Auth Account Login Password & Email Reset
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LockReset,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Supabase Auth Account Password",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Managed securely by Supabase Auth. Update your sign-in password or request a password reset email anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = newAccountPassword,
                            onValueChange = { newAccountPassword = it; accountPasswordFeedback = null },
                            label = { Text("New Password (min 6 chars)") },
                            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showAccountPasswords = !showAccountPasswords }) {
                                    Icon(
                                        imageVector = if (showAccountPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showAccountPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_new_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmAccountPassword,
                            onValueChange = { confirmAccountPassword = it; accountPasswordFeedback = null },
                            label = { Text("Confirm New Password") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            visualTransformation = if (showAccountPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_confirm_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        accountPasswordFeedback?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isAccountPasswordSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.updateAuthPassword(
                                    newPassword = newAccountPassword,
                                    confirmPassword = confirmAccountPassword
                                ) { success, msg ->
                                    accountPasswordFeedback = msg
                                    isAccountPasswordSuccess = success
                                    if (success) {
                                        newAccountPassword = ""
                                        confirmAccountPassword = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("change_account_password_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Update Password", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("profile_forgot_password_button")
                        ) {
                            Text(
                                text = "Forgot Password? Send Reset Email",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item(key = "profile_saved_devices", contentType = "card") {
                // Section 4: Logged-In Couple Devices (Backend Saved for Auto-Login)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved Couple Devices",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Auto-Login On",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Devices saved in backend will log in automatically whenever the app opens — no need to enter your couple code again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val devicesToShow = if (deviceSessions.isNotEmpty()) {
                            deviceSessions
                        } else {
                            listOf(
                                com.example.data.model.DeviceSession(
                                    device_id = viewModel.currentDeviceId,
                                    couple_id = userSession.coupleId,
                                    couple_code = userSession.coupleCode,
                                    user_name = userSession.userName.ifBlank { "You" },
                                    email = userSession.userEmail,
                                    device_name = viewModel.currentDeviceName,
                                    is_active = true
                                )
                            )
                        }

                        devicesToShow.forEach { device ->
                            val isCurrentDevice = device.device_id == viewModel.currentDeviceId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isCurrentDevice) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCurrentDevice) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else PartnerBColor.copy(alpha = 0.15f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                tint = if (isCurrentDevice) MaterialTheme.colorScheme.primary else PartnerBColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = device.device_name.ifBlank { "Android Device" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isCurrentDevice) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Text(
                                                            text = "This Phone",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "Account: ${device.user_name}" + if (device.email.isNotBlank()) " (${device.email})" else "",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.revokeDeviceSession(device.device_id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Device",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "profile_logout_section", contentType = "card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Account Session",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Signed in as ${userSession.userName.ifBlank { "You" }} • Couple Code ${userSession.coupleCode.ifBlank { "—" }}. Logging out keeps all money balances, expenses, and couple data safely stored for when you sign back in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showLogoutConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Log Out",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Log Out",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            item(key = "profile_footer_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Log Out of Account?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You will return to the Sign In screen. All your My Money balances, expenses, memos, and account status stay saved and will automatically restore when you log back in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordOtpDialog(
            viewModel = viewModel,
            initialEmail = emailInput.ifBlank { userSession.userEmail },
            onDismiss = { showForgotPasswordDialog = false },
            onPasswordResetSuccess = {
                showForgotPasswordDialog = false
                accountPasswordFeedback = "Password reset email sent! Check your inbox."
                isAccountPasswordSuccess = true
                Toast.makeText(context, "Password reset email sent!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
fun DualCoupleHeaderCard(
    userSessionName: String,
    userAvatar: Any?,
    partnerName: String,
    partnerAvatar: Any?,
    coupleCode: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dual Connected Avatars Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User Avatar Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(3.dp, PartnerAColor, CircleShape)
                                .background(PartnerAColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userAvatar != null && userAvatar.toString().isNotBlank()) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = "User Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = userSessionName.take(1).uppercase().ifBlank { "Y" },
                                    color = PartnerAColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = userSessionName.ifBlank { "You" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You",
                            style = MaterialTheme.typography.labelSmall,
                            color = PartnerAColor
                        )
                    }

                    // Heart Connector in middle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Love Connected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Partner Avatar Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(3.dp, PartnerBColor, CircleShape)
                                .background(PartnerBColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (partnerAvatar != null && partnerAvatar.toString().isNotBlank()) {
                                AsyncImage(
                                    model = partnerAvatar,
                                    contentDescription = "Partner Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = if (partnerName.isNotBlank()) partnerName.take(1).uppercase() else "P",
                                    color = PartnerBColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (partnerName.isNotBlank()) partnerName else "Partner",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Partner 💕",
                            style = MaterialTheme.typography.labelSmall,
                            color = PartnerBColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Couple Code: $coupleCode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Calculates current age and days until next birthday from a "yyyy-MM-dd" date string.
 */
private fun calculateAgeAndCountdown(dobStr: String): String? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val birthDate = sdf.parse(dobStr) ?: return null

        val birthCal = Calendar.getInstance().apply { time = birthDate }
        val nowCal = Calendar.getInstance()

        var age = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (nowCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }

        // Next birthday
        val nextBirthdayCal = Calendar.getInstance().apply {
            set(Calendar.MONTH, birthCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, birthCal.get(Calendar.DAY_OF_MONTH))
        }
        if (nextBirthdayCal.before(nowCal)) {
            nextBirthdayCal.add(Calendar.YEAR, 1)
        }

        val diffMillis = nextBirthdayCal.timeInMillis - nowCal.timeInMillis
        val daysUntil = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        val countdownStr = when (daysUntil) {
            0 -> "Birthday is Today! 🎉🥳"
            1 -> "Birthday is Tomorrow! 🎁"
            else -> "$daysUntil days until next birthday"
        }

        "$age years old • $countdownStr"
    } catch (_: Exception) {
        null
    }
}
