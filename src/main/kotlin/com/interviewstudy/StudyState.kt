package com.interviewstudy

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.PersistentStateComponent
import com.interviewstudy.core.StudyQuestion
import java.time.LocalDate

@Service(Service.Level.APP)
@State(name = "InterviewStudyState", storages = [Storage("interview-study.xml")])
class StudyState : PersistentStateComponent<StudyState.Data> {
    data class Data(
        var baseUrl: String = "https://api.openai.com",
        var model: String = "gpt-4o-mini",
        var questions: MutableList<StudyQuestion> = mutableListOf()
    )

    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }

    fun addQuestions(questions: List<StudyQuestion>, source: String) {
        questions.forEach { it.source = source }
        data.questions.addAll(questions)
    }

    fun dueQuestions(): List<StudyQuestion> {
        val today = LocalDate.now().toEpochDay()
        return data.questions.filter { it.nextReviewEpochDay <= today }
    }

    fun saveApiKey(value: String) = PasswordSafe.instance.setPassword(credentials, value.trim())
    fun apiKey() = PasswordSafe.instance.getPassword(credentials).orEmpty()

    companion object {
        private val credentials = CredentialAttributes("InterviewStudy/OpenAICompatibleApiKey")
        fun instance(): StudyState = service()
    }
}