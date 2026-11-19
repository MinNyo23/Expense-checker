package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Expense
import com.example.data.model.MoneyAccount
import com.example.data.model.MoneyAccountType
import com.example.data.model.OwnerMoneySummary
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import com.example.ui.theme.RomanticAppBackground
import com.example.ui.theme.RomanticLoveBrush
import com.example.ui.theme.VioletLoveBrush
import com.example.ui.viewmodel.CoupleExpenseViewModel
import com.example.util.FormatUtils

private enum class AdjustMode(val label: String) {
    DEPOSIT("+ Deposit"),
    WITHDRAW("- Withdraw"),
    SET_EXACT("Set Exact")
}

@Composable
fun MyMoneyModal(
    viewModel: CoupleExpenseViewModel,
    initialPartnerTab: Boolean = false,
    onDismiss: () -> Unit
) {
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val myAccounts by viewModel.myMoneyAccounts.collectAsStateWithLifecycle()
    val partnerAccounts by viewModel.partnerMoneyAccounts.collectAsStateWithLifecycle()
    val mySummary by viewModel.myMoneySummary.collectAsStateWithLifecycle()
    val partnerSummary by viewModel.partnerMoneySummary.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()

    val myName = userSession.userName.ifBlank { "You" }
    val partnerName = partnerSummary.ownerName.ifBlank { "Girlfriend" }

    var showPartnerTab by remember(initialPartnerTab) { mutableStateOf(initialPartnerTab) }
    var editorInitialType by remember { mutableStateOf<MoneyAccountType?>(null) }
    var editingAccount by remember { mutableStateOf<MoneyAccount?>(null) }
    var adjustingAccount by remember { mutableStateOf<MoneyAccount?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val activeAccounts = if (showPartnerTab) partnerAccounts else myAccounts
    val activeSummary = if (showPartnerTab) partnerSummary else mySummary
    val activeAccent = if (showPartnerTab) PartnerBColor else PartnerAColor

    val cashAccounts = remember(activeAccounts) { activeAccounts.filter { it.typeEnum == MoneyAccountType.CASH } }
    val bankAccounts = remember(activeAccounts) { activeAccounts.filter { it.typeEnum == MoneyAccountType.BANK } }
    val cardAccounts = remember(activeAccounts) { activeAccounts.filter { it.typeEnum == MoneyAccountType.CARD } }

    val activeAccountIds = remember(activeAccounts) { activeAccounts.map { it.id }.toSet() }
    val recentAccountExpenses = remember(expenses, activeAccountIds) {
        expenses.filter { it.moneyAccountId in activeAccountIds }.take(6)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RomanticAppBackground(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = Color(0xF5FFFFFF),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(activeAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = "My Money",
                                        tint = activeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "My Money & Accounts",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Cash • Multi-Bank • Cards (Separate Sync)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.refresh() },
                                    modifier = Modifier.testTag("wallet_sync_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Balances",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_my_money_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close My Money"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Strictly Separated Owner Tabs (Me vs Girlfriend/Partner)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OwnerTabButton(
                                label = "My Money ($myName)",
                                totalText = FormatUtils.formatMmk(mySummary.totalBalance),
                                isSelected = !showPartnerTab,
                                accentColor = PartnerAColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_owner_me"),
                                onClick = { showPartnerTab = false }
                            )
                            OwnerTabButton(
                                label = "$partnerName's Money",
                                totalText = FormatUtils.formatMmk(partnerSummary.totalBalance),
                                isSelected = showPartnerTab,
                                accentColor = PartnerBColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_owner_partner"),
                                onClick = { showPartnerTab = true }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "wallet_spacer_top") {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Hero Separated Balance Summary Card
                    item(key = "wallet_hero_summary") {
                        WalletHeroCard(
                            ownerLabel = if (showPartnerTab) "$partnerName's Real Current Money" else "My Real Current Money ($myName)",
                            summary = activeSummary,
                            isPartner = showPartnerTab
                        )
                    }

                    // Quick Action Strip: + Cash, + Add Bank, + Add Card, Transfer
                    item(key = "wallet_quick_actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WalletActionTile(
                                label = "+ Cash",
                                icon = Icons.Default.Payments,
                                accent = Color(0xFF2E7D32),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_cash_account_button"),
                                onClick = { editorInitialType = MoneyAccountType.CASH }
                            )
                            WalletActionTile(
                                label = "+ Bank",
                                icon = Icons.Default.AccountBalance,
                                accent = Color(0xFF1565C0),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_bank_account_button"),
                                onClick = { editorInitialType = MoneyAccountType.BANK }
                            )
                            WalletActionTile(
                                label = "+ Card",
                                icon = Icons.Default.CreditCard,
                                accent = Color(0xFF7B1FA2),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_card_account_button"),
                                onClick = { editorInitialType = MoneyAccountType.CARD }
                            )
                            WalletActionTile(
                                label = "Move",
                                icon = Icons.AutoMirrored.Filled.CompareArrows,
                                accent = activeAccent,
                                enabled = activeAccounts.size >= 2,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("transfer_money_button"),
                                onClick = { showTransferDialog = true }
                            )
                        }
                    }

                    if (activeAccounts.isEmpty()) {
                        item(key = "wallet_empty_state") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = activeAccent,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (showPartnerTab) {
                                            "No money accounts yet for $partnerName"
                                        } else {
                                            "Set Up Your Separate Cash, Banks & Cards"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Add Cash wallets, multiple Bank accounts, and Cards. Whenever an expense is logged, real current balances update automatically & stay separate between you and $partnerName.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { editorInitialType = MoneyAccountType.BANK },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Bank / Cash / Card", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        // Cash Section
                        if (cashAccounts.isNotEmpty()) {
                            item(key = "header_cash") {
                                SectionHeaderRow(
                                    title = "Cash Wallets (${cashAccounts.size})",
                                    subtotal = FormatUtils.formatMmk(activeSummary.cashBalance),
                                    accent = Color(0xFF2E7D32)
                                )
                            }
                            itemsIndexed(cashAccounts, key = { idx, acc -> "cash_${acc.id}_$idx" }) { _, acc ->
                                MoneyAccountCard(
                                    account = acc,
                                    accentColor = Color(0xFF2E7D32),
                                    onTopUp = { adjustingAccount = acc },
                                    onEdit = { editingAccount = acc },
                                    onDelete = { viewModel.deleteMoneyAccount(acc.id) }
                                )
                            }
                        }

                        // Multi-Bank Section
                        if (bankAccounts.isNotEmpty()) {
                            item(key = "header_banks") {
                                SectionHeaderRow(
                                    title = if (bankAccounts.size > 1) "All Banks Combined (${bankAccounts.size})" else "Bank Account (1)",
                                    subtotal = FormatUtils.formatMmk(activeSummary.bankBalance),
                                    accent = Color(0xFF1565C0)
                                )
                            }
                            itemsIndexed(bankAccounts, key = { idx, acc -> "bank_${acc.id}_$idx" }) { _, acc ->
                                MoneyAccountCard(
                                    account = acc,
                                    accentColor = Color(0xFF1565C0),
                                    onTopUp = { adjustingAccount = acc },
                                    onEdit = { editingAccount = acc },
                                    onDelete = { viewModel.deleteMoneyAccount(acc.id) }
                                )
                            }
                        }

                        // Cards Section
                        if (cardAccounts.isNotEmpty()) {
                            item(key = "header_cards") {
                                SectionHeaderRow(
                                    title = "Cards (${cardAccounts.size})",
                                    subtotal = FormatUtils.formatMmk(activeSummary.cardBalance),
                                    accent = Color(0xFF7B1FA2)
                                )
                            }
                            itemsIndexed(cardAccounts, key = { idx, acc -> "card_${acc.id}_$idx" }) { _, acc ->
                                MoneyAccountCard(
                                    account = acc,
                                    accentColor = Color(0xFF7B1FA2),
                                    onTopUp = { adjustingAccount = acc },
                                    onEdit = { editingAccount = acc },
                                    onDelete = { viewModel.deleteMoneyAccount(acc.id) }
                                )
                            }
                        }
                    }

                    // Recent Live Synced Expense Deductions for this owner
                    if (recentAccountExpenses.isNotEmpty()) {
                        item(key = "wallet_recent_activity_header") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Recent Synced Expense Deductions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        itemsIndexed(recentAccountExpenses, key = { idx, exp -> "act_${exp.id}_$idx" }) { _, exp ->
                            SyncedExpenseRow(exp = exp)
                        }
                    }

                    item(key = "wallet_spacer_bottom") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Add or Edit Account Dialog
    if (editorInitialType != null || editingAccount != null) {
        AccountEditorDialog(
            initialType = editingAccount?.typeEnum ?: editorInitialType ?: MoneyAccountType.BANK,
            existing = editingAccount,
            ownerDisplayName = if (showPartnerTab) partnerName else myName,
            onSave = { type, name, inst, last4, balance, note ->
                val target = editingAccount
                if (target != null) {
                    viewModel.updateMoneyAccount(
                        account = target,
                        accountName = name,
                        institutionName = inst,
                        last4 = last4,
                        newBalance = balance,
                        note = note
                    )
                } else {
                    viewModel.addMoneyAccount(
                        type = type,
                        accountName = name,
                        institutionName = inst,
                        last4 = last4,
                        initialBalance = balance,
                        note = note,
                        forPartner = showPartnerTab
                    )
                }
                editorInitialType = null
                editingAccount = null
            },
            onDismiss = {
                editorInitialType = null
                editingAccount = null
            }
        )
    }

    // Top Up / Withdraw / Exact Balance Adjust Dialog
    adjustingAccount?.let { target ->
        AdjustBalanceDialog(
            account = target,
            onConfirm = { amount, isAbsolute ->
                viewModel.topUpOrAdjustMoneyAccount(target, amount, isAbsolute)
                adjustingAccount = null
            },
            onDismiss = { adjustingAccount = null }
        )
    }

    // Transfer Dialog (Strictly scoped to active owner's accounts)
    if (showTransferDialog && activeAccounts.size >= 2) {
        TransferBetweenAccountsDialog(
            ownerLabel = if (showPartnerTab) partnerName else myName,
            accounts = activeAccounts,
            onTransfer = { fromId, toId, amount ->
                viewModel.transferBetweenAccounts(fromId, toId, amount)
                showTransferDialog = false
            },
            onDismiss = { showTransferDialog = false }
        )
    }
}

@Composable
private fun OwnerTabButton(
    label: String,
    totalText: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = totalText,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WalletHeroCard(
    ownerLabel: String,
    summary: OwnerMoneySummary,
    isPartner: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPartner) VioletLoveBrush else RomanticLoveBrush)
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ownerLabel,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${summary.totalAccounts} accounts • Separate",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Total Combined Money (Cash + All Banks + Cards)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = FormatUtils.formatMmk(summary.totalBalance),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroCategoryChip(
                        title = "Cash (${summary.cashCount})",
                        amount = FormatUtils.formatMmk(summary.cashBalance),
                        modifier = Modifier.weight(1f)
                    )
                    HeroCategoryChip(
                        title = if (summary.bankCount > 1) "All Banks (${summary.bankCount})" else "Banks (${summary.bankCount})",
                        amount = FormatUtils.formatMmk(summary.bankBalance),
                        modifier = Modifier.weight(1f)
                    )
                    HeroCategoryChip(
                        title = "Cards (${summary.cardCount})",
                        amount = FormatUtils.formatMmk(summary.cardBalance),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCategoryChip(
    title: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = amount,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WalletActionTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtotal: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = accent
        )
        Text(
            text = subtotal,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MoneyAccountCard(
    account: MoneyAccount,
    accentColor: Color,
    onTopUp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (account.typeEnum) {
        MoneyAccountType.CASH -> Icons.Default.Payments
        MoneyAccountType.BANK -> Icons.Default.AccountBalance
        MoneyAccountType.CARD -> Icons.Default.CreditCard
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("money_account_card_${account.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = account.typeEnum.label,
                        tint = accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = account.typeEnum.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (account.maskedNumber.isNotBlank()) {
                            Text(
                                text = account.maskedNumber,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (account.note.isNotBlank()) {
                        Text(
                            text = account.note,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatMmk(account.balance),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (account.balance >= 0.0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = account.owner_name,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTopUp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("adjust_account_${account.id}")
                ) {
                    Text("+/- Balance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("edit_account_${account.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Account", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("delete_account_${account.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Account",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedExpenseRow(exp: Expense) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exp.cleanNote.ifBlank { exp.category },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${exp.moneyAccountLabel ?: "Account"} • ${exp.date}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "-${FormatUtils.formatMmk(exp.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
    initialType: MoneyAccountType,
    existing: MoneyAccount?,
    ownerDisplayName: String,
    onSave: (MoneyAccountType, String, String, String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember(existing, initialType) { mutableStateOf(existing?.typeEnum ?: initialType) }
    var institutionName by remember(existing) { mutableStateOf(existing?.institution_name.orEmpty()) }
    var accountName by remember(existing) { mutableStateOf(existing?.account_name.orEmpty()) }
    var last4 by remember(existing) { mutableStateOf(existing?.account_number_last4.orEmpty()) }
    var balanceText by remember(existing) {
        mutableStateOf(
            if (existing != null) existing.balance.toLong().toString() else ""
        )
    }
    var noteText by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val bankPresets = remember { listOf("KBZ Bank", "AYA Bank", "CB Bank", "Yoma Bank", "UAB Bank") }
    val cardPresets = remember { listOf("Visa", "Mastercard", "MPU", "KBZPay Card") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Add $ownerDisplayName's Account" else "Edit ${existing.displayTitle}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MoneyAccountType.entries.forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = {
                                    selectedType = t
                                    errorText = null
                                },
                                label = { Text(t.label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (selectedType == MoneyAccountType.BANK) {
                    Text("Quick Bank Presets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bankPresets.forEach { preset ->
                            FilterChip(
                                selected = institutionName.equals(preset, ignoreCase = true),
                                onClick = { institutionName = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = institutionName,
                        onValueChange = { institutionName = it },
                        label = { Text("Bank Name (e.g. KBZ Bank, AYA Bank)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_institution_input")
                    )
                } else if (selectedType == MoneyAccountType.CARD) {
                    Text("Card Type / Issuer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cardPresets.forEach { preset ->
                            FilterChip(
                                selected = institutionName.equals(preset, ignoreCase = true),
                                onClick = { institutionName = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = institutionName,
                        onValueChange = { institutionName = it },
                        label = { Text("Card Issuer / Network (e.g. Visa, MPU)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_institution_input")
                    )
                }

                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = {
                        Text(
                            when (selectedType) {
                                MoneyAccountType.CASH -> "Cash Wallet Name (e.g. Main Cash)"
                                MoneyAccountType.BANK -> "Account Label (e.g. Salary, Savings)"
                                MoneyAccountType.CARD -> "Card Name (e.g. Shopping Visa)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_name_input")
                )

                if (selectedType != MoneyAccountType.CASH) {
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { input ->
                            last4 = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Account / Card Last 4 Digits (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_last4_input")
                    )
                }

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { input ->
                        val cleaned = input.replace(",", "").replace(" ", "")
                        if (cleaned.matches(Regex("""^-?\d*(\.\d{0,2})?$"""))) {
                            balanceText = cleaned
                            errorText = null
                        }
                    },
                    label = { Text("Current Real Money Balance (MMK)") },
                    supportingText = {
                        val parsed = balanceText.toDoubleOrNull()
                        if (parsed != null) {
                            Text("Will add to combined total: ${FormatUtils.formatMmk(parsed)}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_balance_input")
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorText?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanBalance = balanceText.replace(",", "").trim()
                    val balance = if (cleanBalance.isEmpty()) 0.0 else cleanBalance.toDoubleOrNull()
                    if (accountName.isBlank() && institutionName.isBlank()) {
                        errorText = "Please enter an account or bank name"
                        return@Button
                    }
                    if (balance == null) {
                        errorText = "Please enter a valid balance amount"
                        return@Button
                    }
                    onSave(selectedType, accountName, institutionName, last4, balance, noteText)
                },
                modifier = Modifier.testTag("save_money_account_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AdjustBalanceDialog(
    account: MoneyAccount,
    onConfirm: (Double, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(AdjustMode.DEPOSIT) }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update ${account.displayTitle}", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Balance: ${FormatUtils.formatMmk(account.balance)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdjustMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val cleaned = input.replace(",", "").replace(" ", "")
                        if (cleaned.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                            amountText = cleaned
                        }
                    },
                    label = {
                        Text(
                            when (mode) {
                                AdjustMode.DEPOSIT -> "Amount to Deposit (MMK)"
                                AdjustMode.WITHDRAW -> "Amount to Withdraw (MMK)"
                                AdjustMode.SET_EXACT -> "New Exact Balance (MMK)"
                            }
                        )
                    },
                    supportingText = {
                        val parsed = amountText.toDoubleOrNull()
                        if (parsed != null) {
                            val projected = when (mode) {
                                AdjustMode.DEPOSIT -> account.balance + parsed
                                AdjustMode.WITHDRAW -> account.balance - parsed
                                AdjustMode.SET_EXACT -> parsed
                            }
                            Text("Updated balance: ${FormatUtils.formatMmk(projected)}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_balance_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = amountText.replace(",", "").trim().toDoubleOrNull() ?: return@Button
                    when (mode) {
                        AdjustMode.DEPOSIT -> onConfirm(value, false)
                        AdjustMode.WITHDRAW -> onConfirm(-value, false)
                        AdjustMode.SET_EXACT -> onConfirm(value, true)
                    }
                },
                modifier = Modifier.testTag("confirm_adjust_balance_button")
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TransferBetweenAccountsDialog(
    ownerLabel: String,
    accounts: List<MoneyAccount>,
    onTransfer: (String, String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var fromId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var toId by remember(accounts) { mutableStateOf(accounts.getOrNull(1)?.id.orEmpty()) }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Move $ownerLabel's Money", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("From Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = fromId == acc.id,
                            onClick = {
                                fromId = acc.id
                                if (toId == acc.id) {
                                    toId = accounts.firstOrNull { it.id != acc.id }?.id.orEmpty()
                                }
                            },
                            label = { Text("${acc.shortBadgeLabel} • ${FormatUtils.formatMmk(acc.balance)}", fontSize = 11.sp) }
                        )
                    }
                }

                Text("To Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.filter { it.id != fromId }.forEach { acc ->
                        FilterChip(
                            selected = toId == acc.id,
                            onClick = { toId = acc.id },
                            label = { Text("${acc.shortBadgeLabel} • ${FormatUtils.formatMmk(acc.balance)}", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                            amountText = input
                        }
                    },
                    label = { Text("Transfer Amount (MMK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    if (amount > 0.0 && fromId.isNotBlank() && toId.isNotBlank() && fromId != toId) {
                        onTransfer(fromId, toId, amount)
                    }
                },
                modifier = Modifier.testTag("confirm_transfer_button")
            ) {
                Text("Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
