package com.interviewstudy

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.interviewstudy.core.MaterialProcessing
import com.interviewstudy.core.PromptBuilder
import com.interviewstudy.core.QuestionBank
import com.interviewstudy.core.QuestionFilters
import com.interviewstudy.core.ReviewSchedule
import com.interviewstudy.core.StudyQuestion
import com.interviewstudy.core.answerText
import com.interviewstudy.core.markReview
import com.interviewstudy.core.reviewStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import java.time.LocalDate
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

class InterviewStudyToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val state = StudyState.instance()
        val practice = PracticePanel(state)
        val tabs = JTabbedPane().apply {
            addTab("生成题目", GeneratePanel(state, practice::refresh))
            addTab("题库练习", practice)
            addTab("设置", SettingsPanel(state))
        }
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(tabs, "", false))
    }
}

private class GeneratePanel(private val state: StudyState, private val onGenerated: () -> Unit) : JPanel(BorderLayout()) {
    private val status = JLabel("配置 API 后即可生成题目")

    init {
        val tabs = JTabbedPane().apply {
            addTab("按岗位生成", jobPanel())
            addTab("按资料生成", materialPanel())
        }
        add(tabs, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    private fun jobPanel(): JPanel {
        val role = JTextField("Java 后端")
        val level = JTextField("高级")
        val stack = JTextField("Java, Spring Boot, MySQL, Redis")
        val direction = JTextField("项目追问、原理和故障排查")
        val count = JSpinner(SpinnerNumberModel(10, 1, 20, 1))
        val button = JButton("AI 生成题目")
        button.addActionListener {
            generate(button, "岗位：${role.text}") {
                PromptBuilder.forJob(role.text, level.text, stack.text, direction.text, count.value as Int)
            }
        }
        return formPanel(
            "岗位" to role,
            "级别" to level,
            "技术栈" to stack,
            "出题方向" to direction,
            "题目数量" to count,
            "" to button
        )
    }

    private fun materialPanel(): JPanel {
        val file = JTextField().apply { isEditable = false }
        val choose = JButton("选择资料")
        val fileRow = JPanel(BorderLayout(6, 0)).apply { add(file); add(choose, BorderLayout.EAST) }
        val direction = JTextField("提取核心知识点和项目追问")
        val count = JSpinner(SpinnerNumberModel(10, 1, 20, 1))
        val button = JButton("根据资料生成")
        val preview = JButton("预览文本")
        var selectedPath: Path? = null
        var selectedText: String? = null
        var estimatedCalls = 1

        choose.addActionListener {
            val chooser = JFileChooser().apply {
                dialogTitle = "选择 PDF、DOCX、Markdown 或 TXT"
                fileSelectionMode = JFileChooser.FILES_ONLY
            }
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return@addActionListener
            val path = chooser.selectedFile.toPath()
            file.text = path.toString()
            selectedPath = null
            selectedText = null
            runAsync(choose, status, "正在提取资料文本...", task = {
                val text = DocumentTextExtractor.extract(path)
                Triple(path, text, MaterialProcessing.plan(text))
            }, done = { (loadedPath, text, plan) ->
                selectedPath = loadedPath
                selectedText = text
                estimatedCalls = plan.estimatedCalls
                status.text = if (plan.isLarge) {
                    "共 ${text.length} 字，全文模式，预计 $estimatedCalls 次 AI 调用"
                } else {
                    "共 ${text.length} 字，单次 AI 调用"
                }
            })
        }

        preview.addActionListener {
            val text = selectedText
            if (text == null) {
                status.text = "请先选择并等待资料解析完成"
            } else {
                JTextArea(text.take(10_000)).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    JOptionPane.showMessageDialog(this@GeneratePanel, JScrollPane(this), "资料文本预览", JOptionPane.PLAIN_MESSAGE)
                }
            }
        }

        button.addActionListener {
            val path = selectedPath
            val text = selectedText
            if (path == null || text == null) {
                status.text = "请先选择并等待资料解析完成"
                return@addActionListener
            }
            if (estimatedCalls > 1) {
                val answer = JOptionPane.showConfirmDialog(
                    this,
                    "资料共 ${text.length} 字，将进行全文处理，预计调用 AI $estimatedCalls 次。是否继续？",
                    "确认 AI 调用",
                    JOptionPane.YES_NO_OPTION
                )
                if (answer != JOptionPane.YES_OPTION) return@addActionListener
            }
            runAsync(button, status, "正在调用 AI...", task = {
                val data = state.state
                val questions = AiClient.generateFromMaterial(
                    data.baseUrl,
                    state.apiKey(),
                    data.model,
                    path.fileName.toString(),
                    text,
                    direction.text,
                    count.value as Int
                ) { message -> SwingUtilities.invokeLater { status.text = message } }
                state.addQuestions(questions, "资料：${path.fileName}")
                questions.size
            }, done = { generated ->
                status.text = "已生成并保存 $generated 道题"
                onGenerated()
            })
        }
        return formPanel("资料" to fileRow, "出题方向" to direction, "题目数量" to count, "" to JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(button); add(preview) })
    }

    private fun generate(button: JButton, source: String, prompt: () -> String) {
        runAsync(button, status, "正在调用 AI...", task = {
            val data = state.state
            val questions = AiClient.generate(data.baseUrl, state.apiKey(), data.model, prompt())
            state.addQuestions(questions, source)
            questions.size
        }, done = { count ->
            status.text = "已生成并保存 $count 道题"
            onGenerated()
        })
    }
}

private class PracticePanel(private val state: StudyState) : JPanel(BorderLayout(6, 6)) {
    private val model = DefaultListModel<StudyQuestion>()
    private val list = JList(model)
    private val keyword = JTextField()
    private val type = JComboBox(arrayOf("全部", "qa", "single_choice"))
    private val tag = JTextField()
    private val source = JTextField()
    private val dueOnly = JCheckBox("今日")
    private val favoriteOnly = JCheckBox("收藏")
    private val wrongOnly = JCheckBox("错题")
    private val question = JTextArea()
    private val reference = JTextArea("点“答案”再自测")
    private val stats = JLabel()
    private val status = JLabel()
    private var currentQuestions: List<StudyQuestion> = emptyList()

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                val item = value as? StudyQuestion
                val today = LocalDate.now().toEpochDay()
                return super.getListCellRendererComponent(list, item?.let { "${if (it.favorite) "★" else ""}${it.reviewStatus(today)} · ${it.difficulty} · ${it.title}" }, index, selected, focus)
            }
        }
        list.addListSelectionListener { if (!it.valueIsAdjusting) show(list.selectedValue) }
        listOf(question, reference).forEach { area -> area.isEditable = false; area.lineWrap = true; area.wrapStyleWord = true }
        add(toolbar(), BorderLayout.NORTH)
        add(JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(question), JScrollPane(reference)).apply { resizeWeight = 0.6 }, BorderLayout.CENTER)
        add(actions(), BorderLayout.SOUTH)
        refresh()
    }

    private fun toolbar(): JPanel {
        val library = JButton("题库")
        val refresh = JButton("搜")
        val more = JButton("更多")
        library.addActionListener { showLibrary() }
        refresh.addActionListener { refresh() }
        more.addActionListener {
            JOptionPane.showMessageDialog(this, formPanel("标签" to tag, "来源" to source), "更多筛选", JOptionPane.PLAIN_MESSAGE)
            refresh()
        }
        return JPanel(BorderLayout(4, 4)).apply {
            add(JPanel(BorderLayout(4, 0)).apply { add(keyword, BorderLayout.CENTER); add(library, BorderLayout.EAST) }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(type); add(dueOnly); add(favoriteOnly); add(wrongOnly); add(refresh); add(more); add(stats) }, BorderLayout.CENTER)
        }
    }

    private fun actions(): JPanel {
        val answer = JButton("答案")
        val wrong = JButton("不会")
        val known = JButton("会了")
        val next = JButton("下一题")
        val more = JButton("更多")
        answer.addActionListener { list.selectedValue?.let { reference.text = "参考答案：\n${it.answerText()}" } }
        wrong.addActionListener { updateReview(false) }
        known.addActionListener { updateReview(true) }
        next.addActionListener { nextQuestion() }
        more.addActionListener { showMoreActions() }
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(answer); add(wrong); add(known); add(next); add(more); add(status) }
    }

    private fun showLibrary() {
        JOptionPane.showMessageDialog(this, JScrollPane(list).apply { preferredSize = Dimension(360, 420) }, "题库", JOptionPane.PLAIN_MESSAGE)
    }

    private fun showMoreActions() {
        val favorite = JButton("收藏/取消")
        val markWrong = JButton("错题/取消")
        val mastered = JButton("已掌握")
        val review = JButton("AI 批改")
        val followUp = JButton("AI 追问")
        val export = JButton("导出")
        val import = JButton("导入")
        val delete = JButton("删除")
        favorite.addActionListener { list.selectedValue?.let { it.favorite = !it.favorite; refresh() } }
        markWrong.addActionListener { list.selectedValue?.let { it.isWrong = !it.isWrong; refresh() } }
        mastered.addActionListener { list.selectedValue?.let { it.reviewLevel = 5; it.isWrong = false; it.markReview(true); refresh() } }
        review.addActionListener { askAndReview(review) }
        followUp.addActionListener { ai(followUp, null) }
        export.addActionListener { exportQuestions() }
        import.addActionListener { importQuestions() }
        delete.addActionListener { list.selectedValue?.let { if (confirm("删除这道题？")) { state.state.questions.remove(it); refresh() } } }
        JOptionPane.showMessageDialog(this, JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(favorite); add(markWrong); add(mastered); add(review); add(followUp); add(export); add(import); add(delete) }, "更多操作", JOptionPane.PLAIN_MESSAGE)
    }

    private fun show(item: StudyQuestion?) {
        question.text = item?.let {
            buildString {
                append(it.title)
                if (it.options.isNotEmpty()) append("\n\n").append(it.options.joinToString("\n") { option -> "${option.key}. ${option.text}" })
                append("\n\n来源：${it.source}\n标签：${it.tags.joinToString("、")}")
                append("\n状态：${it.reviewStatus()}　下次：${reviewDate(it.nextReviewEpochDay)}")
            }
        }.orEmpty()
        reference.text = "先心里答一遍，再点“答案”。"
        status.text = ""
    }

    private fun updateReview(known: Boolean) {
        val item = list.selectedValue ?: return
        item.markReview(known)
        status.text = "下次：${reviewDate(item.nextReviewEpochDay)}"
        refresh()
        nextQuestion()
    }

    private fun nextQuestion() {
        if (currentQuestions.isEmpty()) return
        list.selectedIndex = (list.selectedIndex + 1).let { if (it >= currentQuestions.size) 0 else it }
    }

    private fun askAndReview(button: JButton) {
        val answerInput = JTextArea().apply { lineWrap = true; wrapStyleWord = true }
        if (JOptionPane.showConfirmDialog(this, JScrollPane(answerInput).apply { preferredSize = Dimension(360, 180) }, "写下你的回答", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            ai(button, answerInput.text)
        }
    }

    private fun ai(button: JButton, userAnswer: String?) {
        val item = list.selectedValue ?: return
        runAsync(button, status, "AI 中...", task = {
            val data = state.state
            if (userAnswer != null) AiClient.reviewAnswer(data.baseUrl, state.apiKey(), data.model, item, userAnswer)
            else AiClient.followUp(data.baseUrl, state.apiKey(), data.model, item)
        }, done = { reference.text = it; status.text = "AI 完成" })
    }

    private fun exportQuestions() {
        val chooser = JFileChooser().apply { selectedFile = java.io.File("interview-questions.json") }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        chooser.selectedFile.writeText(QuestionBank.exportJson(state.state.questions), Charsets.UTF_8)
        status.text = "已导出 ${state.state.questions.size} 道"
    }

    private fun importQuestions() {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val imported = QuestionBank.importJson(chooser.selectedFile.readText(Charsets.UTF_8))
        state.state.questions.addAll(imported)
        status.text = "已导入 ${imported.size} 道"
        refresh()
    }

    fun refresh() {
        val selectedId = list.selectedValue?.id
        val today = LocalDate.now().toEpochDay()
        val filters = QuestionFilters(
            keyword = keyword.text,
            type = (type.selectedItem as String).takeUnless { it == "全部" }.orEmpty(),
            tag = tag.text,
            source = source.text,
            dueOnly = dueOnly.isSelected,
            favoriteOnly = favoriteOnly.isSelected,
            wrongOnly = wrongOnly.isSelected
        )
        currentQuestions = QuestionBank.filter(state.state.questions, filters, today)
        val summary = QuestionBank.stats(state.state.questions, today)
        stats.text = "${summary.due}/${summary.total}"
        model.clear()
        currentQuestions.forEach(model::addElement)
        val index = currentQuestions.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: if (currentQuestions.isEmpty()) -1 else 0
        list.selectedIndex = index
        if (currentQuestions.isEmpty()) question.text = if (state.state.questions.isEmpty()) "暂无题目，先去生成。" else "没搜到，换个条件。"
    }

    private fun reviewDate(epochDay: Long): String {
        val today = LocalDate.now().toEpochDay()
        return when (val delta = epochDay - today) {
            0L -> "今天"
            1L -> "明天"
            else -> if (delta > 1) "${delta} 天后" else LocalDate.ofEpochDay(epochDay).toString()
        }
    }

    private fun confirm(message: String) = JOptionPane.showConfirmDialog(this, message, "确认", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
}
private class SettingsPanel(private val state: StudyState) : JPanel(BorderLayout()) {
    private val baseUrl = JTextField(state.state.baseUrl)
    private val model = JTextField(state.state.model)
    private val apiKey = JPasswordField(state.apiKey())
    private val status = JLabel("API Key 保存到 JetBrains Password Safe")

    init {
        val save = JButton("保存")
        val test = JButton("测试连接")
        val fetchModels = JButton("获取模型")
        save.addActionListener { save(); status.text = "已保存" }
        test.addActionListener {
            save()
            runAsync(test, status, "正在测试连接...", task = {
                AiClient.test(baseUrl.text, String(apiKey.password), model.text)
                Unit
            }, done = { status.text = "连接成功" })
        }
        fetchModels.addActionListener {
            save()
            runAsync(fetchModels, status, "正在获取模型...", task = {
                AiClient.models(baseUrl.text, String(apiKey.password))
            }, done = { models ->
                val selected = JOptionPane.showInputDialog(this, "选择模型", "模型列表", JOptionPane.PLAIN_MESSAGE, null, models.toTypedArray(), model.text)
                if (selected != null) {
                    model.text = selected.toString()
                    save()
                    status.text = "已选择模型：${model.text}"
                } else {
                    status.text = "已获取 ${models.size} 个模型"
                }
            })
        }
        add(formPanel(
            "Base URL" to baseUrl,
            "API Key" to apiKey,
            "模型名" to model,
            "" to JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(save); add(test); add(fetchModels) },
            "" to status
        ), BorderLayout.NORTH)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    private fun save() {
        state.state.baseUrl = baseUrl.text.trim()
        state.state.model = model.text.trim()
        state.saveApiKey(String(apiKey.password))
    }
}

private fun formPanel(vararg rows: Pair<String, Component>) = JPanel(GridBagLayout()).apply {
    rows.forEachIndexed { index, (label, component) ->
        add(JLabel(label), GridBagConstraints().apply {
            gridx = 0; gridy = index; anchor = GridBagConstraints.NORTHWEST; insets = Insets(6, 4, 6, 8)
        })
        add(component, GridBagConstraints().apply {
            gridx = 1; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(6, 4, 6, 4)
        })
    }
}

private fun <T> runAsync(button: JButton, status: JLabel, message: String, task: () -> T, done: (T) -> Unit) {
    button.isEnabled = false
    status.text = message
    ApplicationManager.getApplication().executeOnPooledThread {
        runCatching(task).fold(
            onSuccess = { value -> SwingUtilities.invokeLater { button.isEnabled = true; done(value) } },
            onFailure = { error -> SwingUtilities.invokeLater { button.isEnabled = true; status.text = error.message ?: "操作失败" } }
        )
    }
}

