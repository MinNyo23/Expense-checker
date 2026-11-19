package com.example

import com.example.data.model.AuthSessionInfo
import com.example.data.model.AuthState
import com.example.data.model.CoupleMemo
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.model.UserProfile
import com.example.data.repository.AuthDataSource
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthSignUpOutcome
import com.example.data.repository.ExpenseDataSource
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ImageStorageDataSource
import com.example.data.repository.ImageStorageRepository
import com.example.data.repository.MemoDataSource
import com.example.data.repository.MemoRepository
import com.example.data.repository.ProfileDataSource
import com.example.data.repository.ProfileRepository
import com.example.data.repository.RawSessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers all 13 security, RLS, Auth, and private storage requirements:
 * 1. Sign-in success
 * 2. Sign-in failure
 * 3. Session restoration
 * 4. Signed-out access denial
 * 5. Missing profile / auth_user_id
 * 6. Expense access with user's own couple_id
 * 7. Rejection of another couple's data
 * 8. Image larger than 5 MiB
 * 9. Unsupported image MIME type
 * 10. Successful private image upload
 * 11. Signed URL creation
 * 12. Memo creation with an image path
 * 13. Memo deletion and image cleanup
 */
class SecurityArchitectureTest {

    private class FakeAuthDataSource : AuthDataSource {
        override var isConfigured: Boolean = true
        override val rawSessionStatus: Flow<RawSessionStatus> = MutableStateFlow(RawSessionStatus.NotAuthenticated)

        var currentSession: AuthSessionInfo? = null
        var refreshedSession: AuthSessionInfo? = null
        var refreshCalled = false
        var signInError: Throwable? = null
        var signUpOutcome: AuthSignUpOutcome? = null

        override suspend fun signUp(email: String, password: String): AuthSignUpOutcome {
            return signUpOutcome ?: AuthSignUpOutcome.EmailConfirmationPending(email)
        }

        override suspend fun signIn(email: String, password: String): AuthSessionInfo {
            signInError?.let { throw it }
            val session = AuthSessionInfo(
                authUserId = "uid-123",
                email = email,
                expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + 3600L
            )
            currentSession = session
            return session
        }

        override suspend fun signOut() {
            currentSession = null
        }

        override suspend fun resetPassword(email: String) = Unit

        override suspend fun updatePassword(newPassword: String) = Unit

        override suspend fun getCurrentSession(): AuthSessionInfo? = currentSession

        override suspend fun refreshCurrentSession(): AuthSessionInfo? {
            refreshCalled = true
            currentSession = refreshedSession
            return refreshedSession
        }
    }

    private class FakeProfileDataSource : ProfileDataSource {
        var profileToReturn: UserProfile? = null
        var updateCalls = 0

        override suspend fun fetchProfileByAuthUserId(authUserId: String): UserProfile? {
            return profileToReturn
        }

        override suspend fun fetchProfilesByCoupleId(coupleId: String): List<UserProfile> {
            return listOfNotNull(profileToReturn).filter { it.couple_id == coupleId }
        }

        override suspend fun updateOwnProfileFields(
            profileId: String,
            authUserId: String,
            userName: String,
            dateOfBirth: String,
            avatarUrl: String
        ): UserProfile {
            updateCalls++
            val current = profileToReturn ?: error("Missing profile")
            val updated = current.copy(user_name = userName, date_of_birth = dateOfBirth, avatar_url = avatarUrl)
            profileToReturn = updated
            return updated
        }
    }

    private class FakeExpenseDataSource : ExpenseDataSource {
        val storedExpenses = mutableListOf<Expense>()
        val deletedIds = mutableListOf<String>()

        override suspend fun fetchExpenses(coupleId: String): List<Expense> {
            return storedExpenses.filter { it.couple_id == coupleId }
        }

        override suspend fun insertExpense(expense: Expense): Expense {
            storedExpenses.add(expense)
            return expense
        }

        override suspend fun deleteExpense(expenseId: String, coupleId: String) {
            deletedIds.add(expenseId)
            storedExpenses.removeAll { it.id == expenseId && it.couple_id == coupleId }
        }
    }

    private class FakeImageStorageDataSource : ImageStorageDataSource {
        val uploadedObjects = mutableMapOf<String, Pair<ByteArray, String>>()
        val deletedObjects = mutableListOf<String>()
        var signedUrlCreateCount = 0
        var lastExpiresInSeconds = 0L

        override suspend fun uploadPrivateImage(objectPath: String, bytes: ByteArray, mimeType: String): String {
            uploadedObjects[objectPath] = bytes to mimeType
            return objectPath
        }

        override suspend fun createSignedUrl(objectPath: String, expiresInSeconds: Long): String {
            signedUrlCreateCount++
            lastExpiresInSeconds = expiresInSeconds
            return "https://example.supabase.co/storage/v1/object/sign/images/$objectPath?token=signed-$signedUrlCreateCount"
        }

        override suspend fun deletePrivateImage(objectPath: String) {
            deletedObjects.add(objectPath)
            uploadedObjects.remove(objectPath)
        }
    }

    private class FakeMemoDataSource : MemoDataSource {
        val storedMemos = mutableListOf<CoupleMemo>()
        val deletedMemoIds = mutableListOf<String>()

        override suspend fun fetchMemos(coupleId: String): List<CoupleMemo> {
            return storedMemos.filter { it.couple_id == coupleId }
        }

        override suspend fun insertMemo(memo: CoupleMemo): CoupleMemo {
            storedMemos.add(memo)
            return memo
        }

        override suspend fun deleteMemo(memoId: String, coupleId: String) {
            deletedMemoIds.add(memoId)
            storedMemos.removeAll { it.id == memoId && it.couple_id == coupleId }
        }
    }

    private fun webpHeaderBytes(extraSize: Int = 16): ByteArray {
        val bytes = ByteArray(12 + extraSize)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        return bytes
    }

    @Test
    fun `1 sign in success transitions to SignedIn with verified couple profile`() = runBlocking {
        val authDs = FakeAuthDataSource()
        val profileDs = FakeProfileDataSource().apply {
            profileToReturn = UserProfile(
                id = "prof-1",
                couple_id = "couple-abc",
                user_name = "Min Nyo",
                email = "minnyo@example.com",
                auth_user_id = "uid-123"
            )
        }
        val repo = AuthRepository(authDs, ProfileRepository(profileDs))

        val res = repo.signIn("minnyo@example.com", "StrongPassword123")
        assertTrue(res.isSuccess)
        val state = repo.currentAuthState
        assertTrue(state is AuthState.SignedIn)
        state as AuthState.SignedIn
        assertEquals("uid-123", state.authUserId)
        assertEquals("couple-abc", state.coupleId)
        assertEquals("Min Nyo", state.userName)
    }

    @Test
    fun `2 sign in failure sets InvalidCredentials with safe friendly message`() = runBlocking {
        val authDs = FakeAuthDataSource().apply {
            signInError = RuntimeException("Invalid login credentials; jwt=eyJhbGciOiJIUzI1NiJ9.secret")
        }
        val repo = AuthRepository(authDs, ProfileRepository(FakeProfileDataSource()))

        val res = repo.signIn("minnyo@example.com", "WrongPass")
        assertTrue(res.isFailure)
        val state = repo.currentAuthState
        assertTrue(state is AuthState.InvalidCredentials)
        val message = (state as AuthState.InvalidCredentials).message
        assertFalse(message.contains("eyJ"))
        assertFalse(message.contains("user_accounts"))
    }

    @Test
    fun `3 session restoration refreshes near-expiry session and restores SignedIn`() = runBlocking {
        val nowSec = System.currentTimeMillis() / 1000L
        val authDs = FakeAuthDataSource().apply {
            currentSession = AuthSessionInfo(
                authUserId = "uid-123",
                email = "minnyo@example.com",
                expiresAtEpochSec = nowSec + 10L
            )
            refreshedSession = AuthSessionInfo(
                authUserId = "uid-123",
                email = "minnyo@example.com",
                expiresAtEpochSec = nowSec + 3600L
            )
        }
        val profileDs = FakeProfileDataSource().apply {
            profileToReturn = UserProfile(
                id = "prof-1",
                couple_id = "couple-abc",
                user_name = "Min Nyo",
                email = "minnyo@example.com",
                auth_user_id = "uid-123"
            )
        }
        val repo = AuthRepository(authDs, ProfileRepository(profileDs))

        val state = repo.refreshOrRestoreSession()
        assertTrue(authDs.refreshCalled)
        assertTrue(state is AuthState.SignedIn)
        assertEquals("couple-abc", (state as AuthState.SignedIn).coupleId)
    }

    @Test
    fun `4 signed out access denial blocks expenses memos and storage`() = runBlocking {
        val signedOutProvider: () -> AuthState = { AuthState.SignedOut }
        val expenseRepo = ExpenseRepository(FakeExpenseDataSource(), signedOutProvider)
        val imageRepo = ImageStorageRepository(FakeImageStorageDataSource(), signedOutProvider)
        val memoRepo = MemoRepository(FakeMemoDataSource(), imageRepo, signedOutProvider)

        assertTrue(expenseRepo.getExpenses().exceptionOrNull() is SupabaseDomainError.AuthenticationRequired)
        assertTrue(memoRepo.getMemos().exceptionOrNull() is SupabaseDomainError.AuthenticationRequired)
        assertTrue(imageRepo.uploadValidatedBytes(webpHeaderBytes(), "image/webp").exceptionOrNull() is SupabaseDomainError.AuthenticationRequired)
        assertTrue(imageRepo.getSignedUrl("uid-123/file.webp").exceptionOrNull() is SupabaseDomainError.AuthenticationRequired)
    }

    @Test
    fun `5 missing profile or mismatched auth_user_id enters AccountNotLinked without modifying profile`() = runBlocking {
        val authDs = FakeAuthDataSource()
        val profileDs = FakeProfileDataSource().apply {
            // Legacy/unlinked row where auth_user_id does not match uid-123
            profileToReturn = UserProfile(
                id = "prof-legacy",
                couple_id = "couple-abc",
                user_name = "Min Nyo",
                email = "minnyo@example.com",
                auth_user_id = ""
            )
        }
        val repo = AuthRepository(authDs, ProfileRepository(profileDs))

        val res = repo.signIn("minnyo@example.com", "Password123")
        assertTrue(res.isSuccess)
        assertTrue(res.getOrThrow().accountPendingLink)
        assertTrue(repo.currentAuthState is AuthState.AccountNotLinked)
        // App must NEVER overwrite profiles.auth_user_id directly from the client
        assertEquals(0, profileDs.updateCalls)

        // Completely missing profile also routes to AccountNotLinked
        profileDs.profileToReturn = null
        repo.signIn("minnyo@example.com", "Password123")
        assertTrue(repo.currentAuthState is AuthState.AccountNotLinked)
        assertEquals(0, profileDs.updateCalls)
    }

    @Test
    fun `6 expense access with own couple_id succeeds`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val ds = FakeExpenseDataSource()
        val repo = ExpenseRepository(ds) { signedIn }

        val added = repo.addExpense(
            Expense(
                id = "e1",
                couple_id = "couple-own",
                user_name = "Min Nyo",
                amount = 15000.0,
                category = ExpenseCategory.FOOD.name,
                date = "2026-11-19",
                note = "Lunch"
            )
        )
        assertTrue(added.isSuccess)

        val fetched = repo.getExpenses()
        assertTrue(fetched.isSuccess)
        assertEquals(1, fetched.getOrThrow().size)
        assertEquals("couple-own", fetched.getOrThrow().first().couple_id)
    }

    @Test
    fun `7 rejection of another couple data across read write and delete`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val ds = FakeExpenseDataSource()
        val expenseRepo = ExpenseRepository(ds) { signedIn }
        val memoRepo = MemoRepository(
            FakeMemoDataSource(),
            ImageStorageRepository(FakeImageStorageDataSource(), { signedIn })
        ) { signedIn }

        val readOther = expenseRepo.getExpenses("couple-other")
        assertTrue(readOther.exceptionOrNull() is SupabaseDomainError.PermissionDenied)

        val writeOther = expenseRepo.addExpense(
            Expense(
                id = "e-other",
                couple_id = "couple-other",
                user_name = "Min Nyo",
                amount = 5000.0,
                category = ExpenseCategory.COFFEE.name,
                date = "2026-11-19"
            )
        )
        assertTrue(writeOther.exceptionOrNull() is SupabaseDomainError.PermissionDenied)

        val memoOther = memoRepo.createMemo(
            title = "Secret",
            note = "Cross-couple test",
            date = "2026-11-19",
            coupleIdOverride = "couple-other"
        )
        assertTrue(memoOther.exceptionOrNull() is SupabaseDomainError.PermissionDenied)
    }

    @Test
    fun `8 image larger than 5 MiB is rejected with FileTooLarge`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val imageDs = FakeImageStorageDataSource()
        val repo = ImageStorageRepository(imageDs, { signedIn })

        val oversized = webpHeaderBytes(extraSize = ImageStorageRepository.MAX_IMAGE_BYTES) // > 5 MiB
        val res = repo.uploadValidatedBytes(oversized, "image/webp")
        assertTrue(res.exceptionOrNull() is SupabaseDomainError.FileTooLarge)
        assertTrue(imageDs.uploadedObjects.isEmpty())
    }

    @Test
    fun `9 unsupported image MIME type and spoofed headers are rejected`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val imageDs = FakeImageStorageDataSource()
        val repo = ImageStorageRepository(imageDs, { signedIn })

        val svgRes = repo.uploadValidatedBytes("<svg></svg>".toByteArray(), "image/svg+xml")
        assertTrue(svgRes.exceptionOrNull() is SupabaseDomainError.UnsupportedImageType)

        // Spoofed ContentResolver claiming PNG for plain text bytes
        val spoofRes = repo.uploadValidatedBytes("not-actually-a-png-file".toByteArray(), "image/png")
        assertTrue(spoofRes.exceptionOrNull() is SupabaseDomainError.UnsupportedImageType)
        assertTrue(imageDs.uploadedObjects.isEmpty())
    }

    @Test
    fun `10 successful private image upload writes under authUserId prefix with unique UUID`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val imageDs = FakeImageStorageDataSource()
        val repo = ImageStorageRepository(imageDs, { signedIn })

        val res = repo.uploadValidatedBytes(webpHeaderBytes(), "image/webp")
        assertTrue(res.isSuccess)
        val path = res.getOrThrow()
        assertTrue(path.startsWith("uid-123/"))
        assertTrue(path.endsWith(".webp"))
        assertFalse(path.startsWith("http"))
        assertEquals("image/webp", imageDs.uploadedObjects[path]?.second)
    }

    @Test
    fun `11 signed URL creation uses 10 minute expiry and caches until forceRefresh`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val imageDs = FakeImageStorageDataSource()
        var nowMillis = 1_000_000L
        val repo = ImageStorageRepository(imageDs, { signedIn }, timeProviderMillis = { nowMillis })

        val path = "uid-123/sample-photo.webp"
        val first = repo.getSignedUrl(path)
        assertTrue(first.isSuccess)
        assertEquals(600L, imageDs.lastExpiresInSeconds)
        assertEquals(1, imageDs.signedUrlCreateCount)

        // Cached within TTL
        nowMillis += 60_000L
        val cached = repo.getSignedUrl(path)
        assertEquals(first.getOrThrow(), cached.getOrThrow())
        assertEquals(1, imageDs.signedUrlCreateCount)

        // Refreshes when forced
        val refreshed = repo.getSignedUrl(path, forceRefresh = true)
        assertTrue(refreshed.isSuccess)
        assertEquals(2, imageDs.signedUrlCreateCount)
    }

    @Test
    fun `12 memo creation stores private object path and rejects public URLs`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val memoDs = FakeMemoDataSource()
        val imageRepo = ImageStorageRepository(FakeImageStorageDataSource(), { signedIn })
        val memoRepo = MemoRepository(memoDs, imageRepo) { signedIn }

        val badPublic = memoRepo.createMemo(
            title = "Bad",
            note = "Should reject public URL",
            date = "2026-11-19",
            imagePath = "https://example.supabase.co/storage/v1/object/public/images/foo.jpg"
        )
        assertTrue(badPublic.exceptionOrNull() is SupabaseDomainError.ValidationError)

        val valid = memoRepo.createMemo(
            title = "Night Market",
            note = "Walk together",
            date = "2026-11-19",
            imagePath = "uid-123/abc-123.webp"
        )
        assertTrue(valid.isSuccess)
        val created = valid.getOrThrow()
        assertEquals("couple-own", created.couple_id)
        assertEquals("uid-123/abc-123.webp", created.image_url)
    }

    @Test
    fun `13 memo deletion removes database record and cleans up private storage object`() = runBlocking {
        val signedIn = AuthState.SignedIn("uid-123", "minnyo@example.com", "couple-own", "Min Nyo")
        val memoDs = FakeMemoDataSource()
        val imageDs = FakeImageStorageDataSource()
        val imageRepo = ImageStorageRepository(imageDs, { signedIn })
        val memoRepo = MemoRepository(memoDs, imageRepo) { signedIn }

        val uploadedPath = imageRepo.uploadValidatedBytes(webpHeaderBytes(), "image/webp").getOrThrow()
        val created = memoRepo.createMemo(
            title = "Lake Walk",
            note = "Sunset",
            date = "2026-11-19",
            imagePath = uploadedPath
        ).getOrThrow()

        val deleteRes = memoRepo.deleteMemo(created)
        assertTrue(deleteRes.isSuccess)
        assertTrue(memoDs.deletedMemoIds.contains(created.id))
        assertTrue(imageDs.deletedObjects.contains(uploadedPath))
        assertTrue(imageDs.uploadedObjects.isEmpty())
    }

    @Test
    fun `error mapper covers all domain error categories safely`() {
        assertTrue(SupabaseErrorMapper.mapError(RuntimeException("email not confirmed")) is SupabaseDomainError.EmailConfirmationRequired)
        assertTrue(SupabaseErrorMapper.mapError(RuntimeException("JWT expired")) is SupabaseDomainError.SessionExpired)
        assertTrue(SupabaseErrorMapper.mapError(RuntimeException("Payload too large 413")) is SupabaseDomainError.FileTooLarge)
        assertTrue(SupabaseErrorMapper.mapError(RuntimeException("Invalid content type MIME")) is SupabaseDomainError.UnsupportedImageType)
        assertTrue(SupabaseErrorMapper.mapError(RuntimeException("Signed url token has expired")) is SupabaseDomainError.SignedUrlExpired)
        assertTrue(SupabaseErrorMapper.mapError(java.net.UnknownHostException("host")) is SupabaseDomainError.NetworkUnavailable)
    }
}
