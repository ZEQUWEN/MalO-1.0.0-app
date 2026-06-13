package com.example.util

import android.content.Context
import android.net.Uri
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.InputStream

object PdfExtractor {
    fun extractText(context: Context, uri: Uri): String {
        var inputStream: InputStream? = null
        var reader: PdfReader? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) 
                ?: return "Не удалось открыть PDF файл"
            reader = PdfReader(inputStream)
            val pages = reader.numberOfPages
            val sb = StringBuilder()
            val maxPages = minOf(pages, 15) // Limit page count to prevent API content limit / memory overhead
            
            for (i in 1..maxPages) {
                val text = PdfTextExtractor.getTextFromPage(reader, i)
                sb.append(text).append("\n")
            }
            
            if (pages > maxPages) {
                sb.append("\n[Файл обрезен: показаны первые $maxPages страниц из $pages]\n")
            }
            
            val result = sb.toString().trim()
            return if (result.isEmpty()) "[Пустой PDF или отсканированный документ без текстового слоя]" else result
        } catch (e: Exception) {
            e.printStackTrace()
            return "Не удалось прочитать текст из PDF: ${e.localizedMessage}"
        } finally {
            try {
                reader?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
