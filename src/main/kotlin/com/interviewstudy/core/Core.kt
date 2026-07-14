package com.interviewstudy.core

import com.google.gson.JsonParser
import java.time.LocalDate
import java.util.UUID

data class QuestionOption(var key: String = "", var text: String = "")

data class StudyQuestion(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var type: String = "qa",
    var difficulty: String = "中等",
    var tags: MutableList<String> = mutableListOf(),
    var answer: String = "",
    var options: MutableList<QuestionOption> = mutableListOf(),
    var source: String = "",
    var favorite: Boolean = false,
    var isWrong: Boolean = false,
    var reviewLevel: Int = 0,
    var lastReviewedEpochDay: Long = 0,
    var nextReviewEpochDay: Long = LocalDate.now().toEpochDay()
)

fun StudyQuestion.reviewStatus(todayEpochDay: Long = LocalDate.now().toEpochDay()): String = when {
    isWrong -> "错题"
    reviewLevel >= 5 -> "已掌握"
    nextReviewEpochDay <= todayEpochDay && lastReviewedEpochDay > 0 -> "待复习"
    lastReviewedEpochDay == 0L -> "新题"
    else -> "学习中"
}

fun StudyQuestion.markReview(known: Boolean, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
    lastReviewedEpochDay = todayEpochDay
    if (known) {
        isWrong = false
        reviewLevel = (reviewLevel + 1).coerceAtMost(5)
    } else {
        isWrong = true
        reviewLevel = (reviewLevel - 1).coerceAtLeast(0)
    }
    val days = ReviewSchedule.daysUntilNextReview(reviewLevel)
    nextReviewEpochDay = todayEpochDay + days
}

object ReviewSchedule {
    private val intervals = intArrayOf(1, 2, 4, 7, 14, 30)
    fun daysUntilNextReview(level: Int) = intervals[level.coerceIn(0, intervals.lastIndex)]
}

object PromptBuilder {
    private const val schema = """{"questions":[{"title":"题干","type":"qa|single_choice","difficulty":"简单|中等|偏难","tags":["标签"],"answer":"参考答案","options":[{"key":"A","text":"选项"}]}]}"""

    fun forJob(role: String, level: String, stack: String, direction: String, count: Int) = build(
        count,
        "岗位：${role.trim()}\n级别：${level.trim()}\n技术栈：${stack.trim()}",
        direction
    )

    fun forMaterial(name: String, text: String, direction: String, count: Int) = build(
        count,
        "资料：${name.trim()}\n内容：$text",
        direction
    )

    fun forReview(question: StudyQuestion, userAnswer: String): String = """请你扮演面试官，对下面的回答做简洁批改。
题目：${question.title}
题型：${question.type}
参考答案：${question.answer}
我的回答：$userAnswer
要求：输出评分（满分 10 分）、优点、缺口、改进后的参考回答；只返回纯文本，不要 JSON，不要 Markdown。"""

    fun forFollowUp(question: StudyQuestion): String = """请基于下面这道面试题继续追问 3 个更深入的问题，并给出每个问题的考察点。
题目：${question.title}
参考答案：${question.answer}
要求：只返回纯文本，不要 JSON，不要 Markdown。"""

    fun forKnowledgeNotes(name: String, direction: String, content: String, index: Int, total: Int): String {
        require(index in 1..total) { "分块序号无效" }
        return """请阅读资料《${name.trim()}》第 $index/$total 块，围绕“${direction.trim().ifBlank { "综合考察" }}”提炼面试知识卡。
要求：覆盖关键概念、原理、项目实践、易错点和可追问点；不生成题目；不重复原文；控制在 700 个中文字符以内；只返回纯文本。
内容：$content"""
    }
    private fun build(count: Int, source: String, direction: String): String {
        require(count in 1..20) { "题目数量必须在 1 到 20 之间" }
        return """请生成 $count 道中文技术面试题。
$source
方向：${direction.trim().ifBlank { "综合考察" }}
要求：题目准确、有区分度，答案适合面试复习；根据内容自动混合问答题和单选题；单选题至少提供 2 个选项，answer 包含正确选项。只返回 JSON，不要解释或 Markdown。
JSON 格式：$schema"""
    }
}

object AiQuestionParser {
    fun parse(content: String): List<StudyQuestion> {
        val json = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = JsonParser.parseString(json).asJsonObject
        val questions = root.getAsJsonArray("questions") ?: error("AI 返回中缺少 questions")
        return questions.map { element ->
            val item = element.asJsonObject
            val options = item.getAsJsonArray("options")?.map { option ->
                val value = option.asJsonObject
                QuestionOption(value.string("key", ""), value.string("text", ""))
            }?.filter { it.key.isNotBlank() && it.text.isNotBlank() }?.toMutableList() ?: mutableListOf()
            val type = if (item.string("type", "qa") == "single_choice" && options.size >= 2) "single_choice" else "qa"
            StudyQuestion(
                title = item.requiredString("title"),
                type = type,
                difficulty = item.string("difficulty", "中等"),
                tags = item.getAsJsonArray("tags")?.map { it.asString }?.toMutableList() ?: mutableListOf(),
                answer = item.requiredString("answer"),
                options = options
            )
        }.filter { it.title.isNotBlank() }
    }

    private fun com.google.gson.JsonObject.requiredString(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() }
            ?: error("AI 返回题目缺少 $name")

    private fun com.google.gson.JsonObject.string(name: String, fallback: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty().ifBlank { fallback }
}
