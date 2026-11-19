package com.lememo.aicommit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * 应用级配置：一次配置，所有项目生效。
 */
@Service(Service.Level.APP)
@State(name = "AiCommitSettings", storages = [Storage("ai-commit-message.xml")])
class AiCommitSettings : PersistentStateComponent<AiCommitSettings.State> {

    private var current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        current = state
    }

    fun isConfigured(): Boolean {
        val state = current
        if (state.baseUrl.isBlank() || state.model.isBlank()) return false
        if (isLocalEndpoint(state.baseUrl)) return true
        return !ApiKeyStore.get(state.baseUrl).isNullOrBlank()
    }

    private fun isLocalEndpoint(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("localhost") ||
            lower.contains("127.0.0.1") ||
            lower.contains("0.0.0.0") ||
            lower.contains("[::1]")
    }

    data class State(
        var providerId: String = ProviderPreset.DEEPSEEK.id,
        var baseUrl: String = ProviderPreset.DEEPSEEK.baseUrl,
        var model: String = ProviderPreset.DEEPSEEK.model,
        /** zh | en */
        var language: String = "zh",
        /** conventional | plain */
        var style: String = "conventional",
        /** replace | onlyIfEmpty */
        var writeMode: String = "replace",
        var maxDiffChars: Int = 40_000,
        var maxFileLines: Int = 400,
        var maxTokens: Int = 2_048,
        var temperature: Double = 0.2,
        var connectTimeoutMs: Int = 10_000,
        var readTimeoutMs: Int = 120_000,
        var ignoreIdeaFiles: Boolean = true,
        var streamEnabled: Boolean = true,
        var recentSubjectsEnabled: Boolean = true,
        var recentSubjectsCount: Int = 30,
        var customSystemPrompt: String = "",
    )

    companion object {
        fun getInstance(): AiCommitSettings = service()
    }
}
