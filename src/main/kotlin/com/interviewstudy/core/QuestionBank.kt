package com.interviewstudy.core

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

data class QuestionFilters(
    val keyword: String = "",
    val type: String = "",
    val tag: String = "",
    val source: String = "",
    val dueOnly: Boolean = false,
    val favoriteOnly: Boolean = false,
    val wrongOnly: Boolean = false
)

data class QuestionStats(
    val total: Int,
    val due: Int,
    val reviewedToday: Int,
    val mastered: Int,
    val favorite: Int,
    val wrong: Int,
    val masteryRate: Int
)

object QuestionBank {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val questionListType = object : TypeToken<MutableList<StudyQuestion>>() {}.type

    fun filter(
        questions: List<StudyQuestion>,
        filters: QuestionFilters,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): List<StudyQuestion> = questions.filter { question ->
        question.matchesKeyword(filters.keyword) &&
            question.matchesType(filters.type) &&
            question.matchesTag(filters.tag) &&
            question.source.contains(filters.source.trim(), ignoreCase = true) &&
            (!filters.dueOnly || question.nextReviewEpochDay <= todayEpochDay) &&
            (!filters.favoriteOnly || question.favorite) &&
            (!filters.wrongOnly || question.isWrong)
    }

    fun stats(questions: List<StudyQuestion>, todayEpochDay: Long = LocalDate.now().toEpochDay()): QuestionStats {
        val total = questions.size
        val mastered = questions.count { it.reviewLevel >= 5 }
        return QuestionStats(
            total = total,
            due = questions.count { it.nextReviewEpochDay <= todayEpochDay },
            reviewedToday = questions.count { it.lastReviewedEpochDay == todayEpochDay },
            mastered = mastered,
            favorite = questions.count { it.favorite },
            wrong = questions.count { it.isWrong },
            masteryRate = if (total == 0) 0 else mastered * 100 / total
        )
    }

    fun exportJson(questions: List<StudyQuestion>): String = gson.toJson(questions)

    fun importJson(json: String): MutableList<StudyQuestion> = gson.fromJson(json, questionListType)
        ?: mutableListOf()

    private fun StudyQuestion.matchesKeyword(keyword: String): Boolean {
        val value = keyword.trim()
        if (value.isBlank()) return true
        return title.contains(value, ignoreCase = true) ||
            answer.contains(value, ignoreCase = true) ||
            source.contains(value, ignoreCase = true) ||
            tags.any { it.contains(value, ignoreCase = true) } ||
            options.any { it.key.contains(value, ignoreCase = true) || it.text.contains(value, ignoreCase = true) }
    }

    private fun StudyQuestion.matchesType(type: String): Boolean = type.isBlank() || this.type == type

    private fun StudyQuestion.matchesTag(tag: String): Boolean {
        val value = tag.trim()
        return value.isBlank() || tags.any { it.contains(value, ignoreCase = true) }
    }
}
