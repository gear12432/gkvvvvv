package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.InstituteProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotebookRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndUpdateNote_validPayload_succeedsAndEmitsUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NotebookRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Data Structures & Algorithms",
                content = "Binary search trees, AVL rotations, and graph traversal.",
                subject = "Lecture Notes",
                courseCode = "CS-201",
                instituteName = "Oxford Institute of Science & Technology",
                colorIndex = 2,
                isPinned = true
            )
        }
        assertTrue(createResult.isSuccess)
        val noteId = createResult.getOrThrow()
        assertTrue(noteId.isNotBlank())

        val notes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes(uid).first { list -> list.any { it.id == noteId } }
        }
        val createdNote = notes.first { it.id == noteId }
        assertEquals("Data Structures & Algorithms", createdNote.title)
        assertTrue(createdNote.isPinned)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateNote(
                createdNote.copy(
                    title = "Updated Data Structures & Algorithms",
                    content = "Added Dijkstra shortest path algorithm."
                )
            )
        }
        assertTrue(updateResult.isSuccess)
    }

    @Test
    fun getNoteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NotebookRepository(firestore, auth)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createNote(
                title = "Alice Private Note",
                content = "Confidential exam review notes",
                subject = "Exam Prep"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NotebookRepository(firestore, auth)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getNoteById(noteId, ownerUserId = aliceUid)
        }
        assertTrue(bobReadResult.isFailure)
        val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = NotebookRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeNotes(aliceUid).first()
            }
            fail("Expected exception for unauthenticated user")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun saveAndObserveInstituteProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NotebookRepository(firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveInstituteProfile(
                InstituteProfile(
                    instituteName = "Cambridge Institute of Engineering",
                    department = "Department of Electrical Engineering",
                    academicSession = "2026 - 2027",
                    studentOrFacultyName = "Dr. Evelyn Vance",
                    rollOrIdNumber = "FAC-8821"
                )
            )
        }
        assertTrue(saveResult.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeInstituteProfile(uid).first { it != null }
        }
        assertEquals("Cambridge Institute of Engineering", profile?.instituteName)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@notebook.test"
        const val BOB_EMAIL = "bob@notebook.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
