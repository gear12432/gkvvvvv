package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Note(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val subject: String = "Lecture Notes",
    val courseCode: String = "",
    val instituteName: String = "Oxford Institute of Science & Technology",
    val colorIndex: Int = 0,
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InstituteProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val instituteName: String = "Oxford Institute of Science & Technology",
    val department: String = "Department of Computer Science & Engineering",
    val academicSession: String = "Academic Session 2026 - 2027",
    val studentOrFacultyName: String = "Academic Scholar",
    val rollOrIdNumber: String = "SCH-2026-01",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

val NOTE_SUBJECT_CATEGORIES = listOf(
    "All Notes",
    "Lecture Notes",
    "Assignments",
    "Lab Reports",
    "Exam Prep",
    "Research",
    "Seminar & Workshop"
)
