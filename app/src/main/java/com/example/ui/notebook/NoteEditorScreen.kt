package com.example.ui.notebook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.NOTE_SUBJECT_CATEGORIES
import com.example.data.model.Note
import com.example.ui.theme.AmberGold
import com.example.ui.theme.NoteCardAccentBars
import com.example.ui.theme.NoteCardColorsLight
import com.example.ui.theme.OxfordNavy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    editorState: NoteEditorUiState,
    onUpdateFields: (
        title: String,
        content: String,
        subject: String,
        courseCode: String,
        instituteName: String,
        colorIndex: Int,
        isPinned: Boolean
    ) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onManualSave: () -> Unit,
    onCloseWithAutoSave: () -> Unit,
    onDownloadPdf: (Note) -> Unit,
    onShareNote: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    currentNoteSnapshot: () -> Note
) {
    // Mandatory BackHandler so system back gesture auto-saves and returns to Home screen
    BackHandler {
        onCloseWithAutoSave()
    }

    val accentColor = NoteCardAccentBars.getOrElse(editorState.colorIndex) { OxfordNavy }
    val paperTint = NoteCardColorsLight.getOrElse(editorState.colorIndex) { Color.White }
    val wordCount = editorState.content.trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (editorState.noteId.isBlank()) "New Institute Note" else "Edit Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedContent(
                            targetState = editorState.saveStatus,
                            label = "save_status_anim"
                        ) { status ->
                            val statusText = when (status) {
                                is SaveStatus.Idle -> if (editorState.autoSaveEnabled) "Auto-save ready" else "Manual save mode"
                                is SaveStatus.AutoSaving -> "Auto-saving changes…"
                                is SaveStatus.Saved -> status.label
                                is SaveStatus.Error -> status.error
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (status is SaveStatus.AutoSaving) Icons.Default.Sync else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (status is SaveStatus.Error) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCloseWithAutoSave,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and auto-save"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onUpdateFields(
                                editorState.title,
                                editorState.content,
                                editorState.subject,
                                editorState.courseCode,
                                editorState.instituteName,
                                editorState.colorIndex,
                                !editorState.isPinned
                            )
                        },
                        modifier = Modifier.testTag("editor_pin_button")
                    ) {
                        Icon(
                            imageVector = if (editorState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin note",
                            tint = if (editorState.isPinned) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { onDownloadPdf(currentNoteSnapshot()) },
                        modifier = Modifier.testTag("editor_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Download Note as PDF",
                            tint = OxfordNavy
                        )
                    }

                    IconButton(
                        onClick = { onShareNote(currentNoteSnapshot()) },
                        modifier = Modifier.testTag("editor_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Note"
                        )
                    }

                    Button(
                        onClick = onManualSave,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_note_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OxfordNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onManualSave,
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("bottom_save_note_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OxfordNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Note")
                    }

                    OutlinedButton(
                        onClick = { onDownloadPdf(currentNoteSnapshot()) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bottom_download_pdf_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AmberGold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF")
                    }

                    OutlinedButton(
                        onClick = { onShareNote(currentNoteSnapshot()) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bottom_share_note_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    if (editorState.noteId.isNotBlank()) {
                        IconButton(
                            onClick = { onDeleteNote(editorState.noteId) },
                            modifier = Modifier.testTag("editor_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Note",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Auto-Save Control Strip + Institute Header Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = OxfordNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Save on Edit & Tap",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = editorState.autoSaveEnabled,
                        onCheckedChange = onToggleAutoSave,
                        modifier = Modifier.testTag("auto_save_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Institute Name & Course Code Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editorState.instituteName,
                    onValueChange = { newInst ->
                        onUpdateFields(
                            editorState.title,
                            editorState.content,
                            editorState.subject,
                            editorState.courseCode,
                            newInst,
                            editorState.colorIndex,
                            editorState.isPinned
                        )
                    },
                    label = { Text("Institute Name") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("note_institute_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = editorState.courseCode,
                    onValueChange = { newCode ->
                        onUpdateFields(
                            editorState.title,
                            editorState.content,
                            editorState.subject,
                            newCode,
                            editorState.instituteName,
                            editorState.colorIndex,
                            editorState.isPinned
                        )
                    },
                    label = { Text("Course Code") },
                    placeholder = { Text("CS-101") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("note_course_code_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subject / Category Selector
            Text(
                text = "Subject / Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NOTE_SUBJECT_CATEGORIES.filter { it != "All Notes" }.forEach { category ->
                    val selected = editorState.subject.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdateFields(
                                editorState.title,
                                editorState.content,
                                category,
                                editorState.courseCode,
                                editorState.instituteName,
                                editorState.colorIndex,
                                editorState.isPinned
                            )
                        },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OxfordNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note Paper Color Accent Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Notebook Sheet Color:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NoteCardAccentBars.forEachIndexed { index, barColor ->
                    val isSelected = editorState.colorIndex == index
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(barColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onUpdateFields(
                                    editorState.title,
                                    editorState.content,
                                    editorState.subject,
                                    editorState.courseCode,
                                    editorState.instituteName,
                                    index,
                                    editorState.isPinned
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected color",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notebook Sheet Card for Title & Content
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = paperTint,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top Accent Bar
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
                        OutlinedTextField(
                            value = editorState.title,
                            onValueChange = { newTitle ->
                                onUpdateFields(
                                    newTitle,
                                    editorState.content,
                                    editorState.subject,
                                    editorState.courseCode,
                                    editorState.instituteName,
                                    editorState.colorIndex,
                                    editorState.isPinned
                                )
                            },
                            label = { Text("Note Title") },
                            placeholder = { Text("Enter lecture topic or note title…") },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = Color(0xFF0F172A)
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                focusedLabelColor = accentColor,
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOTEBOOK ENTRY",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$wordCount words • ${editorState.content.length} chars",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editorState.content,
                            onValueChange = { newContent ->
                                onUpdateFields(
                                    editorState.title,
                                    newContent,
                                    editorState.subject,
                                    editorState.courseCode,
                                    editorState.instituteName,
                                    editorState.colorIndex,
                                    editorState.isPinned
                                )
                            },
                            placeholder = {
                                Text(
                                    "Write your lecture notes, formulas, definitions, and study points here…\n\nChanges are automatically saved to your notebook.",
                                    color = Color(0xFF64748B)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp)
                                .testTag("note_content_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
