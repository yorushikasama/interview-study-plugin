package com.interviewstudy.core

object CoreBehaviorCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(ReviewSchedule.daysUntilNextReview(0) == 1)
        check(ReviewSchedule.daysUntilNextReview(3) == 7)

        val prompt = PromptBuilder.forJob("Java 后端", "高级", "Spring, MySQL", "并发", 5)
        check(prompt.contains("5 道中文技术面试题"))
        check(prompt.contains("Java 后端"))
        check(prompt.contains("JSON"))
        check(!prompt.contains("behavior_star"))

        val notesPrompt = PromptBuilder.forKnowledgeNotes("资料.pdf", "并发", "正文", 2, 5)
        check(notesPrompt.contains("第 2/5 块"))
        check(notesPrompt.contains("并发"))
        check(notesPrompt.contains("知识卡"))
        check(notesPrompt.contains("正文"))

        val response = """```json
            {"questions":[{"title":"什么是 GC Roots？","type":"qa","difficulty":"中等","tags":["JVM"],"answer":"GC Roots 是可达性分析起点","options":[]}]}
            ```""".trimIndent()
        val question = AiQuestionParser.parse(response).single()
        check(question.title == "什么是 GC Roots？")
        check(question.tags.single() == "JVM")
        val unknownType = AiQuestionParser.parse("""{"questions":[{"title":"自我介绍","type":"behavior_star","difficulty":"中等","tags":[],"answer":"回答","options":[]}]}""").single()
        check(unknownType.type == "qa")

        val choice = AiQuestionParser.parse("""{"questions":[{"title":"哪个是 JVM 参数？","type":"single_choice","difficulty":"简单","tags":["JVM"],"answer":"A","options":[{"key":"A","text":"-Xmx"},{"key":"B","text":"SELECT"}]}]}""").single()
        check(choice.type == "single_choice")
        check(choice.options.size == 2)
        check(choice.answerText().contains("A. -Xmx"))
        val explainedChoice = AiQuestionParser.parse("""{"questions":[{"title":"事务失效原因？","type":"single_choice","difficulty":"中等","tags":["Spring"],"answer":"C","explanation":"C 会吞掉异常，事务拦截器感知不到回滚条件。","options":[{"key":"C","text":"try-catch 中吞掉 RuntimeException"},{"key":"D","text":"默认回滚 RuntimeException"}]}]}""").single()
        check(explainedChoice.explanation.contains("事务拦截器"))
        check(explainedChoice.answerText().contains("解析"))
        val shortPlan = MaterialProcessing.plan("短资料")
        check(shortPlan.chunks == listOf("短资料"))
        check(!shortPlan.isLarge)
        check(shortPlan.estimatedCalls == 1)

        val paragraphText = "甲".repeat(10_000) + "\n\n" + "乙".repeat(10_000)
        val paragraphPlan = MaterialProcessing.plan(paragraphText)
        check(paragraphPlan.chunks.size == 2)
        check(paragraphPlan.chunks.all { it.length <= 12_000 })
        check(paragraphPlan.chunks.joinToString("\n\n") == paragraphText)
        check(paragraphPlan.estimatedCalls == 3)

        val longParagraph = "长".repeat(18_001)
        val longParagraphPlan = MaterialProcessing.plan(longParagraph)
        check(longParagraphPlan.chunks.size == 2)
        check(longParagraphPlan.chunks.joinToString("") == longParagraph)

        val excessive = (1..21).joinToString("\n\n") { it.toString().repeat(12_000) }
        check(runCatching { MaterialProcessing.plan(excessive) }.exceptionOrNull()?.message?.contains("20") == true)
    }
}

