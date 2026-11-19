package com.lememo.aicommit.ui

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.HttpRequests
import com.lememo.aicommit.llm.ChatMessage
import com.lememo.aicommit.llm.LlmException
import com.lememo.aicommit.llm.OpenAiCompatibleClient
import com.lememo.aicommit.settings.AiCommitSettings
import com.lememo.aicommit.settings.ApiKeyStore
import com.lememo.aicommit.settings.ProviderPreset
import java.net.HttpURLConnection
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPasswordField

class AiCommitConfigurable : Configurable {

    private var panelComponent: JComponent? = null
    private var loading = false

    private val providerCombo = ComboBox(ProviderPreset.entries.toTypedArray())
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val apiKeyField = JPasswordField()
    private val languageCombo = ComboBox(arrayOf("中文", "English"))
    private val styleCombo = ComboBox(arrayOf("Conventional Commits", "纯文本标题"))
    private val writeModeCombo = ComboBox(arrayOf("覆盖输入框", "仅当输入框为空时写入"))
    private val maxDiffCharsSpinner = JBIntSpinner(40_000, 1_000, 200_000, 1_000)
    private val maxFileLinesSpinner = JBIntSpinner(400, 20, 5_000, 20)
    private val maxTokensSpinner = JBIntSpinner(2_048, 64, 32_768, 256)
    private val temperatureField = JBTextField()
    private val connectTimeoutSpinner = JBIntSpinner(10_000, 1_000, 120_000, 1_000)
    private val readTimeoutSpinner = JBIntSpinner(120_000, 5_000, 600_000, 5_000)
    private val streamCheck = JCheckBox("流式输出（边生成边写入）")
    private val ignoreIdeaCheck = JCheckBox("忽略 .idea/ 与 *.iml")
    private val recentCheck = JCheckBox("参考最近提交的措辞")
    private val recentCountSpinner = JBIntSpinner(30, 0, 200, 5)
    private val customPromptArea = JBTextArea(4, 60)
    private val testButton = JButton("测试连接")
    private val modelsButton = JButton("获取模型列表")
    private var busy = false

    init {
        providerCombo.addActionListener {
            if (loading) return@addActionListener
            val preset = providerCombo.selectedItem as? ProviderPreset ?: return@addActionListener
            if (preset == ProviderPreset.CUSTOM) return@addActionListener
            baseUrlField.text = preset.baseUrl
            modelField.text = preset.model
        }
        testButton.addActionListener { testConnection() }
        modelsButton.addActionListener { loadModels() }
    }

    override fun getDisplayName(): String = "AI Commit Message"

    override fun createComponent(): JComponent {
        if (panelComponent == null) {
            panelComponent = panel {
                group("模型服务") {
                    row("服务商") { cell(providerCombo).align(AlignX.FILL) }
                    row("Base URL") { cell(baseUrlField).align(AlignX.FILL) }
                    row("模型") { cell(modelField).align(AlignX.FILL) }
                    row("API Key") { cell(apiKeyField).align(AlignX.FILL) }
                    row {
                        cell(testButton)
                        cell(modelsButton)
                    }
                }
                group("生成选项") {
                    row("输出语言") { cell(languageCombo) }
                    row("提交信息格式") { cell(styleCombo) }
                    row("写入方式") { cell(writeModeCombo) }
                    row { cell(streamCheck) }
                    row { cell(ignoreIdeaCheck) }
                    row {
                        cell(recentCheck)
                        cell(recentCountSpinner)
                    }
                }
                group("高级") {
                    row("diff 字符上限") { cell(maxDiffCharsSpinner) }
                    row("单文件行数上限") { cell(maxFileLinesSpinner) }
                    row("max_tokens") { cell(maxTokensSpinner) }
                    row("temperature") { cell(temperatureField).align(AlignX.FILL) }
                    row("连接超时（毫秒）") { cell(connectTimeoutSpinner) }
                    row("读取超时（毫秒）") { cell(readTimeoutSpinner) }
                    row("自定义 system prompt") { cell(customPromptArea).align(AlignX.FILL) }
                }
            }
            reset()
        }
        return panelComponent!!
    }

    override fun isModified(): Boolean {
        if (panelComponent == null) return false
        return readForm() != formOf(AiCommitSettings.getInstance().state)
    }

    override fun apply() {
        val values = readForm()
        val state = AiCommitSettings.getInstance().state
        state.providerId = values.providerId
        state.baseUrl = values.baseUrl
        state.model = values.model
        state.language = values.language
        state.style = values.style
        state.writeMode = values.writeMode
        state.maxDiffChars = values.maxDiffChars
        state.maxFileLines = values.maxFileLines
        state.maxTokens = values.maxTokens
        state.temperature = values.temperature
        state.connectTimeoutMs = values.connectTimeoutMs
        state.readTimeoutMs = values.readTimeoutMs
        state.streamEnabled = values.streamEnabled
        state.ignoreIdeaFiles = values.ignoreIdeaFiles
        state.recentSubjectsEnabled = values.recentEnabled
        state.recentSubjectsCount = values.recentCount
        state.customSystemPrompt = values.customPrompt
        ApiKeyStore.set(values.baseUrl, values.apiKey)
    }

    override fun reset() {
        val state = AiCommitSettings.getInstance().state
        loading = true
        try {
            providerCombo.selectedItem = ProviderPreset.byId(state.providerId)
            baseUrlField.text = state.baseUrl
            modelField.text = state.model
            apiKeyField.text = ApiKeyStore.get(state.baseUrl).orEmpty()
            languageCombo.selectedIndex = if (state.language == "en") 1 else 0
            styleCombo.selectedIndex = if (state.style == "plain") 1 else 0
            writeModeCombo.selectedIndex = if (state.writeMode == "onlyIfEmpty") 1 else 0
            maxDiffCharsSpinner.number = state.maxDiffChars
            maxFileLinesSpinner.number = state.maxFileLines
            maxTokensSpinner.number = state.maxTokens
            temperatureField.text = state.temperature.toString()
            connectTimeoutSpinner.number = state.connectTimeoutMs
            readTimeoutSpinner.number = state.readTimeoutMs
            streamCheck.isSelected = state.streamEnabled
            ignoreIdeaCheck.isSelected = state.ignoreIdeaFiles
            recentCheck.isSelected = state.recentSubjectsEnabled
            recentCountSpinner.number = state.recentSubjectsCount
            customPromptArea.text = state.customSystemPrompt
        } finally {
            loading = false
        }
    }

    override fun disposeUIResources() {
        panelComponent = null
    }

    override fun getPreferredFocusedComponent(): JComponent = baseUrlField

    private fun readForm(): FormValues = FormValues(
        providerId = (providerCombo.selectedItem as? ProviderPreset ?: ProviderPreset.CUSTOM).id,
        baseUrl = baseUrlField.text.trim(),
        model = modelField.text.trim(),
        apiKey = String(apiKeyField.password).trim(),
        language = if (languageCombo.selectedIndex == 1) "en" else "zh",
        style = if (styleCombo.selectedIndex == 1) "plain" else "conventional",
        writeMode = if (writeModeCombo.selectedIndex == 1) "onlyIfEmpty" else "replace",
        maxDiffChars = maxDiffCharsSpinner.number,
        maxFileLines = maxFileLinesSpinner.number,
        maxTokens = maxTokensSpinner.number,
        temperature = temperatureField.text.trim().toDoubleOrNull() ?: 0.2,
        connectTimeoutMs = connectTimeoutSpinner.number,
        readTimeoutMs = readTimeoutSpinner.number,
        streamEnabled = streamCheck.isSelected,
        ignoreIdeaFiles = ignoreIdeaCheck.isSelected,
        recentEnabled = recentCheck.isSelected,
        recentCount = recentCountSpinner.number,
        customPrompt = customPromptArea.text.trim(),
    )

    private fun formOf(state: AiCommitSettings.State): FormValues = FormValues(
        providerId = state.providerId,
        baseUrl = state.baseUrl,
        model = state.model,
        apiKey = ApiKeyStore.get(state.baseUrl).orEmpty(),
        language = state.language,
        style = state.style,
        writeMode = state.writeMode,
        maxDiffChars = state.maxDiffChars,
        maxFileLines = state.maxFileLines,
        maxTokens = state.maxTokens,
        temperature = state.temperature,
        connectTimeoutMs = state.connectTimeoutMs,
        readTimeoutMs = state.readTimeoutMs,
        streamEnabled = state.streamEnabled,
        ignoreIdeaFiles = state.ignoreIdeaFiles,
        recentEnabled = state.recentSubjectsEnabled,
        recentCount = state.recentSubjectsCount,
        customPrompt = state.customSystemPrompt,
    )

    private fun beginBusy(button: JButton, text: String) {
        busy = true
        testButton.isEnabled = false
        modelsButton.isEnabled = false
        button.text = text
        button.icon = AnimatedIcon.Default.INSTANCE
        button.horizontalTextPosition = SwingConstants.RIGHT
    }

    private fun endBusy() {
        busy = false
        testButton.isEnabled = true
        modelsButton.isEnabled = true
        testButton.text = "测试连接"
        modelsButton.text = "获取模型列表"
        testButton.icon = null
        modelsButton.icon = null
    }

    private fun modelsEndpoint(): String {
        val base = baseUrlField.text.trim().trimEnd('/')
        if (base.endsWith("/models")) return base
        if (base.endsWith("/chat/completions")) return base.removeSuffix("/chat/completions") + "/models"
        return if (Regex("/v\\d+$").containsMatchIn(base)) "$base/models" else "$base/v1/models"
    }

    private fun loadModels() {
        if (busy) return
        val baseUrl = baseUrlField.text.trim()
        if (baseUrl.isBlank()) {
            Messages.showWarningDialog("请先填写 Base URL。", "AI Commit Message")
            return
        }
        val apiKey = String(apiKeyField.password).trim()
        val url = modelsEndpoint()
        val current = modelField.text.trim()

        beginBusy(modelsButton, "获取中…")
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(null, "获取模型列表", true) {
                override fun run(indicator: ProgressIndicator) {
                    val result: Result<Pair<Int, String>> = try {
                        Result.success(
                            HttpRequests.request(url)
                                .accept("application/json")
                                .connectTimeout(10_000)
                                .readTimeout(30_000)
                                .throwStatusCodeException(false)
                                .tuner { connection ->
                                    if (apiKey.isNotBlank()) {
                                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                                    }
                                }
                                .connect { request ->
                                    val code = (request.connection as? HttpURLConnection)?.responseCode ?: -1
                                    val text = if (code in 200..299) {
                                        request.readString(indicator) ?: ""
                                    } else {
                                        runCatching { request.readError() }.getOrNull().orEmpty()
                                    }
                                    code to text
                                },
                        )
                    } catch (e: ProcessCanceledException) {
                        ApplicationManager.getApplication().invokeLater { endBusy() }
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        try {
                            result.fold(
                                onSuccess = { (code, body) ->
                                    if (code !in 200..299) {
                                        Messages.showErrorDialog(
                                            "获取模型列表失败（HTTP $code）" +
                                                body.take(300).let { if (it.isBlank()) "" else "：$it" },
                                            "AI Commit Message",
                                        )
                                        return@fold
                                    }
                                    val ids = parseModelIds(body)
                                    if (ids.isEmpty()) {
                                        Messages.showWarningDialog("接口没有返回任何模型 ID。", "AI Commit Message")
                                    } else {
                                        JBPopupFactory.getInstance()
                                            .createPopupChooserBuilder(ids)
                                            .setTitle("选择模型（共 ${ids.size} 个）")
                                            .setItemChosenCallback { chosen -> modelField.text = chosen }
                                            .createPopup()
                                            .showUnderneathOf(modelField)
                                    }
                                },
                                onFailure = { error ->
                                    Messages.showErrorDialog(
                                        error.message ?: error.javaClass.simpleName,
                                        "AI Commit Message",
                                    )
                                },
                            )
                        } finally {
                            endBusy()
                        }
                    }
                }
            },
        )
    }

    private fun parseModelIds(body: String): List<String> {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return emptyList()
        val data = root.getAsJsonArray("data") ?: return emptyList()
        return data.mapNotNull { element ->
            runCatching { element.asJsonObject.get("id").asString }.getOrNull()
        }.distinct().take(300)
    }

    private fun testConnection() {
        if (busy) return
        val values = readForm()
        if (values.baseUrl.isBlank() || values.model.isBlank()) {
            Messages.showWarningDialog("请先填写 Base URL 和模型名称。", "AI Commit Message")
            return
        }

        beginBusy(testButton, "测试中…")
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(null, "测试模型连接", true) {
                override fun run(indicator: ProgressIndicator) {
                    val client = OpenAiCompatibleClient(
                        baseUrl = values.baseUrl,
                        model = values.model,
                        apiKey = values.apiKey.takeIf { it.isNotBlank() },
                        temperature = 0.7,
                        maxTokens = 64,
                        connectTimeoutMs = values.connectTimeoutMs,
                        readTimeoutMs = values.readTimeoutMs,
                    )
                    val result: Result<String> = try {
                        Result.success(client.testConnection(listOf(ChatMessage.user("ping")), indicator))
                    } catch (e: ProcessCanceledException) {
                        ApplicationManager.getApplication().invokeLater { endBusy() }
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        try {
                            result.fold(
                                onSuccess = {
                                    Messages.showInfoMessage(
                                        "连接成功：地址可达、鉴权通过、模型可用。",
                                        "AI Commit Message",
                                    )
                                },
                                onFailure = { error ->
                                    val message = if (error is LlmException) {
                                        error.message
                                    } else {
                                        error.message ?: error.javaClass.simpleName
                                    }
                                    Messages.showErrorDialog(
                                        "$message\n\n模型：${values.model}\n地址：${client.endpoint()}",
                                        "AI Commit Message",
                                    )
                                },
                            )
                        } finally {
                            endBusy()
                        }
                    }
                }
            },
        )
    }

    private data class FormValues(
        val providerId: String,
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val language: String,
        val style: String,
        val writeMode: String,
        val maxDiffChars: Int,
        val maxFileLines: Int,
        val maxTokens: Int,
        val temperature: Double,
        val connectTimeoutMs: Int,
        val readTimeoutMs: Int,
        val streamEnabled: Boolean,
        val ignoreIdeaFiles: Boolean,
        val recentEnabled: Boolean,
        val recentCount: Int,
        val customPrompt: String,
    )
}
