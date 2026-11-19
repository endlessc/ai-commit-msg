package com.lememo.aicommit.llm

import com.intellij.openapi.progress.ProgressIndicator

data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
    }
}

interface ChatClient {
    /** 同步调用，失败抛 [LlmException]；[indicator] 为空表示不可取消。 */
    fun complete(messages: List<ChatMessage>, indicator: ProgressIndicator? = null): String

    /**
     * 流式调用：每收到一段增量就回调一次 [onDelta]，返回拼接后的完整文本。
     * 默认实现退化为一次性返回，便于其它实现只需要写 [complete]。
     */
    fun stream(
        messages: List<ChatMessage>,
        indicator: ProgressIndicator?,
        onDelta: (String) -> Unit,
    ): String {
        val text = complete(messages, indicator)
        onDelta(text)
        return text
    }
}

enum class LlmError {
    CONFIG,
    AUTH,
    NOT_FOUND,
    RATE_LIMIT,
    TIMEOUT,
    NETWORK,
    SERVER,
    EMPTY_RESPONSE,
    TRUNCATED,
    BAD_RESPONSE,
}

class LlmException(
    message: String,
    val kind: LlmError,
    cause: Throwable? = null,
) : Exception(message, cause)
