package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.InstituteProfile
import com.example.data.model.Note
import com.example.util.PdfExportHelper
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Institute Notebook", appName)
    }

    @Test
    fun `generate single note pdf produces valid pdf bytes`() {
        val output = ByteArrayOutputStream()
        val sampleNote = Note(
            id = "note_test_1",
            title = "Operating Systems Lecture Notes",
            content = "Virtual memory, paging, segmentation, and page replacement algorithms.",
            subject = "Lecture Notes",
            courseCode = "CS-305",
            instituteName = "Oxford Institute of Science & Technology"
        )
        val profile = InstituteProfile(
            instituteName = "Oxford Institute of Science & Technology",
            department = "Department of Computer Science",
            academicSession = "2026 - 2027"
        )
        PdfExportHelper.writeSingleNotePdf(output, sampleNote, profile)
        val bytes = output.toByteArray()
        assertTrue(bytes.isNotEmpty())
        val header = String(bytes.take(4).toByteArray())
        assertEquals("%PDF", header)
    }
}
