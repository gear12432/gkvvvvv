package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.InstituteProfile
import com.example.data.model.Note
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotebookRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeNotes(explicitUserId: String? = null): Flow<List<Note>> = flow {
        val uid = explicitUserId ?: auth.currentUser?.uid ?: "unauthenticated_uid"
        val path = "users/$uid/notes"
        emitAll(
            db.collection("users").document(uid).collection("notes")
                .snapshots()
                .map { snapshot ->
                    val notes = snapshot.toObjects(
                        Note::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    notes.sortedWith(
                        compareByDescending<Note> { it.isPinned }
                            .thenByDescending {
                                (it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)).seconds
                            }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeInstituteProfile(explicitUserId: String? = null): Flow<InstituteProfile?> = flow {
        val uid = explicitUserId ?: auth.currentUser?.uid ?: "unauthenticated_uid"
        val path = "users/$uid/profile/institute"
        emitAll(
            db.collection("users").document(uid).collection("profile").document("institute")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            InstituteProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserNotes(): Result<List<Note>> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid/notes"
        try {
            val snapshot = db.collection("users").document(uid).collection("notes").get().await()
            snapshot.toObjects(Note::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getNoteById(noteId: String, ownerUserId: String? = null): Result<Note?> = runCatching {
        val uid = ownerUserId ?: requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                snapshot.toObject(Note::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createNote(
        title: String,
        content: String,
        subject: String = "Lecture Notes",
        courseCode: String = "",
        instituteName: String = "Oxford Institute of Science & Technology",
        colorIndex: Int = 0,
        isPinned: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document()
        val safeTitle = title.trim().ifBlank { "Untitled Note" }.take(200)
        val safeContent = content.take(20000)
        val safeSubject = subject.trim().ifBlank { "Lecture Notes" }.take(80)
        val safeCourseCode = courseCode.trim().take(40)
        val safeInstitute = instituteName.trim().ifBlank { "Oxford Institute of Science & Technology" }.take(120)
        val safeColor = colorIndex.coerceIn(0, 5)

        val payload = mapOf(
            "userId" to uid,
            "title" to safeTitle,
            "content" to safeContent,
            "subject" to safeSubject,
            "courseCode" to safeCourseCode,
            "instituteName" to safeInstitute,
            "colorIndex" to safeColor,
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateNote(note: Note): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(note.id.isNotBlank()) { "Note ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("notes").document(note.id)
        val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.take(200)
        val safeContent = note.content.take(20000)
        val safeSubject = note.subject.trim().ifBlank { "Lecture Notes" }.take(80)
        val safeCourseCode = note.courseCode.trim().take(40)
        val safeInstitute = note.instituteName.trim().ifBlank { "Oxford Institute of Science & Technology" }.take(120)
        val safeColor = note.colorIndex.coerceIn(0, 5)

        val updates = mapOf(
            "title" to safeTitle,
            "content" to safeContent,
            "subject" to safeSubject,
            "courseCode" to safeCourseCode,
            "instituteName" to safeInstitute,
            "colorIndex" to safeColor,
            "isPinned" to note.isPinned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(noteId.isNotBlank()) { "Note ID must not be blank for delete" }
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun saveInstituteProfile(profile: InstituteProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document("institute")
        val payload = mapOf(
            "userId" to uid,
            "instituteName" to profile.instituteName.trim().ifBlank { "Oxford Institute of Science & Technology" }.take(120),
            "department" to profile.department.trim().ifBlank { "Department of Computer Science" }.take(100),
            "academicSession" to profile.academicSession.trim().ifBlank { "2026 - 2027" }.take(60),
            "studentOrFacultyName" to profile.studentOrFacultyName.trim().ifBlank { "Academic Scholar" }.take(100),
            "rollOrIdNumber" to profile.rollOrIdNumber.trim().take(60),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }
}
