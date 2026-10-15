package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseRepository
import com.example.ui.viewmodel.CoupleExpenseViewModel

@Composable
fun SettingsDialog(
    viewModel: CoupleExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userSession by viewModel.userSession.collectAsState()

    var supabaseUrl by remember(userSession) {
        mutableStateOf(
            if (userSession.customSupabaseUrl.isNotBlank()) userSession.customSupabaseUrl
            else viewModel.clientManager.getSupabaseUrl()
        )
    }
    var supabaseKey by remember(userSession) {
        mutableStateOf(
            if (userSession.customSupabaseKey.isNotBlank()) userSession.customSupabaseKey
            else viewModel.clientManager.getSupabaseKey()
        )
    }

    var showSqlSchema by remember { mutableStateOf(false) }
    val isLiveConfigured = viewModel.clientManager.isConfigured()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Supabase & Couple Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Connection Status Pill
                Surface(
                    color = if (isLiveConfigured) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isLiveConfigured) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isLiveConfigured) "Connected to Live Supabase" else "Local / Demo Sync Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isLiveConfigured) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isLiveConfigured) "Realtime WebSocket active" else "Couple code '${userSession.coupleCode}' ready",
                                fontSize = 11.sp,
                                color = if (isLiveConfigured) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Couple Code Display with copy
                if (userSession.coupleCode.isNotBlank()) {
                    Column {
                        Text("Active Couple Code", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userSession.coupleCode,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Couple Code", userSession.coupleCode))
                                    Toast.makeText(context, "Copied Couple Code!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Supabase URL Input
                Column {
                    Text("Supabase Project URL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it },
                        placeholder = { Text("https://xyzcompany.supabase.co") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Supabase Anon Key Input
                Column {
                    Text("Supabase Anon Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = supabaseKey,
                        onValueChange = { supabaseKey = it },
                        placeholder = { Text("eyJhbGciOi...") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Save credentials button
                Button(
                    onClick = {
                        viewModel.updateSupabaseCredentials(supabaseUrl, supabaseKey)
                        Toast.makeText(context, "Supabase credentials updated!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Connect")
                }

                // Toggle Supabase SQL Schema snippet
                OutlinedButton(
                    onClick = { showSqlSchema = !showSqlSchema },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (showSqlSchema) "Hide SQL Setup Script" else "View Supabase SQL Schema")
                }

                if (showSqlSchema) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E24), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SQL Schema Script", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Supabase SQL", SupabaseRepository.SUPABASE_SQL_SETUP_SCRIPT))
                                    Toast.makeText(context, "SQL copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy SQL", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = SupabaseRepository.SUPABASE_SQL_SETUP_SCRIPT,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFCE9178),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }

                // Disconnect session
                if (userSession.isPaired) {
                    TextButton(
                        onClick = {
                            viewModel.disconnectCouple()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnect / Unpair Couple", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
