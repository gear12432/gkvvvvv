package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.InstituteProfile
import com.example.data.model.Note
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportResult(
    val fileName: String,
    val cacheUri: Uri,
    val savedToDownloads: Boolean
)

object PdfExportHelper {

    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN_HORIZONTAL = 44f
    private const val MARGIN_TOP = 44f
    private const val MARGIN_BOTTOM = 52f
    private val CONTENT_WIDTH = (PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)).toInt()

    fun formatTimestamp(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: Date()
        val formatter = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.ENGLISH)
        return formatter.format(date)
    }

    fun sanitizeFileName(rawTitle: String): String {
        val cleaned = rawTitle.trim()
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .replace(Regex("\\s+"), "_")
            .take(45)
            .ifBlank { "Institute_Note" }
        return "${cleaned}_${System.currentTimeMillis() / 1000}.pdf"
    }

    /**
     * Generates a formatted PDF for a single Note and writes it to the provided OutputStream.
     */
    fun writeSingleNotePdf(
        outputStream: OutputStream,
        note: Note,
        profile: InstituteProfile
    ) {
        val document = PdfDocument()
        try {
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            val fullText = note.content.ifBlank { "(No content entered in this note.)" }
            val staticLayout = StaticLayout.Builder
                .obtain(fullText, 0, fullText.length, bodyPaint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .setIncludePad(true)
                .build()

            val totalLines = staticLayout.lineCount
            var currentLine = 0
            var pageNumber = 1

            while (currentLine < totalLines || pageNumber == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                var currentY = drawInstituteHeader(canvas, profile, note.instituteName)

                if (pageNumber == 1) {
                    currentY = drawNoteMetadataCard(canvas, note, currentY)
                } else {
                    val contPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#64748B")
                        textSize = 10f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    }
                    canvas.drawText(
                        "Continued: ${note.title.take(65)}",
                        MARGIN_HORIZONTAL,
                        currentY + 14f,
                        contPaint
                    )
                    currentY += 28f
                }

                val maxContentY = PAGE_HEIGHT - MARGIN_BOTTOM
                var lineIdx = currentLine
                val startLineTop = staticLayout.getLineTop(currentLine)

                while (lineIdx < totalLines) {
                    val lineBottom = staticLayout.getLineBottom(lineIdx) - startLineTop
                    if (currentY + lineBottom > maxContentY) {
                        break
                    }
                    lineIdx++
                }

                if (lineIdx == currentLine && currentLine < totalLines) {
                    lineIdx = currentLine + 1
                }

                canvas.save()
                canvas.translate(MARGIN_HORIZONTAL, currentY - startLineTop)
                val clipBottom = if (lineIdx > currentLine) {
                    staticLayout.getLineBottom(lineIdx - 1).toFloat()
                } else {
                    startLineTop.toFloat() + 24f
                }
                canvas.clipRect(0f, startLineTop.toFloat(), CONTENT_WIDTH.toFloat(), clipBottom)
                staticLayout.draw(canvas)
                canvas.restore()

                drawPageFooter(canvas, pageNumber, profile.instituteName)
                document.finishPage(page)

                currentLine = lineIdx
                pageNumber++
                if (currentLine >= totalLines) break
            }

            document.writeTo(outputStream)
        } catch (e: IllegalStateException) {
            writeFallbackStandardPdf(outputStream, listOf(note), profile)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun writeFallbackStandardPdf(
        outputStream: OutputStream,
        notes: List<Note>,
        profile: InstituteProfile
    ) {
        val textLines = mutableListOf<String>()
        textLines.add(profile.instituteName)
        textLines.add("${profile.department} - ${profile.academicSession}")
        textLines.add("Owner: ${profile.studentOrFacultyName} (${profile.rollOrIdNumber})")
        textLines.add("------------------------------------------------------------")
        notes.forEach { note ->
            textLines.add("Title: ${note.title}")
            textLines.add("Subject: ${note.subject} | Course: ${note.courseCode}")
            textLines.add("Date: ${formatTimestamp(note.updatedAt ?: note.createdAt)}")
            textLines.add("")
            note.content.lines().forEach { line ->
                textLines.add(line.take(90))
            }
            textLines.add("------------------------------------------------------------")
        }

        val escapedLines = textLines.take(45).map { line ->
            line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        }
        val streamContent = buildString {
            append("BT\n/F1 11 Tf\n50 780 Td\n15 TL\n")
            escapedLines.forEach { line ->
                append("($line) Tj T*\n")
            }
            append("ET\n")
        }
        val pdfString = buildString {
            appendLine("%PDF-1.4")
            appendLine("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj")
            appendLine("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj")
            appendLine("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj")
            appendLine("4 0 obj << /Length ${streamContent.length} >>")
            appendLine("stream")
            append(streamContent)
            appendLine("endstream")
            appendLine("endobj")
            appendLine("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj")
            appendLine("xref")
            appendLine("0 6")
            appendLine("0000000000 65535 f ")
            appendLine("trailer << /Size 6 /Root 1 0 R >>")
            appendLine("startxref")
            appendLine("0")
            append("%%EOF")
        }
        outputStream.write(pdfString.toByteArray(Charsets.UTF_8))
    }

    /**
     * Generates a multi-note Notebook Compendium PDF containing all notes.
     */
    fun writeAllNotesCompendiumPdf(
        outputStream: OutputStream,
        notes: List<Note>,
        profile: InstituteProfile
    ) {
        val document = PdfDocument()
        try {
            if (notes.isEmpty()) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = document.startPage(pageInfo)
                val y = drawInstituteHeader(page.canvas, profile, profile.instituteName)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#334155")
                    textSize = 13f
                }
                page.canvas.drawText("No notes available in notebook.", MARGIN_HORIZONTAL, y + 40f, paint)
                drawPageFooter(page.canvas, 1, profile.instituteName)
                document.finishPage(page)
            } else {
                var pageNumber = 1
                notes.forEachIndexed { index, note ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    var currentY = drawInstituteHeader(canvas, profile, note.instituteName)
                    currentY = drawNoteMetadataCard(canvas, note, currentY, index + 1, notes.size)

                    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E293B")
                        textSize = 11f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    }
                    val snippet = note.content.ifBlank { "(No content)" }.take(2800)
                    val staticLayout = StaticLayout.Builder
                        .obtain(snippet, 0, snippet.length, bodyPaint, CONTENT_WIDTH)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.15f)
                        .build()

                    canvas.save()
                    canvas.translate(MARGIN_HORIZONTAL, currentY)
                    canvas.clipRect(0f, 0f, CONTENT_WIDTH.toFloat(), (PAGE_HEIGHT - MARGIN_BOTTOM - currentY))
                    staticLayout.draw(canvas)
                    canvas.restore()

                    drawPageFooter(canvas, pageNumber, profile.instituteName)
                    document.finishPage(page)
                    pageNumber++
                }
            }
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    private fun drawInstituteHeader(
        canvas: Canvas,
        profile: InstituteProfile,
        noteInstituteName: String
    ): Float {
        val bannerRect = RectF(
            MARGIN_HORIZONTAL,
            MARGIN_TOP,
            PAGE_WIDTH - MARGIN_HORIZONTAL,
            MARGIN_TOP + 76f
        )
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B365D")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bannerRect, 10f, 10f, bannerPaint)

        // Gold accent stripe
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D97706")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(MARGIN_HORIZONTAL, MARGIN_TOP + 70f, PAGE_WIDTH - MARGIN_HORIZONTAL, MARGIN_TOP + 76f),
            3f,
            3f,
            goldPaint
        )

        val instTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val displayInstitute = noteInstituteName.ifBlank { profile.instituteName }.take(55)
        canvas.drawText(displayInstitute, MARGIN_HORIZONTAL + 16f, MARGIN_TOP + 26f, instTitlePaint)

        val deptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val subLine1 = "${profile.department.take(45)}  •  ${profile.academicSession.take(25)}"
        canvas.drawText(subLine1, MARGIN_HORIZONTAL + 16f, MARGIN_TOP + 44f, deptPaint)

        val scholarLine = buildString {
            append("Notebook Owner: ")
            append(profile.studentOrFacultyName.ifBlank { "Scholar" })
            if (profile.rollOrIdNumber.isNotBlank()) {
                append("  |  ID: ${profile.rollOrIdNumber}")
            }
        }
        val goldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(scholarLine, MARGIN_HORIZONTAL + 16f, MARGIN_TOP + 61f, goldTextPaint)

        return MARGIN_TOP + 92f
    }

    private fun drawNoteMetadataCard(
        canvas: Canvas,
        note: Note,
        startY: Float,
        noteIndex: Int? = null,
        totalNotes: Int? = null
    ): Float {
        val metaRect = RectF(
            MARGIN_HORIZONTAL,
            startY,
            PAGE_WIDTH - MARGIN_HORIZONTAL,
            startY + 68f
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(metaRect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(metaRect, 8f, 8f, borderPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D97706")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val prefix = if (noteIndex != null && totalNotes != null) "NOTE $noteIndex OF $totalNotes  •  " else ""
        val coursePart = if (note.courseCode.isNotBlank()) " [${note.courseCode}]" else ""
        val pinPart = if (note.isPinned) "  ★ PINNED" else ""
        canvas.drawText(
            "$prefix${note.subject.uppercase(Locale.ENGLISH)}$coursePart$pinPart",
            MARGIN_HORIZONTAL + 14f,
            startY + 18f,
            badgePaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            note.title.ifBlank { "Untitled Note" }.take(58),
            MARGIN_HORIZONTAL + 14f,
            startY + 40f,
            titlePaint
        )

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val dateText = "Last Updated: ${formatTimestamp(note.updatedAt ?: note.createdAt)}"
        canvas.drawText(dateText, MARGIN_HORIZONTAL + 14f, startY + 58f, datePaint)

        return startY + 86f
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, instituteName: String) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        val footerY = PAGE_HEIGHT - 28f
        canvas.drawLine(MARGIN_HORIZONTAL, footerY - 12f, PAGE_WIDTH - MARGIN_HORIZONTAL, footerY - 12f, linePaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            "Generated by Institute Notebook • ${instituteName.take(40)}",
            MARGIN_HORIZONTAL,
            footerY,
            footerTextPaint
        )
        val pageLabel = "Page $pageNumber"
        val labelWidth = footerTextPaint.measureText(pageLabel)
        canvas.drawText(
            pageLabel,
            PAGE_WIDTH - MARGIN_HORIZONTAL - labelWidth,
            footerY,
            footerTextPaint
        )
    }

    /**
     * Exports a single note PDF both to the app's FileProvider cache AND to the device's
     * public Downloads folder so it is immediately downloaded and ready to open or share.
     */
    fun downloadNoteAsPdf(
        context: Context,
        note: Note,
        profile: InstituteProfile
    ): Result<PdfExportResult> = runCatching {
        val fileName = sanitizeFileName(note.title)
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val cacheFile = File(pdfDir, fileName)

        FileOutputStream(cacheFile).use { out ->
            writeSingleNotePdf(out, note, profile)
        }

        val savedToDownloads = saveFileToPublicDownloads(context, cacheFile, fileName)
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, cacheFile)

        PdfExportResult(
            fileName = fileName,
            cacheUri = contentUri,
            savedToDownloads = savedToDownloads
        )
    }

    /**
     * Exports all notes as a single Compendium PDF to Downloads and FileProvider cache.
     */
    fun downloadAllNotesAsPdf(
        context: Context,
        notes: List<Note>,
        profile: InstituteProfile
    ): Result<PdfExportResult> = runCatching {
        val fileName = sanitizeFileName("${profile.instituteName}_Notebook")
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val cacheFile = File(pdfDir, fileName)

        FileOutputStream(cacheFile).use { out ->
            writeAllNotesCompendiumPdf(out, notes, profile)
        }

        val savedToDownloads = saveFileToPublicDownloads(context, cacheFile, fileName)
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, cacheFile)

        PdfExportResult(
            fileName = fileName,
            cacheUri = contentUri,
            savedToDownloads = savedToDownloads
        )
    }

    private fun saveFileToPublicDownloads(context: Context, sourceFile: File, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openOrSharePdf(context: Context, pdfResult: PdfExportResult, preferShare: Boolean = false) {
        if (!preferShare) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfResult.cacheUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(viewIntent, "Open PDF Document"))
                return
            } catch (_: Exception) {
                // Fallback to share sheet if no PDF viewer is installed
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfResult.cacheUri)
            putExtra(Intent.EXTRA_SUBJECT, pdfResult.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Share PDF Note").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun shareNoteText(context: Context, note: Note, profile: InstituteProfile) {
        val shareBody = buildString {
            appendLine("🏛️ ${note.instituteName.ifBlank { profile.instituteName }}")
            if (profile.department.isNotBlank()) {
                appendLine("📚 ${profile.department} (${profile.academicSession})")
            }
            appendLine("────────────────────────────────")
            appendLine("📝 ${note.title.ifBlank { "Untitled Note" }}")
            val metaLine = buildList {
                if (note.subject.isNotBlank()) add("Subject: ${note.subject}")
                if (note.courseCode.isNotBlank()) add("Course: ${note.courseCode}")
            }.joinToString(" | ")
            if (metaLine.isNotBlank()) {
                appendLine(metaLine)
            }
            appendLine("Date: ${formatTimestamp(note.updatedAt ?: note.createdAt)}")
            appendLine("────────────────────────────────")
            appendLine()
            appendLine(note.content.ifBlank { "(No content)" })
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Institute Note" })
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, "Share Note via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
