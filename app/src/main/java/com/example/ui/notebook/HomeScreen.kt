package com.example.ui.notebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.InstituteProfile
import com.example.data.model.NOTE_SUBJECT_CATEGORIES
import com.example.data.model.Note
import com.example.data.repository.NotebookRepository
import com.example.ui.auth.signOutUser
import com.example.ui.theme.AmberGold
import com.example.ui.theme.NoteCardAccentBars
import com.example.ui.theme.NoteCardColorsLight
import com.example.ui.theme.OxfordNavy
import com.example.ui.theme.OxfordNavyDark
import com.example.util.PdfExportHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookAppScreen(
    currentUserId: String,
    userEmail: String?
) {
    val viewModel: NotebookViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NotebookViewModel(NotebookRepository(db), currentUserId)
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember { CredentialManager.create(context) }

    val notesUiState by viewModel.filteredNotesState.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val instituteProfile by viewModel.instituteProfile.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showPinnedOnly by viewModel.showPinnedOnly.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    var showInstituteProfileDialog by remember { mutableStateOf(false) }
    var notePendingPdfSaveAs by remember { mutableStateOf<Note?>(null) }

    // System "Save PDF As..." launcher if user wants to pick a custom location
    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val targetNote = notePendingPdfSaveAs
        if (uri != null && targetNote != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        PdfExportHelper.writeSingleNotePdf(out, targetNote, instituteProfile)
                    }
                }.onSuccess {
                    snackbarHostState.showSnackbar("PDF saved to selected folder!")
                }.onFailure {
                    snackbarHostState.showSnackbar("Could not write PDF to selected location.")
                }
            }
        }
        notePendingPdfSaveAs = null
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    val handleDownloadSingleNotePdf: (Note) -> Unit = { note ->
        scope.launch {
            val result = PdfExportHelper.downloadNoteAsPdf(context, note, instituteProfile)
            result.fold(
                onSuccess = { pdfExport ->
                    val snackResult = snackbarHostState.showSnackbar(
                        message = "PDF downloaded: ${pdfExport.fileName}",
                        actionLabel = "Open / Share",
                        duration = SnackbarDuration.Long
                    )
                    if (snackResult == SnackbarResult.ActionPerformed) {
                        PdfExportHelper.openOrSharePdf(context, pdfExport, preferShare = false)
                    }
                },
                onFailure = { err ->
                    snackbarHostState.showSnackbar("PDF export failed: ${err.localizedMessage ?: "Error"}")
                }
            )
        }
    }

    val handleDownloadAllNotesPdf: () -> Unit = {
        scope.launch {
            val result = PdfExportHelper.downloadAllNotesAsPdf(context, allNotes, instituteProfile)
            result.fold(
                onSuccess = { pdfExport ->
                    val snackResult = snackbarHostState.showSnackbar(
                        message = "Full Notebook PDF saved: ${pdfExport.fileName}",
                        actionLabel = "Open / Share",
                        duration = SnackbarDuration.Long
                    )
                    if (snackResult == SnackbarResult.ActionPerformed) {
                        PdfExportHelper.openOrSharePdf(context, pdfExport, preferShare = false)
                    }
                },
                onFailure = { err ->
                    snackbarHostState.showSnackbar("Notebook PDF export failed: ${err.localizedMessage ?: "Error"}")
                }
            )
        }
    }

    val handleShareNote: (Note) -> Unit = { note ->
        PdfExportHelper.shareNoteText(context, note, instituteProfile)
    }

    if (showInstituteProfileDialog) {
        InstituteProfileDialog(
            currentProfile = instituteProfile,
            onDismiss = { showInstituteProfileDialog = false },
            onSave = { updated ->
                viewModel.updateInstituteProfile(updated)
                showInstituteProfileDialog = false
            }
        )
    }

    if (editorState.isOpen) {
        NoteEditorScreen(
            editorState = editorState,
            onUpdateFields = { title, content, subject, courseCode, instituteName, colorIndex, isPinned ->
                viewModel.updateEditorFields(
                    title = title,
                    content = content,
                    subject = subject,
                    courseCode = courseCode,
                    instituteName = instituteName,
                    colorIndex = colorIndex,
                    isPinned = isPinned
                )
            },
            onToggleAutoSave = viewModel::toggleAutoSave,
            onManualSave = { viewModel.saveNoteManually(closeAfterSave = false) },
            onCloseWithAutoSave = viewModel::closeEditorWithAutoSave,
            onDownloadPdf = handleDownloadSingleNotePdf,
            onShareNote = handleShareNote,
            onDeleteNote = { id -> viewModel.deleteNote(id, closeEditorIfOpen = true) },
            currentNoteSnapshot = viewModel::currentEditorNoteSnapshot
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideNavigationDrawerSheet(
                instituteProfile = instituteProfile,
                userEmail = userEmail,
                totalNotesCount = allNotes.size,
                pinnedNotesCount = allNotes.count { it.isPinned },
                selectedCategory = selectedCategory,
                showPinnedOnly = showPinnedOnly,
                onSelectAllNotes = {
                    viewModel.onSelectCategory("All Notes")
                    scope.launch { drawerState.close() }
                },
                onSelectPinnedNotes = {
                    viewModel.onTogglePinnedFilter(true)
                    scope.launch { drawerState.close() }
                },
                onSelectCategory = { category ->
                    viewModel.onSelectCategory(category)
                    scope.launch { drawerState.close() }
                },
                onAddNewNote = {
                    scope.launch { drawerState.close() }
                    viewModel.openNewNoteEditor()
                },
                onEditInstituteProfile = {
                    scope.launch { drawerState.close() }
                    showInstituteProfileDialog = true
                },
                onExportAllPdf = {
                    scope.launch { drawerState.close() }
                    handleDownloadAllNotesPdf()
                },
                onSignOut = {
                    scope.launch { drawerState.close() }
                    signOutUser(credentialManager, scope)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier.testTag("side_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.side_menu_description)
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = instituteProfile.instituteName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::onToggleViewMode,
                            modifier = Modifier.testTag("toggle_view_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Switch list or grid view"
                            )
                        }

                        IconButton(
                            onClick = handleDownloadAllNotesPdf,
                            modifier = Modifier.testTag("top_export_all_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(R.string.export_all_pdf),
                                tint = AmberGold
                            )
                        }

                        IconButton(
                            onClick = viewModel::openNewNoteEditor,
                            modifier = Modifier.testTag("top_add_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_new_note),
                                tint = OxfordNavy
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openNewNoteEditor,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.add_new_note),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = OxfordNavy,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_note")
                )
            }
        ) { innerPadding ->
            val columns = if (isGridView) GridCells.Adaptive(minSize = 168.dp) else GridCells.Fixed(1)

            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("notes_list_view"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Institute Header Card on Home Screen
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InstituteHomeHeroCard(
                        profile = instituteProfile,
                        totalNotes = allNotes.size,
                        pinnedNotes = allNotes.count { it.isPinned },
                        onEditInstitute = { showInstituteProfileDialog = true },
                        onQuickAddNote = viewModel::openNewNoteEditor
                    )
                }

                // 2. Search Bar on Home Screen
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeSearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange
                    )
                }

                // 3. Category Filter Chips
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryFilterRow(
                        selectedCategory = selectedCategory,
                        showPinnedOnly = showPinnedOnly,
                        onSelectCategory = viewModel::onSelectCategory,
                        onTogglePinned = { viewModel.onTogglePinnedFilter(!showPinnedOnly) }
                    )
                }

                // 4. Notes List / Grid Content
                when (val state = notesUiState) {
                    is UiState.Loading -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = OxfordNavy)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyNotesView(
                                    isSearching = searchQuery.isNotBlank() || showPinnedOnly || selectedCategory != "All Notes",
                                    onAddNote = viewModel::openNewNoteEditor,
                                    onLoadSamples = viewModel::seedSampleAcademicNotes,
                                    onClearFilters = {
                                        viewModel.onSearchQueryChange("")
                                        viewModel.onSelectCategory("All Notes")
                                    }
                                )
                            }
                        } else {
                            items(
                                items = state.data,
                                key = { note -> note.id }
                            ) { note ->
                                NoteItemCard(
                                    note = note,
                                    onOpenNote = { viewModel.openExistingNoteEditor(note) },
                                    onTogglePin = { viewModel.toggleNotePinnedFromList(note) },
                                    onDownloadPdf = { handleDownloadSingleNotePdf(note) },
                                    onSavePdfAs = {
                                        notePendingPdfSaveAs = note
                                        createPdfDocumentLauncher.launch(
                                            PdfExportHelper.sanitizeFileName(note.title)
                                        )
                                    },
                                    onShareNote = { handleShareNote(note) },
                                    onDeleteNote = { viewModel.deleteNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstituteHomeHeroCard(
    profile: InstituteProfile,
    totalNotes: Int,
    pinnedNotes: Int,
    onEditInstitute: () -> Unit,
    onQuickAddNote: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("institute_home_card"),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_institute_banner_1794500987219),
                contentDescription = "Institute Notebook Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                OxfordNavyDark.copy(alpha = 0.92f),
                                OxfordNavy.copy(alpha = 0.80f),
                                OxfordNavyDark.copy(alpha = 0.68f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = AmberGold,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = profile.academicSession,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = onEditInstitute,
                        modifier = Modifier.testTag("edit_institute_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFDE68A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Institute Info",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Institute",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = profile.instituteName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${profile.department} • ${profile.studentOrFacultyName} (${profile.rollOrIdNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadgePill(label = "Notes", value = totalNotes.toString())
                        StatBadgePill(label = "Pinned", value = pinnedNotes.toString())
                    }

                    Button(
                        onClick = onQuickAddNote,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGold,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("hero_add_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Note",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadgePill(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFDE68A),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HomeSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_notes_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search notes",
                tint = OxfordNavy
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_bar"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = OxfordNavy,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: String,
    showPinnedOnly: Boolean,
    onSelectCategory: (String) -> Unit,
    onTogglePinned: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = showPinnedOnly,
            onClick = onTogglePinned,
            label = { Text("Pinned") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AmberGold,
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White
            )
        )

        NOTE_SUBJECT_CATEGORIES.forEach { category ->
            val isSelected = !showPinnedOnly && selectedCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OxfordNavy,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun NoteItemCard(
    note: Note,
    onOpenNote: () -> Unit,
    onTogglePin: () -> Unit,
    onDownloadPdf: () -> Unit,
    onSavePdfAs: () -> Unit,
    onShareNote: () -> Unit,
    onDeleteNote: () -> Unit
) {
    val accentColor = NoteCardAccentBars.getOrElse(note.colorIndex) { OxfordNavy }
    val cardBackground = NoteCardColorsLight.getOrElse(note.colorIndex) { Color.White }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenNote)
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Accent Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Subject Badge + Course Code + Pin Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = note.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (note.courseCode.isNotBlank()) {
                            Surface(
                                color = Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = note.courseCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.pin_note),
                            tint = if (note.isPinned) AmberGold else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Note Title
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Note Content Preview
                Text(
                    text = note.content.ifBlank { "Tap to edit and add content to this note…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Institute + Timestamp Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.instituteName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = PdfExportHelper.formatTimestamp(note.updatedAt ?: note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(6.dp))

                // Action Bar: Edit, Download PDF, Save PDF As, Share, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onOpenNote,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("card_edit_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Edit Note",
                            tint = OxfordNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = OxfordNavy
                        )
                    }

                    TextButton(
                        onClick = onDownloadPdf,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("card_pdf_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.download_pdf),
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF92400E)
                        )
                    }

                    IconButton(
                        onClick = onSavePdfAs,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("card_save_pdf_as_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save PDF to custom folder",
                            tint = OxfordNavy,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onShareNote,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("card_share_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_note),
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteNote,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("card_delete_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.delete_note),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesView(
    isSearching: Boolean,
    onAddNote: () -> Unit,
    onLoadSamples: () -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Search else Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (isSearching) R.string.no_search_results_title else R.string.empty_notes_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    if (isSearching) R.string.no_search_results_subtitle else R.string.empty_notes_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isSearching) {
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Search & Filters")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddNote,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OxfordNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Note")
                    }

                    OutlinedButton(
                        onClick = onLoadSamples,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("load_sample_notes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AmberGold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sample Notes")
                    }
                }
            }
        }
    }
}

@Composable
private fun SideNavigationDrawerSheet(
    instituteProfile: InstituteProfile,
    userEmail: String?,
    totalNotesCount: Int,
    pinnedNotesCount: Int,
    selectedCategory: String,
    showPinnedOnly: Boolean,
    onSelectAllNotes: () -> Unit,
    onSelectPinnedNotes: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onAddNewNote: () -> Unit,
    onEditInstituteProfile: () -> Unit,
    onExportAllPdf: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(310.dp)
            .fillMaxHeight()
            .testTag("side_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Drawer Institute Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(OxfordNavyDark, OxfordNavy)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = AmberGold
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        TextButton(
                            onClick = onEditInstituteProfile,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFDE68A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Profile")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = instituteProfile.instituteName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = instituteProfile.department,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "${instituteProfile.studentOrFacultyName} • ${instituteProfile.academicSession}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFDE68A)
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Actions inside Side Menu
            NavigationDrawerItem(
                label = { Text("Add New Note", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = onAddNewNote,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = OxfordNavy
                    )
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_add_note_item")
            )

            NavigationDrawerItem(
                label = { Text("All Institute Notes") },
                selected = !showPinnedOnly && selectedCategory == "All Notes",
                onClick = onSelectAllNotes,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null
                    )
                },
                badge = {
                    Badge(containerColor = OxfordNavy, contentColor = Color.White) {
                        Text(totalNotesCount.toString())
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                label = { Text("Pinned Notes") },
                selected = showPinnedOnly,
                onClick = onSelectPinnedNotes,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null
                    )
                },
                badge = {
                    Badge(containerColor = AmberGold, contentColor = Color.White) {
                        Text(pinnedNotesCount.toString())
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

            Text(
                text = "SUBJECTS & CATEGORIES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            NOTE_SUBJECT_CATEGORIES.filter { it != "All Notes" }.forEach { category ->
                NavigationDrawerItem(
                    label = { Text(category) },
                    selected = !showPinnedOnly && selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

            Text(
                text = "INSTITUTE TOOLS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            NavigationDrawerItem(
                label = { Text("Download Full Notebook PDF") },
                selected = false,
                onClick = onExportAllPdf,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = AmberGold
                    )
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_export_pdf_item")
            )

            NavigationDrawerItem(
                label = { Text("Edit Institute Profile") },
                selected = false,
                onClick = onEditInstituteProfile,
                icon = {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.sign_out)) },
                selected = false,
                onClick = onSignOut,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_sign_out_item")
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InstituteProfileDialog(
    currentProfile: InstituteProfile,
    onDismiss: () -> Unit,
    onSave: (InstituteProfile) -> Unit
) {
    var instituteName by remember { mutableStateOf(currentProfile.instituteName) }
    var department by remember { mutableStateOf(currentProfile.department) }
    var academicSession by remember { mutableStateOf(currentProfile.academicSession) }
    var studentOrFacultyName by remember { mutableStateOf(currentProfile.studentOrFacultyName) }
    var rollOrIdNumber by remember { mutableStateOf(currentProfile.rollOrIdNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Institute Notebook Details",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = instituteName,
                    onValueChange = { instituteName = it },
                    label = { Text("Institute / University Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department / Faculty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = academicSession,
                    onValueChange = { academicSession = it },
                    label = { Text("Academic Session / Semester") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = studentOrFacultyName,
                    onValueChange = { studentOrFacultyName = it },
                    label = { Text("Scholar / Student Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rollOrIdNumber,
                    onValueChange = { rollOrIdNumber = it },
                    label = { Text("Roll / ID Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentProfile.copy(
                            instituteName = instituteName,
                            department = department,
                            academicSession = academicSession,
                            studentOrFacultyName = studentOrFacultyName,
                            rollOrIdNumber = rollOrIdNumber
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OxfordNavy,
                    contentColor = Color.White
                )
            ) {
                Text("Save Institute Info")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
