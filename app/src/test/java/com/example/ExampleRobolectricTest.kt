package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CouplePreferences
import com.example.data.local.DeviceHelper
import com.example.data.local.LocalCoupleCache
import com.example.data.model.AuthResult
import com.example.data.model.AuthState
import com.example.data.model.CoupleMemo
import com.example.data.model.DeviceSession
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.MoneyAccount
import com.example.data.model.MoneyAccountType
import com.example.data.model.OwnerMoneySummary
import com.example.data.model.SplitType
import com.example.data.repository.ProfileDataSource
import com.example.data.repository.ProfileRepository
import com.example.data.repository.RawSessionStatus
import com.example.data.repository.SupabaseAuthRepository
import com.example.data.service.AccountLinkingService
import com.example.data.supabase.ResilientSupabaseDns
import com.example.data.supabase.SupabaseClientManager
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseRepository
import com.example.ui.components.createSafeMapWebViewClient
import com.example.util.PermissionHelper
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `main activity launches and renders without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.launch_window_background))
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_stat_couple_heart))
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_couple_heart_emblem_1794818360746))
    assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
    assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))

    composeTestRule.waitForIdle()
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Couple Expense", appName)
  }

  @Test
  fun `device helper returns stable persisted device id`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firstHelper = DeviceHelper(context)
    val secondHelper = DeviceHelper(context)

    val id1 = firstHelper.getDeviceId()
    val id2 = secondHelper.getDeviceId()

    assertTrue(id1.isNotBlank())
    assertEquals(id1, id2)
  }

  @Test
  fun `device session saves restores and revokes cleanly`() = runBlocking {
    val clientManager = SupabaseClientManager(clientProvider = SupabaseClientProvider("", ""))
    val repository = SupabaseRepository(clientManager)

    val session = DeviceSession(
      device_id = "device-test-1234",
      couple_id = "couple-uuid-1",
      couple_code = "LOVE24",
      user_name = "MinNyo",
      email = "minnyo.work@gmail.com",
      device_name = "Pixel 8",
      is_active = true,
      last_login_at = "2026-11-13T10:00:00Z"
    )

    val saveResult = repository.saveDeviceSession(session)
    assertTrue(saveResult.isSuccess)

    val restored = repository.restoreDeviceSession("device-test-1234")
    assertNotNull(restored)
    assertEquals("LOVE24", restored?.couple_code)
    assertEquals("MinNyo", restored?.user_name)

    val activeList = repository.deviceSessionsFlow.first()
    assertEquals(1, activeList.size)

    repository.removeDeviceSession("device-test-1234", "couple-uuid-1")
    val afterRevoke = repository.restoreDeviceSession("device-test-1234")
    assertNull(afterRevoke)
  }

  @Test
  fun `account linking service generates valid 6-digit numeric codes`() = runBlocking {
    val repository = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")))
    val linkingService = AccountLinkingService(repository)

    assertTrue(linkingService.isValidSixDigitCode("482910"))
    assertFalse(linkingService.isValidSixDigitCode("12345"))
    assertFalse(linkingService.isValidSixDigitCode("ABC123"))

    val generatedCodes = mutableSetOf<String>()
    repeat(10) {
      val codeResult = linkingService.generateUniqueLinkCode(
        coupleId = "couple-link-1",
        userEmail = "partner1@couple.app",
        userName = "Alex"
      )
      assertTrue(codeResult.isSuccess)
      val code = codeResult.getOrThrow().link_code
      assertTrue("Expected 6-digit code, got $code", linkingService.isValidSixDigitCode(code))
      generatedCodes.add(code)
    }
    assertTrue(generatedCodes.isNotEmpty())
  }

  @Test
  fun `local couple cache persists data and counts on disk across repository restarts`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = LocalCoupleCache(context)
    val coupleId = "cache-couple-test-1"
    cache.clearCoupleCache(coupleId)

    val repo1 = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), cache)
    repo1.addExpense(
      Expense(
        id = "cached-exp-1",
        couple_id = coupleId,
        user_name = "Alex",
        amount = 25000.0,
        category = ExpenseCategory.FOOD.name,
        date = "2026-11-15",
        note = "Sushi dinner",
        created_at = "2026-11-15T18:00:00Z"
      )
    )
    repo1.addMemo(
      CoupleMemo(
        id = "cached-memo-1",
        couple_id = coupleId,
        user_name = "Maya",
        title = "Our Sunset",
        note = "Lake walk",
        image_url = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
        date = "2026-11-15",
        created_at = "2026-11-15T18:10:00Z"
      )
    )

    // Simulate cold app restart with fresh cache + fresh repository reading from disk
    val freshCache = LocalCoupleCache(context)
    val repo2 = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), freshCache)
    repo2.hydrateFromLocalCache(coupleId)

    val cachedExpenses = repo2.expensesFlow.first()
    val cachedMemos = repo2.memosFlow.first()
    val snapshot = freshCache.getSnapshot(coupleId)

    assertEquals(1, cachedExpenses.size)
    assertEquals("cached-exp-1", cachedExpenses.first().id)
    assertEquals(1, cachedMemos.size)
    assertEquals("cached-memo-1", cachedMemos.first().id)
    assertNotNull(snapshot)
    assertEquals(1, snapshot?.counts?.expenses_count)
    assertEquals("2026-11-15T18:00:00Z", snapshot?.counts?.expenses_latest)
    assertEquals(1, snapshot?.counts?.memos_count)
    assertEquals("2026-11-15T18:10:00Z", snapshot?.counts?.memos_latest)

    freshCache.clearCoupleCache(coupleId)
  }

  @Test
  fun `permission helper reflects granted states and preferences persist prompt state`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val shadowApp = Shadows.shadowOf(app)

    assertFalse(PermissionHelper.hasLocationPermission(app))
    assertFalse(PermissionHelper.hasPreciseLocationPermission(app))

    shadowApp.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    assertTrue(PermissionHelper.hasLocationPermission(app))
    assertTrue(PermissionHelper.hasApproximateLocationPermission(app))
    assertFalse(PermissionHelper.hasPreciseLocationPermission(app))

    shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    assertTrue(PermissionHelper.hasPreciseLocationPermission(app))

    val prefs = CouplePreferences(app)
    try {
      prefs.setPermissionsPrompted(false)
      assertFalse(prefs.userSessionFlow.first().hasPromptedPermissions)
      prefs.setPermissionsPrompted(true)
      assertTrue(prefs.userSessionFlow.first().hasPromptedPermissions)
    } finally {
      prefs.setPermissionsPrompted(false)
    }
  }

  @Test
  fun `money accounts stay strictly separate between me and girlfriend across cash multi-bank and card`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = LocalCoupleCache(context)
    val repo = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), cache)
    val coupleId = "couple-money-sep-1"

    val myCash = MoneyAccount(
      id = "acc-me-cash",
      couple_id = coupleId,
      owner_name = "Alex",
      owner_email = "alex@example.com",
      account_type = MoneyAccountType.CASH.name,
      account_name = "Pocket Cash",
      balance = 100_000.0,
      initial_balance = 100_000.0
    )
    val myKbzBank = MoneyAccount(
      id = "acc-me-bank-1",
      couple_id = coupleId,
      owner_name = "Alex",
      owner_email = "alex@example.com",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "KBZ Bank",
      account_name = "Salary Account",
      account_number_last4 = "1122",
      balance = 500_000.0,
      initial_balance = 500_000.0
    )
    val myAyaBank = MoneyAccount(
      id = "acc-me-bank-2",
      couple_id = coupleId,
      owner_name = "Alex",
      owner_email = "alex@example.com",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "AYA Bank",
      account_name = "Savings Account",
      account_number_last4 = "3344",
      balance = 250_000.0,
      initial_balance = 250_000.0
    )
    val myCard = MoneyAccount(
      id = "acc-me-card-1",
      couple_id = coupleId,
      owner_name = "Alex",
      owner_email = "alex@example.com",
      account_type = MoneyAccountType.CARD.name,
      institution_name = "Visa",
      account_name = "Travel Card",
      account_number_last4 = "9988",
      balance = 150_000.0,
      initial_balance = 150_000.0
    )
    val gfBank = MoneyAccount(
      id = "acc-gf-bank-1",
      couple_id = coupleId,
      owner_name = "Suzy",
      owner_email = "suzy@example.com",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "CB Bank",
      account_name = "Personal Account",
      account_number_last4 = "7766",
      balance = 800_000.0,
      initial_balance = 800_000.0
    )

    listOf(myCash, myKbzBank, myAyaBank, myCard, gfBank).forEach { acc ->
      repo.upsertMoneyAccount(acc)
    }

    val all = repo.moneyAccountsFlow.first()
    val myList = all.filter { it.matchesOwner("Alex", "alex@example.com") }
    val gfList = all.filter { it.matchesOwner("Suzy", "suzy@example.com") }

    assertEquals(4, myList.size)
    assertEquals(1, gfList.size)

    val mySummary = OwnerMoneySummary.fromAccounts("Alex", myList)
    val gfSummary = OwnerMoneySummary.fromAccounts("Suzy", gfList)

    assertEquals(1_000_000.0, mySummary.totalBalance, 0.01)
    assertEquals(100_000.0, mySummary.cashBalance, 0.01)
    assertEquals(750_000.0, mySummary.bankBalance, 0.01)
    assertEquals(2, mySummary.bankCount)
    assertEquals(150_000.0, mySummary.cardBalance, 0.01)
    assertEquals(800_000.0, gfSummary.totalBalance, 0.01)

    // Verify transfer between Alex's KBZ Bank and Pocket Cash updates real balances
    val transferRes = repo.transferBetweenMoneyAccounts(myKbzBank.id, myCash.id, 50_000.0, coupleId)
    assertTrue(transferRes.isSuccess)
    val afterTransfer = repo.moneyAccountsFlow.first()
    assertEquals(450_000.0, afterTransfer.first { it.id == myKbzBank.id }.balance, 0.01)
    assertEquals(150_000.0, afterTransfer.first { it.id == myCash.id }.balance, 0.01)
    assertEquals(800_000.0, afterTransfer.first { it.id == gfBank.id }.balance, 0.01)

    // Verify local cache persists money accounts cleanly
    val snap = cache.getSnapshot(coupleId)
    assertNotNull(snap)
    assertEquals(5, snap!!.money_accounts.size)
  }

  @Test
  fun `expense metadata encodes linked account and balance adjusts on add and delete`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = LocalCoupleCache(context)
    val repo = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), cache)
    val coupleId = "couple-money-sync-2"

    val myCard = MoneyAccount(
      id = "acc-card-sync",
      couple_id = coupleId,
      owner_name = "Alex",
      account_type = MoneyAccountType.CARD.name,
      institution_name = "Mastercard",
      account_name = "Daily Card",
      account_number_last4 = "4242",
      balance = 300_000.0,
      initial_balance = 300_000.0
    )
    repo.upsertMoneyAccount(myCard)

    val noteWithAccount = Expense.buildNote(
      userNote = "Coffee & cake",
      splitType = SplitType.PERSONAL,
      accountId = myCard.id,
      accountLabel = myCard.shortBadgeLabel
    )
    val exp = Expense(
      id = "exp-sync-1",
      couple_id = coupleId,
      user_name = "Alex",
      amount = 25_000.0,
      category = ExpenseCategory.COFFEE.name,
      date = "2026-11-16",
      note = noteWithAccount,
      created_at = "2026-11-16T08:00:00Z"
    )
    assertEquals("Coffee & cake", exp.cleanNote)
    assertEquals(myCard.id, exp.moneyAccountId)
    assertEquals("Mastercard (4242)", exp.moneyAccountLabel)

    repo.addExpense(exp)
    repo.adjustMoneyAccountBalance(myCard.id, coupleId, -exp.amount)
    assertEquals(275_000.0, repo.moneyAccountsFlow.first().first { it.id == myCard.id }.balance, 0.01)

    // Refund when expense deleted
    repo.deleteExpense(exp.id, coupleId)
    repo.adjustMoneyAccountBalance(myCard.id, coupleId, exp.amount)
    assertEquals(300_000.0, repo.moneyAccountsFlow.first().first { it.id == myCard.id }.balance, 0.01)
  }

  @Test
  fun `supabase url normalization fixes typos whitespace scheme and trailing slashes`() {
    assertEquals(
      "https://yfsrfjjjkxavokwsufog.supabase.co",
      SupabaseClientManager.normalizeSupabaseUrl("  yfsrfjjjkxavokwsufog.supabse.co/  ")
    )
    assertEquals(
      "https://myproj.supabase.co",
      SupabaseClientManager.normalizeSupabaseUrl("https://myproj.supbase.co///")
    )
    assertEquals("", SupabaseClientManager.normalizeSupabaseUrl("   "))
    assertEquals(
      "yfsrfjjjkxavokwsufog.supabase.co",
      SupabaseClientManager.extractHost("https://yfsrfjjjkxavokwsufog.supabase.co/")
    )
  }

  @Test
  fun `resilient doh parser extracts A records and detects NXDOMAIN status`() {
    val validJson = """
      {
        "Status": 0,
        "Answer": [
          {"name": "example.supabase.co.", "type": 5, "TTL": 60, "data": "alias.supabase.co."},
          {"name": "alias.supabase.co.", "type": 1, "TTL": 60, "data": "104.18.38.10"},
          {"name": "alias.supabase.co.", "type": 1, "TTL": 60, "data": "172.64.149.246"}
        ]
      }
    """.trimIndent()
    val parsed = ResilientSupabaseDns.parseDohResponse(validJson)
    assertEquals(0, parsed.status)
    assertFalse(parsed.isNxDomain)
    assertEquals(listOf("104.18.38.10", "172.64.149.246"), parsed.ips)

    val nxJson = """{"Status": 3, "Question": [{"name": "missing.supabase.co.", "type": 1}]}"""
    val nxParsed = ResilientSupabaseDns.parseDohResponse(nxJson)
    assertEquals(3, nxParsed.status)
    assertTrue(nxParsed.isNxDomain)
    assertTrue(nxParsed.ips.isEmpty())
  }

  @Test
  fun `format network exception translates unknown host into actionable settings guidance`() {
    val manager = SupabaseClientManager(
      clientProvider = SupabaseClientProvider(
        rawUrl = "https://yfsrfjjjkxavokwsufog.supabase.co",
        rawAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSJ9.signatureplaceholder"
      )
    )
    val mapped = manager.formatNetworkException(
      UnknownHostException("Unable to resolve host \"yfsrfjjjkxavokwsufog.supabase.co\": No address associated with hostname")
    )
    val msg = mapped.message.orEmpty()
    assertTrue(msg.contains("yfsrfjjjkxavokwsufog.supabase.co"))
    assertTrue(msg.contains("Settings", ignoreCase = true))
  }

  @Test
  fun `format network exception handles port 443 connect timeout and extracts failed public ip`() {
    val rawTimeout = SocketTimeoutException(
      "failed to connect to yfsrfjjjkxavokwsufog.supabase.co/172.64.149.246 (port 443) from /192.168.195.205 (port 58742) after 15000ms"
    )
    val wrapped = IOException("Route failure", rawTimeout)
    assertEquals("172.64.149.246", SupabaseClientManager.extractIpFromException(wrapped))

    val manager = SupabaseClientManager(
      clientProvider = SupabaseClientProvider(
        rawUrl = "https://yfsrfjjjkxavokwsufog.supabase.co",
        rawAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSJ9.signatureplaceholder"
      )
    )
    val formatted = manager.formatNetworkException(wrapped)
    val msg = formatted.message.orEmpty()
    assertTrue(msg.contains("172.64.149.246"))
    assertTrue(msg.contains("port 443", ignoreCase = true))
    assertTrue(msg.contains("4G", ignoreCase = true))
    assertTrue(msg.contains("Settings", ignoreCase = true))
  }

  @Test
  fun `auth result json decodes safely when optional fields are null and startRealtime handles edge urls`() {
    val manager = SupabaseClientManager(
      clientProvider = SupabaseClientProvider(
        rawUrl = "https://yfsrfjjjkxavokwsufog.supabase.co",
        rawAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.sig"
      )
    )
    val decoded = manager.parseResponseList<AuthResult>(
      """{"success":true,"message":null,"auth_user_id":"u1","email":null,"user_name":"Min Nyo","couple_id":"c1","couple_code":"123456"}"""
    )
    assertEquals(1, decoded.size)
    assertTrue(decoded.first().success)
    assertEquals("u1", decoded.first().auth_user_id)
    assertEquals("Min Nyo", decoded.first().user_name)
    assertEquals("", decoded.first().email)

    // Invalid/blank URL should never crash startRealtime
    val badManager = SupabaseClientManager(clientProvider = SupabaseClientProvider("", ""))
    badManager.startRealtime("c1")
    badManager.stopRealtime()
  }

  @Test
  fun `login screen displays create account option and omits quick preview demo button`() {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("login_username_input").fetchSemanticsNodes().isNotEmpty()
    }
    // Ensure demo button is completely removed
    assertEquals(0, composeTestRule.onAllNodesWithTag("demo_mode_button").fetchSemanticsNodes().size)
    // Ensure Sign In & Create Account tabs and CTA are visible
    composeTestRule.onNodeWithTag("login_username_input").assertExists()
    composeTestRule.onNodeWithTag("login_password_input").assertExists()
    composeTestRule.onNodeWithTag("switch_to_create_account_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("register_email_input").assertExists()
    composeTestRule.onNodeWithTag("register_password_input").assertExists()
    composeTestRule.onNodeWithTag("register_confirm_password_input").assertExists()
    composeTestRule.onNodeWithTag("create_code_button").assertExists()
  }

  @Test
  fun `map webview client handles onRenderProcessGone safely without killing host process`() {
    var crashHandled = false
    val client = createSafeMapWebViewClient(
      onMainFrameError = {},
      onPageFinishedCallback = {},
      onRendererCrash = { crashHandled = true }
    )
    val context = ApplicationProvider.getApplicationContext<Context>()
    val webView = android.webkit.WebView(context)
    val handled = client.onRenderProcessGone(webView, null)
    assertTrue(handled)
    assertTrue(crashHandled)
  }

  @Test
  fun `fresh repository and preferences contain zero dummy data`() = runBlocking {
    val repository = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")))

    assertTrue(repository.expensesFlow.first().isEmpty())
    assertTrue(repository.profilesFlow.first().isEmpty())
    assertTrue(repository.memosFlow.first().isEmpty())
    assertTrue(repository.liveLocationsFlow.first().isEmpty())
    assertTrue(repository.locationHistoryFlow.first().isEmpty())
    assertTrue(repository.deviceSessionsFlow.first().isEmpty())
    assertTrue(repository.moneyAccountsFlow.first().isEmpty())

    // Fresh UserSession has blank anniversary date, and milestone/love day helpers handle empty safely
    val freshSession = com.example.data.local.UserSession()
    assertEquals("", freshSession.relationshipStartDate)
    assertNull(com.example.util.FormatUtils.calculateLoveDays(freshSession.relationshipStartDate))
    val (nextMilestone, allMilestones) = com.example.util.FormatUtils.calculateMilestones(freshSession.relationshipStartDate)
    assertNull(nextMilestone)
    assertTrue(allMilestones.isEmpty())

    // Fresh ViewModel starts with empty savingsGoals, recurringBills & movies
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = com.example.ui.viewmodel.CoupleExpenseViewModel(app)
    assertTrue(vm.expenses.value.isEmpty())
    assertTrue(vm.savingsGoals.first().isEmpty())
    assertTrue(vm.recurringBills.first().isEmpty())
    assertTrue(vm.movies.value.isEmpty())
    assertTrue(vm.moneyAccounts.value.isEmpty())
  }

  @Test
  fun `settle up math zeroes net balance in both directions`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = com.example.ui.viewmodel.CoupleExpenseViewModel(app)
    val session = com.example.data.local.UserSession(
      userName = "MinNyo",
      partnerName = "Suzy",
      coupleId = "couple-settle-1",
      isPaired = true
    )

    // Case 1: Partner owes me 50,000 MMK (shared 100,000 paid by me)
    val sharedByMe = Expense(
      id = "s1",
      couple_id = "couple-settle-1",
      user_name = "MinNyo",
      amount = 100_000.0,
      category = ExpenseCategory.FOOD.name,
      date = SupabaseRepository.getTodayDate(),
      note = Expense.buildNote("Dinner", SplitType.SHARED)
    )
    val beforePartnerPays = vm.calculateSummary(listOf(sharedByMe), session)
    assertEquals(50_000.0, beforePartnerPays.netBalance, 0.01)

    val partnerPayer = vm.resolveSettlementPayer(beforePartnerPays.netBalance, session.userName, session.partnerName)
    assertEquals("Suzy", partnerPayer)
    val settleFromPartner = Expense(
      id = "s2",
      couple_id = "couple-settle-1",
      user_name = partnerPayer,
      amount = 50_000.0,
      category = ExpenseCategory.OTHER.name,
      date = SupabaseRepository.getTodayDate(),
      note = Expense.buildNote("Settle Up", SplitType.SETTLEMENT)
    )
    val afterPartnerPays = vm.calculateSummary(listOf(sharedByMe, settleFromPartner), session)
    assertEquals(0.0, afterPartnerPays.netBalance, 0.01)

    // Case 2: I owe partner 40,000 MMK (paid-for-partner 40,000 paid by Suzy)
    val paidByPartnerForMe = Expense(
      id = "s3",
      couple_id = "couple-settle-1",
      user_name = "Suzy",
      amount = 40_000.0,
      category = ExpenseCategory.GIFTS.name,
      date = SupabaseRepository.getTodayDate(),
      note = Expense.buildNote("Flight", SplitType.PAID_FOR_PARTNER)
    )
    val beforeMePays = vm.calculateSummary(listOf(paidByPartnerForMe), session)
    assertEquals(-40_000.0, beforeMePays.netBalance, 0.01)

    val mePayer = vm.resolveSettlementPayer(beforeMePays.netBalance, session.userName, session.partnerName)
    assertEquals("MinNyo", mePayer)
    val settleFromMe = Expense(
      id = "s4",
      couple_id = "couple-settle-1",
      user_name = mePayer,
      amount = 40_000.0,
      category = ExpenseCategory.OTHER.name,
      date = SupabaseRepository.getTodayDate(),
      note = Expense.buildNote("Settle Up", SplitType.SETTLEMENT)
    )
    val afterMePays = vm.calculateSummary(listOf(paidByPartnerForMe, settleFromMe), session)
    assertEquals(0.0, afterMePays.netBalance, 0.01)
  }

  @Test
  fun `love days counts start day as day 1 and avoids DST off by one`() {
    // Same day = Day 1 (not 0 / 1 day late)
    val sameDay = com.example.util.FormatUtils.calculateLoveDaysForDate("2026-11-18", 2026, 11, 18)
    assertNotNull(sameDay)
    assertEquals(1L, sameDay?.totalDays)

    // Yesterday = Day 2
    val secondDay = com.example.util.FormatUtils.calculateLoveDaysForDate("2026-11-17", 2026, 11, 18)
    assertEquals(2L, secondDay?.totalDays)

    // Across Spring DST boundary (2026-01-01 to 2026-08-01): 31+28+31+30+31+30+31 = 212 elapsed -> Day 213
    val dstSpan = com.example.util.FormatUtils.calculateLoveDaysForDate("2026-01-01", 2026, 8, 1)
    assertEquals(213L, dstSpan?.totalDays)

    // Day 100 check: 2026-01-01 + 99 days = 2026-04-10 -> totalDays == 100 and 100 Days milestone achieved
    val day100 = com.example.util.FormatUtils.calculateLoveDaysForDate("2026-01-01", 2026, 4, 10)
    assertEquals(100L, day100?.totalDays)
    val (nextM, allM) = com.example.util.FormatUtils.calculateMilestonesForDate("2026-01-01", 2026, 4, 10)
    val milestone100 = allM.first { it.targetDays == 100L }
    assertTrue(milestone100.isAchieved)
    assertEquals(0L, milestone100.daysLeft)
    assertEquals(200L, nextM?.targetDays)
  }

  @Test
  fun `viewModel combines cash multiple banks and card for same owner across username and display name`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = com.example.ui.viewmodel.CoupleExpenseViewModel(app)
    val session = com.example.data.local.UserSession(
      userName = "Min Nyo",
      username = "minnyo_dev",
      userEmail = "",
      partnerName = "Suzy",
      coupleId = "couple-combine-1",
      isPaired = true
    )

    val cash = MoneyAccount(
      id = "c1",
      couple_id = "couple-combine-1",
      owner_name = "You",
      account_type = MoneyAccountType.CASH.name,
      account_name = "Wallet Cash",
      balance = 120_000.0
    )
    val bank1 = MoneyAccount(
      id = "b1",
      couple_id = "couple-combine-1",
      owner_name = "Min Nyo",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "KBZ Bank",
      account_name = "Main",
      balance = 450_000.0
    )
    val bank2 = MoneyAccount(
      id = "b2",
      couple_id = "couple-combine-1",
      owner_name = "minnyo_dev",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "AYA Bank",
      account_name = "Savings",
      balance = 330_000.0
    )
    val partnerBank = MoneyAccount(
      id = "pb1",
      couple_id = "couple-combine-1",
      owner_name = "Suzy",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "CB Bank",
      account_name = "Partner",
      balance = 900_000.0
    )

    val all = listOf(cash, bank1, bank2, partnerBank)
    val mine = all.filter { vm.isAccountOwnedByMe(it, session) }
    val partner = all.filterNot { vm.isAccountOwnedByMe(it, session) }

    assertEquals(3, mine.size)
    assertEquals(1, partner.size)

    val summary = OwnerMoneySummary.fromAccounts("Min Nyo", mine)
    assertEquals(120_000.0, summary.cashBalance, 0.01)
    assertEquals(780_000.0, summary.bankBalance, 0.01)
    assertEquals(900_000.0, summary.cashAndBankBalance, 0.01)
    assertEquals(900_000.0, summary.totalBalance, 0.01)
    assertEquals(2, summary.bankCount)
  }

  @Test
  fun `money accounts added before remote sync survive cold restart and refresh without disappearing`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = LocalCoupleCache(context)
    val coupleId = "couple-restart-persist-1"
    cache.clearCoupleCache(coupleId)

    val repo1 = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), cache)
    val cash = MoneyAccount(
      id = "restart-cash-1",
      couple_id = coupleId,
      owner_name = "Min Nyo",
      account_type = MoneyAccountType.CASH.name,
      account_name = "Cash Wallet",
      balance = 150_000.0
    )
    val bank1 = MoneyAccount(
      id = "restart-bank-1",
      couple_id = coupleId,
      owner_name = "Min Nyo",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "KBZ Bank",
      account_name = "Main Bank",
      balance = 450_000.0
    )
    val bank2 = MoneyAccount(
      id = "restart-bank-2",
      couple_id = coupleId,
      owner_name = "Min Nyo",
      account_type = MoneyAccountType.BANK.name,
      institution_name = "AYA Bank",
      account_name = "Reserve Bank",
      balance = 300_000.0
    )

    repo1.upsertMoneyAccount(cash)
    repo1.upsertMoneyAccount(bank1)
    repo1.upsertMoneyAccount(bank2)

    val snapBeforeRestart = cache.getSnapshot(coupleId)
    assertNotNull(snapBeforeRestart)
    assertEquals(3, snapBeforeRestart!!.money_accounts.size)
    assertTrue(snapBeforeRestart.pending_money_account_ids.containsAll(listOf("restart-cash-1", "restart-bank-1", "restart-bank-2")))

    // Simulate closing the app completely and reopening with fresh cache + repository
    val freshCache = LocalCoupleCache(context)
    val repo2 = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), freshCache)
    repo2.hydrateFromLocalCache(coupleId)
    repo2.syncChangedTablesOnly(coupleId)

    val restored = repo2.moneyAccountsFlow.first()
    assertEquals(3, restored.size)
    val summary = OwnerMoneySummary.fromAccounts("Min Nyo", restored)
    assertEquals(150_000.0, summary.cashBalance, 0.01)
    assertEquals(750_000.0, summary.bankBalance, 0.01)
    assertEquals(900_000.0, summary.totalBalance, 0.01)

    freshCache.clearCoupleCache(coupleId)
  }

  @Test
  fun `logout clears active session while keeping cached couple data for full restoration on re-login`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val cache = LocalCoupleCache(app)
    val prefs = CouplePreferences(app)
    val coupleId = "couple-logout-restore-1"
    cache.clearCoupleCache(coupleId)

    val repo1 = SupabaseRepository(SupabaseClientManager(clientProvider = SupabaseClientProvider("", "")), cache)
    try {
      prefs.saveSession(
        userName = "Min Nyo",
        coupleId = coupleId,
        coupleCode = "889900",
        username = "minnyo_dev"
      )
      prefs.updateUserProfile(
        name = "Min Nyo",
        email = "minnyo@example.com",
        dob = "1998-05-20",
        avatarPath = ""
      )
      repo1.saveProfile(
        com.example.data.model.UserProfile(
          id = "prof-me-1",
          couple_id = coupleId,
          user_name = "Min Nyo",
          email = "minnyo@example.com",
          date_of_birth = "1998-05-20"
        )
      )
      repo1.upsertMoneyAccount(
        MoneyAccount(
          id = "logout-cash-1",
          couple_id = coupleId,
          owner_name = "Min Nyo",
          account_type = MoneyAccountType.CASH.name,
          account_name = "Wallet",
          balance = 100_000.0
        )
      )
      repo1.upsertMoneyAccount(
        MoneyAccount(
          id = "logout-bank-1",
          couple_id = coupleId,
          owner_name = "Min Nyo",
          account_type = MoneyAccountType.BANK.name,
          institution_name = "CB Bank",
          account_name = "Savings",
          balance = 400_000.0
        )
      )
      repo1.addExpense(
        Expense(
          id = "logout-exp-1",
          couple_id = coupleId,
          user_name = "Min Nyo",
          amount = 25_000.0,
          category = ExpenseCategory.FOOD.name,
          date = "2026-11-18",
          note = "Lunch"
        )
      )

      // Perform Logout (preserve disk cache while clearing active session + RAM)
      repo1.clearSessionState(coupleIdToClearCache = coupleId, clearDiskCache = false)
      prefs.clearSession(clearProfileDetails = true)

      val afterLogoutSession = prefs.userSessionFlow.first()
      assertFalse(afterLogoutSession.isPaired)
      assertEquals("", afterLogoutSession.coupleId)
      assertTrue(repo1.moneyAccountsFlow.first().isEmpty())
      assertTrue(repo1.expensesFlow.first().isEmpty())

      // Simulate logging back into the same account
      prefs.saveSession(
        userName = "Min Nyo",
        coupleId = coupleId,
        coupleCode = "889900",
        username = "minnyo_dev"
      )
      repo1.hydrateFromLocalCache(coupleId)
      val restoredMoney = repo1.moneyAccountsFlow.first()
      val restoredExpenses = repo1.expensesFlow.first()
      val restoredProfiles = repo1.profilesFlow.first()

      assertEquals(2, restoredMoney.size)
      assertEquals(500_000.0, OwnerMoneySummary.fromAccounts("Min Nyo", restoredMoney).totalBalance, 0.01)
      assertEquals(1, restoredExpenses.size)
      assertEquals(1, restoredProfiles.size)
      assertEquals("1998-05-20", restoredProfiles.first().date_of_birth)
    } finally {
      repo1.clearSessionState(coupleIdToClearCache = coupleId, clearDiskCache = true)
      prefs.clearSession(clearProfileDetails = true)
      cache.clearCoupleCache(coupleId)
    }
  }

  @Test
  fun `couple preferences store non-sensitive metadata and never leak tokens on clearSession`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val prefs = CouplePreferences(app)
    try {
      prefs.saveSession(
        userName = "Min Nyo",
        coupleId = "couple-uid-1",
        coupleCode = "123456",
        username = "minnyo",
        authUserId = "auth-uid-999"
      )
      val saved = prefs.userSessionFlow.first()
      assertEquals("Min Nyo", saved.userName)
      assertEquals("couple-uid-1", saved.coupleId)
      assertEquals("auth-uid-999", saved.authUserId)
      assertTrue(saved.isPaired)

      prefs.clearSession(clearProfileDetails = true)
      val cleared = prefs.userSessionFlow.first()
      assertEquals("", cleared.coupleId)
      assertEquals("", cleared.authUserId)
      assertFalse(cleared.isPaired)
    } finally {
      prefs.clearSession(clearProfileDetails = true)
    }
  }

}
