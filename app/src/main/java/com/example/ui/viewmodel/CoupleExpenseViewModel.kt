package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CouplePreferences
import com.example.data.local.UserSession
import com.example.data.location.LocationHelper
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.supabase.SupabaseClientManager
import com.example.data.supabase.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CategorySummary(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val count: Int,
    val percentage: Float
)

data class PartnerSpendingSummary(
    val partnerName: String,
    val amount: Double,
    val percentage: Float
)

data class DashboardSummary(
    val todayTotal: Double = 0.0,
    val monthTotal: Double = 0.0,
    val totalCount: Int = 0,
    val categories: List<CategorySummary> = emptyList(),
    val partnerBreakdown: List<PartnerSpendingSummary> = emptyList()
)

class CoupleExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = CouplePreferences(application)
    val locationHelper = LocationHelper(application)
    val clientManager = SupabaseClientManager()
    val repository = SupabaseRepository(clientManager)

    val userSession: StateFlow<UserSession> = preferences.userSessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSession())

    val expenses: StateFlow<List<Expense>> = repository.expensesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Calculated Dashboard summary
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        expenses,
        userSession
    ) { expenseList, session ->
        calculateSummary(expenseList, session.userName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    init {
        viewModelScope.launch {
            userSession.collect { session ->
                if (session.customSupabaseUrl.isNotBlank() || session.customSupabaseKey.isNotBlank()) {
                    clientManager.updateCredentials(session.customSupabaseUrl, session.customSupabaseKey)
                }
                if (session.isPaired && session.coupleId.isNotBlank()) {
                    repository.initCoupleSession(session.coupleId)
                }
            }
        }
    }

    fun dismissMessage() {
        _userMessage.value = null
    }

    fun showMessage(message: String) {
        _userMessage.value = message
    }

    fun createCouple(userName: String, onResult: (Boolean, String) -> Unit) {
        if (userName.isBlank()) {
            onResult(false, "Please enter your name")
            return
        }

        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.createCouple()
            _isRefreshing.value = false

            result.onSuccess { couple ->
                preferences.saveSession(
                    userName = userName.trim(),
                    coupleId = couple.id,
                    coupleCode = couple.couple_code
                )
                repository.initCoupleSession(couple.id)
                onResult(true, "Couple group created with code: ${couple.couple_code}")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to create couple code")
            }
        }
    }

    fun createCoupleOffline(userName: String, onResult: (Boolean, String) -> Unit) {
        if (userName.isBlank()) {
            onResult(false, "Please enter your name")
            return
        }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { chars.random() }.joinToString("")
        val coupleId = UUID.randomUUID().toString()
        viewModelScope.launch {
            preferences.saveSession(
                userName = userName.trim(),
                coupleId = coupleId,
                coupleCode = code
            )
            repository.initCoupleSession(coupleId)
            onResult(true, "Created offline couple code: $code")
        }
    }

    fun joinCouple(userName: String, coupleCode: String, onResult: (Boolean, String) -> Unit) {
        if (userName.isBlank()) {
            onResult(false, "Please enter your name")
            return
        }
        if (coupleCode.isBlank()) {
            onResult(false, "Please enter the couple code")
            return
        }

        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.joinCouple(coupleCode)
            _isRefreshing.value = false

            result.onSuccess { couple ->
                preferences.saveSession(
                    userName = userName.trim(),
                    coupleId = couple.id,
                    coupleCode = couple.couple_code
                )
                repository.initCoupleSession(couple.id)
                onResult(true, "Successfully linked with couple code: ${couple.couple_code}!")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to link code")
            }
        }
    }

    fun addExpense(
        amount: Double,
        category: ExpenseCategory,
        note: String,
        date: String,
        latitude: Double?,
        longitude: Double?,
        loggedBy: String?,
        onResult: (Boolean) -> Unit
    ) {
        val session = userSession.value
        if (!session.isPaired || session.coupleId.isBlank()) {
            onResult(false)
            return
        }

        val author = if (!loggedBy.isNullOrBlank()) loggedBy else session.userName.ifBlank { "You" }

        val newExpense = Expense(
            id = UUID.randomUUID().toString(),
            couple_id = session.coupleId,
            user_name = author,
            amount = amount,
            category = category.name,
            date = date,
            note = note.trim(),
            latitude = latitude,
            longitude = longitude,
            created_at = SupabaseRepository.getTodayIso()
        )

        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.addExpense(newExpense)
            _isRefreshing.value = false

            result.onSuccess {
                _userMessage.value = "Added ${com.example.util.FormatUtils.formatMmk(amount)} for ${category.title}"
                onResult(true)
            }.onFailure { err ->
                _userMessage.value = "Error: ${err.message}"
                onResult(false)
            }
        }
    }

    fun updateLoveStartDate(date: String) {
        viewModelScope.launch {
            preferences.updateRelationshipStartDate(date)
            _userMessage.value = "Anniversary date updated! 💕"
        }
    }

    fun deleteExpense(expenseId: String) {
        val session = userSession.value
        viewModelScope.launch {
            repository.deleteExpense(expenseId, session.coupleId)
            _userMessage.value = "Expense deleted"
        }
    }

    fun refresh() {
        val session = userSession.value
        if (session.coupleId.isNotBlank()) {
            viewModelScope.launch {
                _isRefreshing.value = true
                repository.refreshExpenses(session.coupleId)
                _isRefreshing.value = false
            }
        }
    }

    fun updateSupabaseCredentials(url: String, key: String) {
        viewModelScope.launch {
            preferences.updateCustomSupabase(url.trim(), key.trim())
            clientManager.updateCredentials(url.trim(), key.trim())
            val session = userSession.value
            if (session.coupleId.isNotBlank()) {
                repository.refreshExpenses(session.coupleId)
            }
            _userMessage.value = "Supabase backend configuration updated"
        }
    }

    fun disconnectCouple() {
        viewModelScope.launch {
            clientManager.stopRealtime()
            preferences.clearSession()
            _userMessage.value = "Disconnected from couple group"
        }
    }

    private fun calculateSummary(list: List<Expense>, myName: String): DashboardSummary {
        val todayStr = SupabaseRepository.getTodayDate()
        val currentMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        var todayTotal = 0.0
        var monthTotal = 0.0

        val categorySums = mutableMapOf<ExpenseCategory, Double>()
        val categoryCounts = mutableMapOf<ExpenseCategory, Int>()
        val partnerSums = mutableMapOf<String, Double>()

        for (exp in list) {
            val amount = exp.amount
            if (exp.date == todayStr) {
                todayTotal += amount
            }
            if (exp.date.startsWith(currentMonthPrefix)) {
                monthTotal += amount
            }

            val cat = ExpenseCategory.fromString(exp.category)
            categorySums[cat] = (categorySums[cat] ?: 0.0) + amount
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1

            val author = exp.user_name.ifBlank { "Unknown" }
            partnerSums[author] = (partnerSums[author] ?: 0.0) + amount
        }

        val allTotal = if (monthTotal > 0.0) monthTotal else list.sumOf { it.amount }
        val categorySummaries = categorySums.map { (cat, sum) ->
            CategorySummary(
                category = cat,
                totalAmount = sum,
                count = categoryCounts[cat] ?: 0,
                percentage = if (allTotal > 0) ((sum / allTotal) * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.totalAmount }

        val partnerSummaries = partnerSums.map { (name, sum) ->
            val total = list.sumOf { it.amount }
            PartnerSpendingSummary(
                partnerName = name,
                amount = sum,
                percentage = if (total > 0) ((sum / total) * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }

        return DashboardSummary(
            todayTotal = todayTotal,
            monthTotal = monthTotal,
            totalCount = list.size,
            categories = categorySummaries,
            partnerBreakdown = partnerSummaries
        )
    }
}
