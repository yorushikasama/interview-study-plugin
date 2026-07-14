package com.interviewstudy.core

data class MaterialPlan(val chunks: List<String>) {
    val isLarge: Boolean get() = chunks.size > 1
    val estimatedCalls: Int get() = if (isLarge) chunks.size + 1 else 1
}

object MaterialProcessing {
    const val DIRECT_LIMIT = 18_000
    const val CHUNK_LIMIT = 12_000
    const val MAX_CHUNKS = 20

    fun plan(text: String): MaterialPlan {
        val content = text.trim()
        require(content.isNotBlank()) { "资料内容为空" }
        if (content.length <= DIRECT_LIMIT) return MaterialPlan(listOf(content))

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
            }
        }

        content.split(Regex("\\n\\s*\\n")).filter(String::isNotBlank).forEach { paragraph ->
            if (paragraph.length > CHUNK_LIMIT) {
                flush()
                chunks += paragraph.chunked(CHUNK_LIMIT)
            } else if (current.isEmpty()) {
                current.append(paragraph)
            } else if (current.length + 2 + paragraph.length <= CHUNK_LIMIT) {
                current.append("\n\n").append(paragraph)
            } else {
                flush()
                current.append(paragraph)
            }
        }
        flush()
        require(chunks.size <= MAX_CHUNKS) { "资料过长：最多支持 $MAX_CHUNKS 个分块（约 24 万字）" }
        return MaterialPlan(chunks)
    }
}