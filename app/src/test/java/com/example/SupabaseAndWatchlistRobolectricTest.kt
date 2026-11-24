package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CouplePreferences
import com.example.data.model.AuthState
import com.example.data.model.UserProfile
import com.example.data.repository.ProfileDataSource
import com.example.data.repository.ProfileRepository
import com.example.data.repository.RawSessionStatus
import com.example.data.repository.SupabaseAuthRepository
import com.example.data.supabase.SupabaseClientProvider
import com.example.ui.viewmodel.CoupleExpenseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseAndWatchlistRobolectricTest {

    @Test
    fun `supabase client provider blocks service role keys and exposes auth repository`() = runBlocking {
        val provider = SupabaseClientProvider(
            rawUrl = "https://example.supabase.co",
            rawAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.signature"
        )
        assertTrue(provider.isConfigured)
        assertFalse(
            SupabaseClientProvider(
                rawUrl = "https://example.supabase.co",
                rawAnonKey = "sb_secret_12345"
            ).isConfigured
        )

        val fakeProfileRepo = ProfileRepository(object : ProfileDataSource {
            override suspend fun fetchProfileByAuthUserId(authUserId: String): UserProfile? = null
            override suspend fun fetchProfilesByCoupleId(coupleId: String): List<UserProfile> = emptyList()
            override suspend fun updateOwnProfileFields(
                profileId: String,
                authUserId: String,
                userName: String,
                dateOfBirth: String,
                avatarUrl: String
            ): UserProfile = error("unused")
        })
        val authRepo = SupabaseAuthRepository(
            clientProvider = SupabaseClientProvider("", ""),
            profileRepository = fakeProfileRepo,
            autoObserveSdkSession = false
        )
        assertFalse(authRepo.hasActiveSdkSession())
        assertTrue(authRepo.observeUiAuthState().value is AuthState.SignedOut)
        assertEquals(RawSessionStatus.NotAuthenticated, authRepo.observeSessionStatus().first())
    }

    @Test
    fun `movie watchlist adds movies to watch later, moves watched movies to finished list, and persists`() = runBlocking(Dispatchers.IO) {
        withTimeout(10_000) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefs = CouplePreferences(app)
            val vm = CoupleExpenseViewModel(app)
            try {
                vm.addMovieToWatchLater("Past Lives", "Netflix")
                vm.addMovieToWatchLater("Interstellar", "IMAX rewatch")

                val initial = vm.movies.value
                assertEquals(2, initial.size)
                assertTrue(initial.all { !it.isWatched })
                val pastLives = initial.first { it.title == "Past Lives" }

                // Move watched movie to Finished list
                vm.markMovieAsWatched(pastLives.id)
                val afterWatched = vm.movies.value
                val finished = afterWatched.filter { it.isWatched }
                val watchLater = afterWatched.filter { !it.isWatched }
                assertEquals(1, finished.size)
                assertEquals("Past Lives", finished.first().title)
                assertTrue(finished.first().watchedAt.isNotBlank())
                assertEquals(1, watchLater.size)
                assertEquals("Interstellar", watchLater.first().title)

                // Move back to Watch Later
                vm.moveMovieToWatchLater(pastLives.id)
                val afterRequeue = vm.movies.value
                assertEquals(2, afterRequeue.count { !it.isWatched })
                assertEquals(0, afterRequeue.count { it.isWatched })

                // Delete movie
                vm.deleteMovie(pastLives.id)
                val afterDelete = vm.movies.value
                assertEquals(1, afterDelete.size)
                assertEquals("Interstellar", afterDelete.first().title)
            } finally {
                prefs.clearSession(clearProfileDetails = true)
            }
        }
    }
}
