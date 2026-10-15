package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.CoupleExpenseViewModel

@Composable
fun PairingScreen(
    viewModel: CoupleExpenseViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userName by remember { mutableStateOf("") }
    var coupleCodeInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Create, 1 = Join
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row with settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Supabase Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hero Illustration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.couple_hero_banner_1792056341418),
                    contentDescription = "Couple budgeting together",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Title & Description
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Couple Expense",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = "Track daily shared spending, sync in real time, and see expenses on a live map.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Name input card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Who is using this device?",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    placeholder = { Text("e.g. Alex, Emma, Sarah...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Tabs: Create vs Join
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Create Code", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Join Code", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.AddLink, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content based on tab
        if (selectedTab == 0) {
            // Create Couple Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Start a new shared budget for your couple",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedVisibility(visible = generatedCode != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Your Couple Code",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = generatedCode ?: "",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Couple Code", generatedCode))
                                    Toast.makeText(context, "Copied code to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code for Partner")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (userName.isBlank()) {
                                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.createCouple(userName) { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    // Successfully coupled
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_code_button"),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.GroupAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Couple Code", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Join with Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter the 6-character code your partner shared with you:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = coupleCodeInput,
                        onValueChange = {
                            if (it.length <= 8) coupleCodeInput = it.uppercase()
                        },
                        placeholder = { Text("e.g. LOVE24") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (userName.isBlank()) {
                                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (coupleCodeInput.isBlank()) {
                                Toast.makeText(context, "Please enter the couple code", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.joinCouple(userName, coupleCodeInput) { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("join_couple_button"),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.AddLink, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join & Link Devices", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Demo quick fill option
        OutlinedButton(
            onClick = {
                userName = "Emma"
                coupleCodeInput = "LOVE24"
                viewModel.joinCouple("Emma", "LOVE24") { success, msg ->
                    Toast.makeText(context, "Joined Demo Couple LOVE24!", Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("demo_mode_button")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Quick Preview Demo Couple (LOVE24)", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
