package com.example.ui.notebook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.InstituteProfile
import com.example.data.model.Note
import com.example.data.repository.NotebookRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object AutoSaving : SaveStatus
    data class Saved(val label: String = "All changes auto-saved") : SaveStatus
    data class Error(val error: String) : SaveStatus
}

data class NoteEditorUiState(
    val isOpen: Boolean = false,
    val noteId: String = "",
    val title: String = "",
    val content: String = "",
    val subject: String = "Lecture Notes",
    val courseCode: String = "",
    val instituteName: String = "Oxford Institute of Science & Technology",
    val colorIndex: Int = 0,
    val isPinned: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val isDirty: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.Idle
)

class NotebookViewModel(
    private val repository: NotebookRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All Notes")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _showPinnedOnly = MutableStateFlow(false)
    val showPinnedOnly: StateFlow<Boolean> = _showPinnedOnly.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _editorState = MutableStateFlow(NoteEditorUiState())
    val editorState: StateFlow<NoteEditorUiState> = _editorState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var autoSaveJob: Job? = null
    private var isCreatingDocument = false

    val instituteProfile: StateFlow<InstituteProfile> = repository.observeInstituteProfile(currentUserId)
        .map { profile ->
            profile ?: InstituteProfile(userId = currentUserId)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing institute profile", firestoreError ?: error)
            emit(InstituteProfile(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = InstituteProfile(userId = currentUserId)
        )

    private val rawNotesState: StateFlow<UiState<List<Note>>> = repository.observeNotes(currentUserId)
        .map<List<Note>, UiState<List<Note>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing notes", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load notes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val filteredNotesState: StateFlow<UiState<List<Note>>> = combine(
        rawNotesState,
        _searchQuery,
        _selectedCategory,
        _showPinnedOnly
    ) { state, query, category, pinnedOnly ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val trimmedQuery = query.trim().lowercase()
                val filtered = state.data.filter { note ->
                    val matchesPinned = !pinnedOnly || note.isPinned
                    val matchesCategory = category == "All Notes" ||
                        note.subject.equals(category, ignoreCase = true)
                    val matchesQuery = trimmedQuery.isEmpty() ||
                        note.title.lowercase().contains(trimmedQuery) ||
                        note.content.lowercase().contains(trimmedQuery) ||
                        note.subject.lowercase().contains(trimmedQuery) ||
                        note.courseCode.lowercase().contains(trimmedQuery) ||
                        note.instituteName.lowercase().contains(trimmedQuery)
                    matchesPinned && matchesCategory && matchesQuery
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    val allNotes: StateFlow<List<Note>> = rawNotesState
        .map { (it as? UiState.Success)?.data ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSelectCategory(category: String) {
        _showPinnedOnly.value = false
        _selectedCategory.value = category
    }

    fun onTogglePinnedFilter(pinnedOnly: Boolean) {
        _showPinnedOnly.value = pinnedOnly
        if (pinnedOnly) {
            _selectedCategory.value = "All Notes"
        }
    }

    fun onToggleViewMode() {
        _isGridView.update { !it }
    }

    fun openNewNoteEditor() {
        autoSaveJob?.cancel()
        val currentInstitute = instituteProfile.value.instituteName
        val defaultSubject = if (_selectedCategory.value != "All Notes") {
            _selectedCategory.value
        } else {
            "Lecture Notes"
        }
        _editorState.value = NoteEditorUiState(
            isOpen = true,
            noteId = "",
            title = "",
            content = "",
            subject = defaultSubject,
            courseCode = "",
            instituteName = currentInstitute,
            colorIndex = 0,
            isPinned = false,
            autoSaveEnabled = true,
            isDirty = false,
            saveStatus = SaveStatus.Idle
        )
    }

    fun openExistingNoteEditor(note: Note) {
        autoSaveJob?.cancel()
        _editorState.value = NoteEditorUiState(
            isOpen = true,
            noteId = note.id,
            title = note.title,
            content = note.content,
            subject = note.subject,
            courseCode = note.courseCode,
            instituteName = note.instituteName.ifBlank { instituteProfile.value.instituteName },
            colorIndex = note.colorIndex,
            isPinned = note.isPinned,
            autoSaveEnabled = true,
            isDirty = false,
            saveStatus = SaveStatus.Saved("Loaded from cloud")
        )
    }

    fun updateEditorFields(
        title: String = _editorState.value.title,
        content: String = _editorState.value.content,
        subject: String = _editorState.value.subject,
        courseCode: String = _editorState.value.courseCode,
        instituteName: String = _editorState.value.instituteName,
        colorIndex: Int = _editorState.value.colorIndex,
        isPinned: Boolean = _editorState.value.isPinned
    ) {
        _editorState.update {
            it.copy(
                title = title,
                content = content,
                subject = subject,
                courseCode = courseCode,
                instituteName = instituteName,
                colorIndex = colorIndex,
                isPinned = isPinned,
                isDirty = true,
                saveStatus = if (it.autoSaveEnabled) SaveStatus.AutoSaving else SaveStatus.Idle
            )
        }
        if (_editorState.value.autoSaveEnabled) {
            scheduleAutoSave()
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        _editorState.update { it.copy(autoSaveEnabled = enabled) }
        if (enabled && _editorState.value.isDirty) {
            scheduleAutoSave()
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(700L)
            performSaveInternal(isManualSave = false)
        }
    }

    fun saveNoteManually(closeAfterSave: Boolean = false) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val saved = performSaveInternal(isManualSave = true)
            if (saved) {
                _snackbarEvents.tryEmit("Note saved successfully!")
                if (closeAfterSave) {
                    _editorState.update { it.copy(isOpen = false) }
                }
            }
        }
    }

    fun closeEditorWithAutoSave() {
        autoSaveJob?.cancel()
        val state = _editorState.value
        val hasAnyInput = state.title.isNotBlank() || state.content.isNotBlank() || state.noteId.isNotBlank()
        if (state.isDirty && hasAnyInput) {
            viewModelScope.launch {
                performSaveInternal(isManualSave = false)
                _editorState.update { it.copy(isOpen = false) }
                _snackbarEvents.tryEmit("Note auto-saved.")
            }
        } else {
            _editorState.update { it.copy(isOpen = false) }
        }
    }

    private suspend fun performSaveInternal(isManualSave: Boolean): Boolean {
        val current = _editorState.value
        if (!isManualSave && current.title.isBlank() && current.content.isBlank() && current.noteId.isBlank()) {
            return false
        }
        if (isCreatingDocument) return false

        _editorState.update { it.copy(saveStatus = SaveStatus.AutoSaving) }

        return if (current.noteId.isBlank()) {
            isCreatingDocument = true
            val result = repository.createNote(
                title = current.title.ifBlank {
                    current.content.lineSequence().firstOrNull()?.take(40)?.ifBlank { "Untitled Note" }
                        ?: "Untitled Note"
                },
                content = current.content,
                subject = current.subject,
                courseCode = current.courseCode,
                instituteName = current.instituteName,
                colorIndex = current.colorIndex,
                isPinned = current.isPinned
            )
            isCreatingDocument = false
            result.fold(
                onSuccess = { newId ->
                    _editorState.update {
                        it.copy(
                            noteId = newId,
                            isDirty = false,
                            saveStatus = SaveStatus.Saved(
                                if (isManualSave) "Saved manually just now" else "Auto-saved just now"
                            )
                        )
                    }
                    true
                },
                onFailure = { err ->
                    _editorState.update {
                        it.copy(saveStatus = SaveStatus.Error(err.localizedMessage ?: "Save failed"))
                    }
                    false
                }
            )
        } else {
            val noteToUpdate = Note(
                id = current.noteId,
                userId = currentUserId,
                title = current.title.ifBlank { "Untitled Note" },
                content = current.content,
                subject = current.subject,
                courseCode = current.courseCode,
                instituteName = current.instituteName,
                colorIndex = current.colorIndex,
                isPinned = current.isPinned
            )
            val result = repository.updateNote(noteToUpdate)
            result.fold(
                onSuccess = {
                    _editorState.update {
                        it.copy(
                            isDirty = false,
                            saveStatus = SaveStatus.Saved(
                                if (isManualSave) "Saved manually just now" else "Auto-saved just now"
                            )
                        )
                    }
                    true
                },
                onFailure = { err ->
                    _editorState.update {
                        it.copy(saveStatus = SaveStatus.Error(err.localizedMessage ?: "Update failed"))
                    }
                    false
                }
            )
        }
    }

    fun toggleNotePinnedFromList(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
                .onSuccess {
                    val msg = if (!note.isPinned) "Note pinned to top" else "Note unpinned"
                    _snackbarEvents.tryEmit(msg)
                }
        }
    }

    fun deleteNote(noteId: String, closeEditorIfOpen: Boolean = false) {
        if (noteId.isBlank()) {
            if (closeEditorIfOpen) {
                _editorState.update { it.copy(isOpen = false) }
            }
            return
        }
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onSuccess {
                    if (closeEditorIfOpen) {
                        _editorState.update { it.copy(isOpen = false) }
                    }
                    _snackbarEvents.tryEmit("Note deleted.")
                }
                .onFailure {
                    _snackbarEvents.tryEmit("Failed to delete note.")
                }
        }
    }

    fun updateInstituteProfile(profile: InstituteProfile) {
        viewModelScope.launch {
            repository.saveInstituteProfile(profile)
                .onSuccess {
                    _snackbarEvents.tryEmit("Institute profile updated!")
                }
                .onFailure {
                    _snackbarEvents.tryEmit("Failed to update institute profile.")
                }
        }
    }

    fun seedSampleAcademicNotes() {
        viewModelScope.launch {
            val institute = instituteProfile.value.instituteName
            val samples = listOf(
                Note(
                    title = "Algorithms & Complexity — Lecture 01",
                    content = "1. Asymptotic Notation:\n• Big-O (Upper Bound): f(n) <= c·g(n) for all n >= n0.\n• Omega (Lower Bound) and Theta (Tight Bound).\n\n2. Divide and Conquer Master Theorem:\nT(n) = a·T(n/b) + f(n)\n• Merge Sort runs in O(n log n) time complexity across worst, average, and best cases.\n\n3. Key Action Items for Next Seminar:\n• Complete problem set #1 on recurrence relations before Friday.",
                    subject = "Lecture Notes",
                    courseCode = "CS-301",
                    instituteName = institute,
                    colorIndex = 1,
                    isPinned = true
                ),
                Note(
                    title = "Quantum Optics Laboratory Report",
                    content = "Objective:\nMeasure the wavelength of a He-Ne laser using a Michelson Interferometer.\n\nApparatus:\n• He-Ne Laser Source (632.8 nm)\n• Beam Splitter & Compensator Plate\n• Micrometer Adjustable Mirror\n\nObservations:\nFringe shift count N = 100 fringes observed for mirror displacement d = 0.0316 mm.\nCalculated wavelength matches theoretical value within 0.4% experimental error.",
                    subject = "Lab Reports",
                    courseCode = "PHY-204",
                    instituteName = institute,
                    colorIndex = 2,
                    isPinned = false
                ),
                Note(
                    title = "Midterm Exam Preparation Checklist",
                    content = "Key Topics to Revise:\n✓ Relational Database Normalization (1NF, 2NF, 3NF, BCNF)\n✓ ACID Properties & Serializable Transactions\n✓ B+ Tree Indexing & Query Optimization\n\nPractice Papers:\n• Solve 2024 & 2025 Institute past papers.\n• Review SQL window functions and indexing strategies.",
                    subject = "Exam Prep",
                    courseCode = "CS-208",
                    instituteName = institute,
                    colorIndex = 3,
                    isPinned = false
                )
            )
            samples.forEach { sample ->
                repository.createNote(
                    title = sample.title,
                    content = sample.content,
                    subject = sample.subject,
                    courseCode = sample.courseCode,
                    instituteName = sample.instituteName,
                    colorIndex = sample.colorIndex,
                    isPinned = sample.isPinned
                )
            }
            _snackbarEvents.tryEmit("Added 3 sample institute notes!")
        }
    }

    fun currentEditorNoteSnapshot(): Note {
        val s = _editorState.value
        return Note(
            id = s.noteId,
            userId = currentUserId,
            title = s.title.ifBlank { "Untitled Note" },
            content = s.content,
            subject = s.subject,
            courseCode = s.courseCode,
            instituteName = s.instituteName.ifBlank { instituteProfile.value.instituteName },
            colorIndex = s.colorIndex,
            isPinned = s.isPinned
        )
    }

    private companion object {
        const val TAG = "NotebookViewModel"
    }
}
