package com.interviewstudy

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.interviewstudy.core.AiQuestionParser
import com.interviewstudy.core.MaterialProcessing
import com.interviewstudy.core.PromptBuilder
import com.interviewstudy.core.StudyQuestion
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object AiClient {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    private val gson = Gson()

    fun generate(baseUrl: String, apiKey: String, model: String, prompt: String): List<StudyQuestion> =
        AiQuestionParser.parse(complete(baseUrl, apiKey, model, prompt))

    fun reviewAnswer(baseUrl: String, apiKey: String, model: String, question: StudyQuestion, userAnswer: String): String =
        complete(baseUrl, apiKey, model, PromptBuilder.forReview(question, userAnswer))

    fun followUp(baseUrl: String, apiKey: String, model: String, question: StudyQuestion): String =
        complete(baseUrl, apiKey, model, PromptBuilder.forFollowUp(question))

    fun generateFromMaterial(
        baseUrl: String,
        apiKey: String,
        model: String,
        name: String,
        text: String,
        direction: String,
        count: Int,
        progress: (String) -> Unit = {}
    ): List<StudyQuestion> {
        val plan = MaterialProcessing.plan(text)
        if (!plan.isLarge) return generate(baseUrl, apiKey, model, PromptBuilder.forMaterial(name, text, direction, count))

        val notes = plan.chunks.mapIndexed { index, chunk ->
            progress("正在提炼第 ${index + 1}/${plan.chunks.size} 块...")
            try {
                complete(
                    baseUrl,
                    apiKey,
                    model,
                    PromptBuilder.forKnowledgeNotes(name, direction, chunk, index + 1, plan.chunks.size)
                )
            } catch (error: Exception) {
                throw IllegalStateException("第 ${index + 1}/${plan.chunks.size} 块提炼失败：${error.message}", error)
            }
        }
        progress("正在汇总知识卡并生成题目...")
        val mergedNotes = notes.joinToString("\n\n").take(MaterialProcessing.DIRECT_LIMIT)
        return generate(baseUrl, apiKey, model, PromptBuilder.forMaterial(name, mergedNotes, direction, count))
    }

    fun test(baseUrl: String, apiKey: String, model: String) {
        generate(baseUrl, apiKey, model, "只返回 JSON：{\"questions\":[{\"title\":\"测试题\",\"type\":\"qa\",\"difficulty\":\"简单\",\"tags\":[],\"answer\":\"测试成功\",\"options\":[]}]}")
    }

    private fun complete(baseUrl: String, apiKey: String, model: String, prompt: String): String {
        require(apiKey.isNotBlank()) { "请先在设置中填写 API Key" }
        require(model.isNotBlank()) { "请先填写模型名" }
        val body = gson.toJson(mapOf(
            "model" to model.trim(),
            "temperature" to 0.4,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt))
        ))
        val request = HttpRequest.newBuilder(chatUrl(baseUrl))
            .timeout(Duration.ofMinutes(2))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("AI 调用失败 (${response.statusCode()}): ${response.body().take(300)}")
        val root = JsonParser.parseString(response.body()).asJsonObject
        return root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString
            ?: error("AI 返回格式不正确")
    }

    private fun chatUrl(baseUrl: String): URI {
        val clean = baseUrl.trim().trimEnd('/')
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Base URL 必须以 http:// 或 https:// 开头" }
        val versioned = if (Regex("/v\\d+$").containsMatchIn(clean)) clean else "$clean/v1"
        return URI.create("$versioned/chat/completions")
    }
}
