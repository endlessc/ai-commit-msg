package com.lememo.aicommit.prompt

import com.lememo.aicommit.llm.ChatMessage
import com.lememo.aicommit.model.DiffBundle

class PromptBuilder(
    private val language: String,
    private val style: String,
    private val customSystemPrompt: String = "",
    private val recentSubjects: List<String> = emptyList(),
) {

    fun build(bundle: DiffBundle, repoName: String, branch: String): List<ChatMessage> {
        val system = customSystemPrompt.ifBlank { PromptTemplates.system(language, style) }

        val user = buildString {
            appendLine("仓库：${repoName.ifBlank { "(未知)" }}")
            appendLine("分支：$branch")
            appendLine()
            appendLine("## 变更概览")
            appendLine(bundle.overview.ifBlank { "(无变更概览)" })
            if (recentSubjects.isNotEmpty()) {
                appendLine()
                appendLine("## 本仓库最近的提交信息（仅用于对齐措辞与 scope 命名，不要照抄）")
                recentSubjects.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## 变更内容")
            appendLine(bundle.diffText.ifBlank { "(没有可用的 diff)" })
        }

        return listOf(
            ChatMessage.system(system.trim()),
            ChatMessage.user(user.trim()),
        )
    }
}
