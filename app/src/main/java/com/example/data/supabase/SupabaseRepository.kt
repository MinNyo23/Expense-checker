package com.example.data.supabase

import com.example.data.model.Couple
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupabaseRepository(
    private val clientManager: SupabaseClientManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    val expensesFlow: Flow<List<Expense>> = _expensesFlow.asStateFlow()

    private var activeCoupleId: String = ""

    // In-memory demo data store when Supabase credentials are not yet configured
    private val demoCouples = mutableMapOf<String, Couple>(
        "LOVE24" to Couple(id = "demo-couple-uuid-1", couple_code = "LOVE24", created_at = "2026-10-15T00:00:00Z")
    )
    private val demoExpenses = mutableListOf(
        Expense(
            id = "demo-exp-1",
            couple_id = "demo-couple-uuid-1",
            user_name = "Alex",
            amount = 45000.0,
            category = ExpenseCategory.DATE_NIGHT.name,
            date = getTodayDate(),
            note = "Romantic dinner at Bella Italia 🍕",
            latitude = 37.7749,
            longitude = -122.4194,
            created_at = "2026-10-15T01:10:00Z"
        ),
        Expense(
            id = "demo-exp-2",
            couple_id = "demo-couple-uuid-1",
            user_name = "Sam",
            amount = 72000.0,
            category = ExpenseCategory.GROCERIES.name,
            date = getTodayDate(),
            note = "Weekly groceries at Market",
            latitude = 37.7833,
            longitude = -122.4167,
            created_at = "2026-10-15T01:30:00Z"
        ),
        Expense(
            id = "demo-exp-3",
            couple_id = "demo-couple-uuid-1",
            user_name = "Alex",
            amount = 12000.0,
            category = ExpenseCategory.COFFEE.name,
            date = getTodayDate(),
            note = "Morning iced lattes & croissant",
            latitude = 37.7690,
            longitude = -122.4467,
            created_at = "2026-10-15T02:00:00Z"
        ),
        Expense(
            id = "demo-exp-4",
            couple_id = "demo-couple-uuid-1",
            user_name = "Sam",
            amount = 28000.0,
            category = ExpenseCategory.TRANSPORT.name,
            date = getTodayDate(),
            note = "Taxi ride to evening concert",
            latitude = 37.7900,
            longitude = -122.4000,
            created_at = "2026-10-15T02:15:00Z"
        )
    )

    init {
        // Listen to Realtime WebSocket and poll events to refresh data automatically
        clientManager.realtimeExpenseEvents.onEach {
            if (activeCoupleId.isNotBlank()) {
                refreshExpenses(activeCoupleId)
            }
        }.launchIn(scope)
    }

    fun initCoupleSession(coupleId: String) {
        activeCoupleId = coupleId
        clientManager.startRealtime(coupleId)
        scope.launch {
            refreshExpenses(coupleId)
        }
    }

    suspend fun refreshExpenses(coupleId: String) {
        if (clientManager.isConfigured()) {
            val result = clientManager.getExpenses(coupleId)
            result.onSuccess { list ->
                _expensesFlow.value = list
            }.onFailure {
                // Keep current or fallback
            }
        } else {
            // Local/Demo Mode
            val filtered = demoExpenses.filter { it.couple_id == coupleId }
            _expensesFlow.value = filtered.sortedByDescending { it.created_at }
        }
    }

    suspend fun createCouple(codeOverride: String? = null): Result<Couple> {
        val code = codeOverride ?: generateCoupleCode()
        return if (clientManager.isConfigured()) {
            clientManager.createCouple(code)
        } else {
            val couple = Couple(
                id = UUID.randomUUID().toString(),
                couple_code = code,
                created_at = getTodayIso()
            )
            demoCouples[code] = couple
            Result.success(couple)
        }
    }

    suspend fun joinCouple(code: String): Result<Couple> {
        val cleanCode = code.trim().uppercase()
        return if (clientManager.isConfigured()) {
            val res = clientManager.getCoupleByCode(cleanCode)
            res.mapCatching { couple ->
                couple ?: throw NoSuchElementException("Couple code '$cleanCode' not found. Please double-check with your partner.")
            }
        } else {
            val found = demoCouples[cleanCode]
                ?: if (cleanCode == "LOVE24") demoCouples["LOVE24"] else null

            if (found != null) {
                Result.success(found)
            } else {
                // In demo mode, if code doesn't exist, create it dynamically so the user is never blocked!
                val fallbackCouple = Couple(
                    id = UUID.randomUUID().toString(),
                    couple_code = cleanCode,
                    created_at = getTodayIso()
                )
                demoCouples[cleanCode] = fallbackCouple
                Result.success(fallbackCouple)
            }
        }
    }

    suspend fun addExpense(expense: Expense): Result<Expense> {
        return if (clientManager.isConfigured()) {
            val result = clientManager.insertExpense(expense)
            result.onSuccess {
                refreshExpenses(expense.couple_id)
            }
            result
        } else {
            demoExpenses.add(0, expense)
            refreshExpenses(expense.couple_id)
            Result.success(expense)
        }
    }

    suspend fun deleteExpense(expenseId: String, coupleId: String): Result<Boolean> {
        return if (clientManager.isConfigured()) {
            val res = clientManager.deleteExpense(expenseId)
            res.onSuccess {
                refreshExpenses(coupleId)
            }
            res
        } else {
            demoExpenses.removeAll { it.id == expenseId }
            refreshExpenses(coupleId)
            Result.success(true)
        }
    }

    private fun generateCoupleCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    companion object {
        fun getTodayDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getTodayIso(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            return sdf.format(Date())
        }

        const val SUPABASE_SQL_SETUP_SCRIPT = """-- ============================================================
-- Supabase Schema for Couple Daily Expense Checker
-- ============================================================

-- 1. Create 'couples' table
create table if not exists public.couples (
    id uuid primary key default gen_random_uuid(),
    couple_code text unique not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 2. Create 'expenses' table
create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    couple_id uuid references public.couples(id) on delete cascade not null,
    user_name text not null,
    amount numeric not null,
    category text not null,
    date text not null,
    note text default '',
    latitude double precision,
    longitude double precision,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 3. Safely enable Supabase Realtime (avoids 42710 error if already added)
do ${'$'}${'$'}
begin
  if not exists (
    select 1 from pg_publication_tables 
    where pubname = 'supabase_realtime' 
      and schemaname = 'public' 
      and tablename = 'expenses'
  ) then
    alter publication supabase_realtime add table public.expenses;
  end if;
end ${'$'}${'$'};

-- 4. Enable Row Level Security (RLS)
alter table public.couples enable row level security;
alter table public.expenses enable row level security;

-- 5. Safe policies (drops previous if re-running)
drop policy if exists "Allow all access to couples" on public.couples;
create policy "Allow all access to couples" on public.couples 
    for all using (true) with check (true);

drop policy if exists "Allow all access to expenses" on public.expenses;
create policy "Allow all access to expenses" on public.expenses 
    for all using (true) with check (true);
"""
    }
}
