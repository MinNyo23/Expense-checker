package com.example

import com.example.data.model.Couple
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
