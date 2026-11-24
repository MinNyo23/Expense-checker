package com.example

import com.example.data.model.Couple
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.repository.isValidSignupOtpToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun couple_serialization_isCorrect() {
        val couple = Couple(id = "c1", couple_code = "LOVE24", created_at = "2026-10-15T00:00:00Z")
        val serialized = json.encodeToString(couple)
        assertTrue(serialized.contains("LOVE24"))

        val deserialized = json.decodeFromString<Couple>(serialized)
        assertEquals(couple.id, deserialized.id)
        assertEquals("LOVE24", deserialized.couple_code)
    }

    @Test
    fun expense_serialization_withCoordinates_isCorrect() {
        val expense = Expense(
            id = "exp-1",
            couple_id = "c1",
            user_name = "Alex",
            amount = 45.50,
            category = ExpenseCategory.FOOD.name,
            date = "2026-10-15",
            note = "Dinner at Italian bistro",
            latitude = 37.7749,
            longitude = -122.4194
        )

        val serialized = json.encodeToString(expense)
        assertTrue(serialized.contains("37.7749"))

        val deserialized = json.decodeFromString<Expense>(serialized)
        assertEquals(45.50, deserialized.amount, 0.001)
        assertEquals(37.7749, deserialized.latitude!!, 0.0001)
        assertEquals(-122.4194, deserialized.longitude!!, 0.0001)
    }

    @Test
    fun category_parsing_isCorrect() {
        val cat = ExpenseCategory.fromString("GROCERIES")
        assertEquals(ExpenseCategory.GROCERIES, cat)

        val unknownCat = ExpenseCategory.fromString("NonExistent")
        assertEquals(ExpenseCategory.OTHER, unknownCat)
    }

    @Test
    fun mmk_formatting_isCorrect() {
        val formatted = com.example.util.FormatUtils.formatMmk(50000.0)
        assertTrue(formatted.contains("50,000 MMK"))
    }

    @Test
    fun love_days_calculation_isCorrect() {
        val info = com.example.util.FormatUtils.calculateLoveDays("2024-02-14")
        assertNotNull(info)
        assertTrue(info!!.totalDays > 0)
        assertTrue(info.years >= 0)
        assertTrue(info.months in 0..11)
        assertTrue(info.days >= 0)
        assertTrue(info.formattedBreakdown.contains("Year") || info.formattedBreakdown.contains("Month"))
    }

    @Test
    fun split_type_note_encoding_and_parsing_isCorrect() {
        val encoded = Expense.buildNote("Romantic Dinner", com.example.data.model.SplitType.PAID_FOR_PARTNER, "/path/to/receipt.jpg")
        assertTrue(encoded.contains("[split:100_PARTNER]"))
        assertTrue(encoded.contains("[photo:/path/to/receipt.jpg]"))

        val dummyExpense = Expense(
            id = "e1",
            couple_id = "c1",
            user_name = "Alex",
            amount = 35000.0,
            category = ExpenseCategory.FOOD.name,
            date = "2026-10-30",
            note = encoded
        )
        assertEquals("Romantic Dinner", dummyExpense.cleanNote)
        assertEquals(com.example.data.model.SplitType.PAID_FOR_PARTNER, dummyExpense.splitType)
        assertEquals("/path/to/receipt.jpg", dummyExpense.photoUri)
    }

    @Test
    fun savings_goal_progress_isCorrect() {
        val goal = com.example.data.model.SavingsGoal(
            id = "g1",
            title = "Japan Vacation",
            targetAmount = 2000000.0,
            currentAmount = 1000000.0
        )
        assertEquals(50.0f, goal.progressPercentage, 0.01f)
        assertEquals(1000000.0, goal.remainingAmount, 0.01)
        assertEquals(false, goal.isCompleted)
    }

    @Test
    fun milestones_calculation_isCorrect() {
        val (next, all) = com.example.util.FormatUtils.calculateMilestones("2024-01-01")
        assertTrue(all.isNotEmpty())
        assertNotNull(all.firstOrNull { it.targetDays == 100L })
    }

    @Test
    fun csv_export_generation_isCorrect() {
        val expense = Expense(
            id = "e-1",
            couple_id = "c-1",
            user_name = "Sam",
            amount = 12000.0,
            category = ExpenseCategory.COFFEE.name,
            date = "2026-10-30",
            note = Expense.buildNote("Morning Latte", com.example.data.model.SplitType.SHARED)
        )
        val csv = com.example.util.FormatUtils.generateCsvExport(listOf(expense), "COUPLE99")
        assertTrue(csv.contains("Morning Latte"))
        assertTrue(csv.contains("12000"))
        assertTrue(csv.contains("50/50"))
    }

    @Test
    fun user_session_pin_status_isCorrect() {
        val noPinSession = com.example.data.local.UserSession()
        assertEquals(false, noPinSession.isPinSet)

        val invalidPinSession = com.example.data.local.UserSession(appPin = "12")
        assertEquals(false, invalidPinSession.isPinSet)

        val validPinSession = com.example.data.local.UserSession(appPin = "1234")
        assertEquals(true, validPinSession.isPinSet)
    }

    @Test
    fun supabase_error_mapper_redacts_jwt_and_sql_internals() {
        val rawLeak = Exception("ERROR: violates row-level security for relation user_accounts password_hash bearer eyJhbGciOiJIUzI1NiJ9.secretpayload.sig")
        val mapped = com.example.data.model.SupabaseErrorMapper.toUserMessage(rawLeak)
        assertTrue(mapped.contains("Permission denied", ignoreCase = true))
        assertTrue(!mapped.contains("password_hash"))
        assertTrue(!mapped.contains("user_accounts"))
        assertTrue(!mapped.contains("eyJ"))
        val sanitized = com.example.data.model.SupabaseErrorMapper.sanitizeMessage(
            "SELECT * FROM expenses WHERE jwt='eyJhbGciOiJIUzI1NiJ9.abc.def'",
            fallback = "Unable to load expenses."
        )
        assertEquals("Unable to load expenses.", sanitized)
    }

    @Test
    fun signup_otp_requires_exactly_six_digits() {
        assertTrue(isValidSignupOtpToken("123456"))
        assertFalse(isValidSignupOtpToken("12345"))
        assertFalse(isValidSignupOtpToken("1234567"))
        assertFalse(isValidSignupOtpToken("12a456"))
        assertFalse(isValidSignupOtpToken(" 123456 "))
    }

    @Test
    fun parse_response_list_handles_array_single_object_and_empty() {
        val manager = com.example.data.supabase.SupabaseClientManager()
        
        // Test array response
        val arrayJson = """[{"id":"c1","couple_code":"CODE1"}]"""
        val listFromArray = manager.parseResponseList<Couple>(arrayJson)
        assertEquals(1, listFromArray.size)
        assertEquals("CODE1", listFromArray[0].couple_code)

        // Test single object response
        val objectJson = """{"id":"c2","couple_code":"CODE2"}"""
        val listFromObject = manager.parseResponseList<Couple>(objectJson)
        assertEquals(1, listFromObject.size)
        assertEquals("CODE2", listFromObject[0].couple_code)

        // Test empty string response (minimal representation)
        val emptyList = manager.parseResponseList<Couple>("")
        assertEquals(0, emptyList.size)

        // Test whitespace or invalid response
        val blankList = manager.parseResponseList<Couple>("   ")
        assertEquals(0, blankList.size)
    }

    @Test
    fun generate_couple_code_is_valid_format() {
        val code = com.example.data.supabase.SupabaseRepository.generateCoupleCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
    }

    @Test
    fun user_profile_serialization_isCorrect() {
        val profile = com.example.data.model.UserProfile(
            id = "p1",
            couple_id = "c1",
            user_name = "Alex",
            email = "minnyo.work@gmail.com",
            date_of_birth = "2000-05-15",
            avatar_url = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
            role = "partner",
            updated_at = "2026-11-01T00:00:00Z"
        )
        val serialized = json.encodeToString(profile)
        assertTrue(serialized.contains("Alex"))
        assertTrue(serialized.contains("minnyo.work@gmail.com"))
        assertTrue(serialized.contains("data:image/jpeg;base64"))

        val deserialized = json.decodeFromString<com.example.data.model.UserProfile>(serialized)
        assertEquals("p1", deserialized.id)
        assertEquals("Alex", deserialized.user_name)
        assertEquals("minnyo.work@gmail.com", deserialized.email)
        assertEquals("2000-05-15", deserialized.date_of_birth)
    }

    @Test
    fun user_profile_includes_auth_user_id_for_rls_verification() {
        val profile = com.example.data.model.UserProfile(
            id = "p-rls-1",
            couple_id = "c1",
            user_name = "Alex",
            email = "alex@example.com",
            auth_user_id = "auth-uid-123"
        )
        val serialized = json.encodeToString(profile)
        assertTrue(serialized.contains("auth-uid-123"))
        val deserialized = json.decodeFromString<com.example.data.model.UserProfile>(serialized)
        assertEquals("auth-uid-123", deserialized.auth_user_id)
    }

    @Test
    fun couple_memo_serialization_isCorrect() {
        val memo = com.example.data.model.CoupleMemo(
            id = "memo-1",
            couple_id = "c1",
            user_name = "Alex",
            title = "Sunset at the beach",
            note = "Best date night ever with my love",
            image_url = "auth-uid-123/550e8400-e29b-41d4-a716-446655440000.webp",
            date = "2026-11-01",
            created_at = "2026-11-01T00:00:00Z"
        )
        val serialized = json.encodeToString(memo)
        assertTrue(serialized.contains("Sunset at the beach"))
        assertTrue(serialized.contains("Best date night ever"))
        assertTrue(serialized.contains("auth-uid-123/550e8400-e29b-41d4-a716-446655440000.webp"))

        val deserialized = json.decodeFromString<com.example.data.model.CoupleMemo>(serialized)
        assertEquals("memo-1", deserialized.id)
        assertEquals("Alex", deserialized.user_name)
        assertEquals("Sunset at the beach", deserialized.title)
        assertEquals("auth-uid-123/550e8400-e29b-41d4-a716-446655440000.webp", deserialized.image_url)
        assertEquals("2026-11-01", deserialized.date)
    }

    @Test
    fun couple_sync_counts_and_content_range_parsing_work() {
        val manager = com.example.data.supabase.SupabaseClientManager()
        assertEquals(42, manager.parseCountFromContentRange("0-0/42", 1))
        assertEquals(0, manager.parseCountFromContentRange("*/0", 0))
        assertEquals(3, manager.parseCountFromContentRange(null, 3))

        val countsJson = """
            {
              "expenses_count": 12,
              "expenses_latest": "2026-11-15T10:00:00Z",
              "memos_count": 5,
              "memos_latest": "2026-11-15T09:30:00Z",
              "profiles_count": 2,
              "profiles_latest": "2026-11-15T08:00:00Z",
              "live_locations_count": 2,
              "live_locations_latest": 1763200000000,
              "location_history_count": 9,
              "location_history_latest": 1763199000000,
              "device_sessions_count": 2,
              "device_sessions_latest": "2026-11-15T07:00:00Z"
            }
        """.trimIndent()
        val parsed = json.decodeFromString<com.example.data.model.CoupleSyncCounts>(countsJson)
        assertEquals(12, parsed.expenses_count)
        assertEquals(5, parsed.memos_count)
        assertEquals(2, parsed.profiles_count)
        assertEquals(1763200000000L, parsed.live_locations_latest)
    }

    @Test
    fun location_history_analyzer_separates_going_dots_and_stay_points() {
        val baseTime = 1763200000000L
        val items = listOf(
            // Alex going point #1 (moving)
            com.example.data.model.LocationHistoryItem(
                id = "h1",
                couple_id = "c1",
                user_name = "Alex",
                latitude = 16.8400,
                longitude = 96.1700,
                place_name = "16.8400, 96.1700",
                timestamp = "10:00:00 AM",
                date = "2026-11-16",
                recorded_at = baseTime
            ),
            // Partner Maya stays at Inya Cafe for 15 minutes (two pings 10m apart)
            com.example.data.model.LocationHistoryItem(
                id = "p1",
                couple_id = "c1",
                user_name = "Maya",
                latitude = 16.8500,
                longitude = 96.1600,
                place_name = "Inya Lake Cafe",
                timestamp = "10:05:00 AM",
                date = "2026-11-16",
                recorded_at = baseTime + 5 * 60_000L
            ),
            com.example.data.model.LocationHistoryItem(
                id = "p2",
                couple_id = "c1",
                user_name = "Maya",
                latitude = 16.8501,
                longitude = 96.16005,
                place_name = "Inya Lake Cafe",
                timestamp = "10:20:00 AM",
                date = "2026-11-16",
                recorded_at = baseTime + 20 * 60_000L
            ),
            // Maya then moves > 500m away to Junction City (going point #2)
            com.example.data.model.LocationHistoryItem(
                id = "p3",
                couple_id = "c1",
                user_name = "Maya",
                latitude = 16.8600,
                longitude = 96.1680,
                place_name = "Junction City",
                timestamp = "10:35:00 AM",
                date = "2026-11-16",
                recorded_at = baseTime + 35 * 60_000L
            )
        )

        val timeline = com.example.util.LocationHistoryAnalyzer.buildTimeline(items, currentUserName = "Alex")
        val alexNodes = timeline.filter { it.isCurrentUser }
        val mayaNodes = timeline.filter { !it.isCurrentUser }

        assertEquals(1, alexNodes.size)
        assertEquals(false, alexNodes.first().isStayPoint)
        assertEquals("Passing", alexNodes.first().stayDurationLabel)

        assertEquals(2, mayaNodes.size)
        val mayaStay = mayaNodes.first { it.placeName == "Inya Lake Cafe" }
        val mayaGoing = mayaNodes.first { it.placeName == "Junction City" }
        assertTrue(mayaStay.isStayPoint)
        assertEquals(2, mayaStay.pointCount)
        assertEquals("Stayed 15m", mayaStay.stayDurationLabel)
        assertEquals(1, mayaStay.stepIndex)

        assertEquals(false, mayaGoing.isStayPoint)
        assertEquals(2, mayaGoing.stepIndex)
    }
}
