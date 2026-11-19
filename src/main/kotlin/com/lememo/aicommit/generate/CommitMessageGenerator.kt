package com.lememo.aicommit.generate

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.lememo.aicommit.collect.DiffFilter
import com.lememo.aicommit.collect.DiffTrimmer
import com.lememo.aicommit.collect.GitCli
import com.lememo.aicommit.collect.GitDiffCollector
import com.lememo.aicommit.llm.LlmError
import com.lememo.aicommit.llm.LlmException
import com.lememo.aicommit.llm.OpenAiCompatibleClient
import com.lememo.aicommit.model.ChangeItem
import com.lememo.aicommit.prompt.PromptBuilder
import com.lememo.aicommit.settings.AiCommitSettings
import com.lememo.aicommit.settings.ApiKeyStore

/**
 * 编排：采集差异 → 裁剪 → 组装 prompt → 调用模型 → 清洗输出。
 */
class CommitMessageGenerator(
    @Suppress("unused") private val project: Project,
    private val settings: AiCommitSettings.State,
) {

    fun generate(
        items: List<ChangeItem>,
        indicator: ProgressIndicator,
        onDelta: ((String) -> Unit)? = null,
    ): String {
        indicator.text = "读取变更差异…"
        indicator.checkCanceled()

        val filter = DiffFilter(settings.ignoreIdeaFiles)
        val collected = GitDiffCollector(filter).collect(items, indicator)
        val bundle = DiffTrimmer(settings.maxDiffChars, settings.maxFileLines).trim(collected)

        if (bundle.diffText.isBlank() && bundle.overview.isBlank()) {
            throw LlmException(
                "没有可分析的文本变更（可能全是二进制、锁文件或被过滤的生成物）",
                LlmError.EMPTY_RESPONSE,
            )
        }

        indicator.checkCanceled()
        indicator.text = "调用模型生成提交信息…"

        val primaryRepo = items.first().repoRoot
        val recentSubjects = if (settings.recentSubjectsEnabled) {
            GitCli.recentSubjects(primaryRepo, settings.recentSubjectsCount)
        } else {
            emptyList()
        }

        val messages = PromptBuilder(
            language = settings.language,
            style = settings.style,
            customSystemPrompt = settings.customSystemPrompt,
            recentSubjects = recentSubjects,
        ).build(
            bundle = bundle,
            repoName = primaryRepo.fileName?.toString().orEmpty(),
            branch = GitCli.branch(primaryRepo),
        )

        val client = OpenAiCompatibleClient(
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = ApiKeyStore.get(settings.baseUrl),
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            connectTimeoutMs = settings.connectTimeoutMs,
            readTimeoutMs = settings.readTimeoutMs,
        )

        val raw = if (onDelta != null) {
            client.stream(messages, indicator, onDelta)
        } else {
            client.complete(messages, indicator)
        }
        val cleaned = MessagePostProcessor.clean(raw)
        if (cleaned.isBlank()) {
            throw LlmException("模型返回了空内容", LlmError.EMPTY_RESPONSE)
        }
        return cleaned
    }
}
