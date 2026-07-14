package com.interviewstudy.core

object StudyFeatureCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val today = java.time.LocalDate.now().toEpochDay()
        val fresh = StudyQuestion(title = "HashMap", type = "qa", answer = "数组+链表+红黑树")
        check(fresh.reviewStatus(today) == "新题")
        fresh.markReview(false, today)
        check(fresh.isWrong)
        check(fresh.reviewLevel == 0)
        check(fresh.nextReviewEpochDay == today + 1)
        check(fresh.reviewStatus(today) == "错题")
        fresh.markReview(true, today)
        check(!fresh.isWrong)
        check(fresh.reviewLevel == 1)
        check(fresh.nextReviewEpochDay == today + 2)
        check(fresh.reviewStatus(today) == "学习中")
        repeat(5) { fresh.markReview(true, today) }
        check(fresh.reviewStatus(today) == "已掌握")
        fresh.favorite = true

        val due = StudyQuestion(title = "JVM", tags = mutableListOf("Java"), source = "资料：a.pdf", nextReviewEpochDay = today - 1)
        val future = StudyQuestion(title = "Redis", type = "single_choice", source = "岗位：后端", nextReviewEpochDay = today + 3)
        val filters = QuestionFilters(keyword = "jv", tag = "Java", dueOnly = true)
        check(QuestionBank.filter(listOf(fresh, due, future), filters, today) == listOf(due))
        check(QuestionBank.stats(listOf(fresh, due, future), today).due == 1)
        check(QuestionBank.stats(listOf(fresh, due, future), today).favorite == 1)
        check(QuestionBank.stats(listOf(fresh, due, future), today).wrong == 0)

        val json = QuestionBank.exportJson(listOf(fresh, due, future))
        val imported = QuestionBank.importJson(json)
        check(imported.size == 3)
        check(imported.first().favorite)
        check(imported.first().reviewStatus(today) == "已掌握")
    }
}
