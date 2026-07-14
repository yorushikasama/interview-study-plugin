package com.interviewstudy

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object DocumentTextExtractor {
    fun extract(path: Path): String {
        require(Files.isRegularFile(path)) { "资料文件不存在" }
        val text = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "pdf" -> Loader.loadPDF(path.toFile()).use { PDFTextStripper().getText(it) }
            "docx" -> Files.newInputStream(path).use { input ->
                XWPFDocument(input).use { document -> document.paragraphs.joinToString("\n") { it.text } }
            }
            "md", "markdown", "txt" -> Files.readString(path, StandardCharsets.UTF_8)
            else -> error("仅支持 PDF、DOCX、Markdown、TXT")
        }.trim()
        require(text.isNotBlank()) { "未能从资料中提取文本" }
        return text
    }
}