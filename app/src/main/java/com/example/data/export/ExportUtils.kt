package com.example.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {

    private fun exportsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DocuScan")
        dir.mkdirs()
        return dir
    }

    fun exportDocument(
        context: Context,
        docName: String,
        format: String,
        text: String,
        images: List<Bitmap> = emptyList()
    ): File {
        val safeName = docName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return when (format.uppercase()) {
            "PDF" -> buildPdf(context, safeName, text, images)
            "DOCX" -> buildDocx(context, safeName, text)
            else -> buildTxt(context, safeName, text)
        }
    }

    private fun buildTxt(context: Context, safeName: String, text: String): File {
        val file = File(exportsDir(context), "$safeName.txt")
        file.writeText(text)
        return file
    }

    private fun buildPdf(context: Context, safeName: String, text: String, images: List<Bitmap>): File {
        val file = File(exportsDir(context), "$safeName.pdf")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }

        fun newPage(): PdfDocument.Page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        )

        var page = newPage()
        var canvas = page.canvas
        var y = margin
        val lineHeight = 16f

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) {
                document.finishPage(page)
                page = newPage()
                canvas = page.canvas
                y = margin
            }
        }

        if (images.isNotEmpty()) {
            images.forEach { bmp ->
                val ratio = (pageWidth - 2 * margin) / bmp.width
                val drawH = (bmp.height * ratio).coerceAtMost(pageHeight - 2 * margin)
                ensureSpace(drawH + 10f)
                val scaled = Bitmap.createScaledBitmap(bmp, (pageWidth - 2 * margin).toInt(), drawH.toInt(), true)
                canvas.drawBitmap(scaled, margin, y, paint)
                y += drawH + 10f
            }
        }

        text.lineSequence().forEach { line ->
            var remaining = line
            while (remaining.isNotEmpty()) {
                val fits = fitLine(remaining, paint, pageWidth - 2 * margin)
                ensureSpace(lineHeight)
                canvas.drawText(remaining.take(fits), margin, y, paint)
                y += lineHeight
                remaining = remaining.drop(fits)
            }
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun fitLine(text: String, paint: Paint, maxWidth: Float): Int {
        if (paint.measureText(text) <= maxWidth) return text.length
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end)) > maxWidth) end--
        val lastSpace = text.lastIndexOf(' ', end)
        return if (lastSpace in 1 until end) lastSpace else end
    }

    // Minimal valid OOXML .docx (a zip with the required document parts).
    private fun buildDocx(context: Context, safeName: String, text: String): File {
        val file = File(exportsDir(context), "$safeName.docx")
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val paragraphs = escaped.lines().joinToString("") {
            "<w:p><w:r><w:t xml:space=\"preserve\">$it</w:t></w:r></w:p>"
        }
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$paragraphs</w:body></w:document>"

        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("[Content_Types].xml", contentTypes)
            add("_rels/.rels", rels)
            add("word/document.xml", document)
        }
        return file
    }
}
