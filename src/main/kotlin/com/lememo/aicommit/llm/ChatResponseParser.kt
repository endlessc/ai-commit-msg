package com.lememo.aicommit.llm

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * OpenAI 兼容响应的解析与错误映射。抽成纯函数，便于单测且不依赖平台类。
 */
internal object ChatResponseParser {

    fun parse(raw: String, allowEmptyContent: Boolean = false): String {
        val root = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (e: Exception) {
            throw LlmException("响应不是合法 JSON：${raw.take(200)}", LlmError.BAD_RESPONSE, e)
        }

        root.getAsJsonObject("error")?.let { error ->
            val message = runCatching { error.get("message").asString }.getOrNull() ?: error.toString()
            throw LlmException("模型服务返回错误：${message.take(300)}", LlmError.BAD_RESPONSE)
        }

        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw LlmException("响应缺少 choices 字段", LlmError.BAD_RESPONSE)
        }

        val first = choices[0].asJsonObject
        val message = first.getAsJsonObject("message")
        val finishReason = first.get("finish_reason")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asString }.getOrNull() }
        val content = message?.get("content")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asString }.getOrNull() }
            ?.trim()
        val reasoning = message?.get("reasoning_content")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asString }.getOrNull() }

        if (content.isNullOrBlank()) {
            if (allowEmptyContent) return ""
            if (finishReason == "length") {
                val hint = if (!reasoning.isNullOrBlank()) {
                    "模型把输出预算用在了思考过程（reasoning_content）上，没有产出正文。请把 max_tokens 调大，建议 2048 以上"
                } else {
                    "模型输出达到 max_tokens 上限，没有产出正文。推理型模型需要更大的输出预算，请调大 max_tokens"
                }
                throw LlmException(hint, LlmError.TRUNCATED)
            }
            throw LlmException("模型返回了空内容", LlmError.EMPTY_RESPONSE)
        }
        return content
    }

    /** 解析 SSE 单条 data 里的增量正文；角色帧、心跳、无 content 的帧返回空串。 */
    fun parseSseDelta(data: String): String {
        val root = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return ""

        root.getAsJsonObject("error")?.let { error ->
            val message = runCatching { error.get("message").asString }.getOrNull() ?: error.toString()
            throw LlmException("模型服务返回错误：${message.take(300)}", LlmError.BAD_RESPONSE)
        }

        val choices = root.getAsJsonArray("choices") ?: return ""
        if (choices.size() == 0) return ""
        val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return ""
        return delta.get("content")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asString }.getOrNull() }
            .orEmpty()
    }

    fun mapHttpError(code: Int, detail: String?, cause: Throwable? = null): LlmException {
        val suffix = detail
            ?.take(200)
            ?.takeIf { it.isNotBlank() && !it.startsWith("Server returned HTTP") }
            ?.let { "（服务端返回：$it）" }
            .orEmpty()

        return when {
            code == 401 || code == 403 ->
                LlmException("API Key 无效或无权限（HTTP $code）$suffix", LlmError.AUTH, cause)

            code == 404 ->
                LlmException("接口地址或模型名不正确（HTTP 404）$suffix", LlmError.NOT_FOUND, cause)

            code == 429 ->
                LlmException("请求过于频繁或额度不足（HTTP 429）$suffix", LlmError.RATE_LIMIT, cause)

            code in 500..599 ->
                LlmException("模型服务异常（HTTP $code）$suffix", LlmError.SERVER, cause)

            else ->
                LlmException("请求失败（HTTP $code）$suffix", LlmError.NETWORK, cause)
        }
    }
}
