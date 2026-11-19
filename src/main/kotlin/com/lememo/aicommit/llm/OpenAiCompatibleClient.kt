package com.lememo.aicommit.llm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

/**
 * OpenAI 兼容的 /chat/completions 客户端。
 *
 * 走 [HttpRequests]，自动继承 IDE 的代理设置。这里刻意关掉它自带的「非 2xx 抛异常」行为，
 * 自己读状态码和错误正文——否则 4xx 会把服务端的说明丢掉，只剩一句
 * "Server returned HTTP response code: 400"。
 */
class OpenAiCompatibleClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val temperature: Double = 0.2,
    private val maxTokens: Int = 2_048,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 120_000,
) : ChatClient {

    override fun complete(messages: List<ChatMessage>, indicator: ProgressIndicator?): String =
        execute(messages, indicator, allowEmptyContent = false)

    /**
     * 只验证「地址可达 + 鉴权通过 + 模型存在」：HTTP 200 即算成功。
     * 推理模型在很小的 max_tokens 下会只输出思考过程、正文为空，不能据此判定失败。
     */
    fun testConnection(messages: List<ChatMessage>, indicator: ProgressIndicator?): String =
        execute(messages, indicator, allowEmptyContent = true, minimalPayload = true)

    override fun stream(
        messages: List<ChatMessage>,
        indicator: ProgressIndicator?,
        onDelta: (String) -> Unit,
    ): String {
        if (baseUrl.isBlank()) throw LlmException("未配置 Base URL", LlmError.CONFIG)
        if (model.isBlank()) throw LlmException("未配置模型名称", LlmError.CONFIG)

        val payload = buildPayload(messages, minimal = false, stream = true)
        return try {
            HttpRequests.post(endpoint(), "")
                .accept("text/event-stream")
                .connectTimeout(connectTimeoutMs)
                .readTimeout(readTimeoutMs)
                .throwStatusCodeException(false)
                .tuner { connection ->
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "text/event-stream")
                    apiKey?.takeIf { it.isNotBlank() }?.let {
                        connection.setRequestProperty("Authorization", "Bearer $it")
                    }
                }
                .connect { request ->
                    request.write(payload)
                    val code = (request.connection as? HttpURLConnection)?.responseCode ?: -1
                    if (code !in 200..299) {
                        val body = runCatching { request.readError() }.getOrNull().orEmpty()
                        throw ChatResponseParser.mapHttpError(code, body)
                    }
                    readStream(request, indicator, onDelta)
                }
        } catch (e: HttpRequests.HttpStatusException) {
            throw ChatResponseParser.mapHttpError(e.statusCode, e.message, e)
        } catch (e: SocketTimeoutException) {
            throw LlmException(
                "请求超时（连接 ${connectTimeoutMs}ms / 读取 ${readTimeoutMs}ms），可调大超时或减小 diff 预算",
                LlmError.TIMEOUT,
                e,
            )
        } catch (e: IOException) {
            throw LlmException("网络请求失败：${e.message}", LlmError.NETWORK, e)
        }
    }

    private fun readStream(
        request: HttpRequests.Request,
        indicator: ProgressIndicator?,
        onDelta: (String) -> Unit,
    ): String {
        val accumulated = StringBuilder()
        val raw = StringBuilder()
        var sawSse = false

        val reader = if (indicator != null) request.getReader(indicator) else request.getReader()
        reader.use {
            while (true) {
                indicator?.checkCanceled()
                val line = it.readLine() ?: break
                if (line.startsWith("data:")) {
                    sawSse = true
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = ChatResponseParser.parseSseDelta(data)
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onDelta(delta)
                    }
                } else if (!sawSse && line.isNotBlank()) {
                    raw.append(line)
                }
            }
        }

        // 服务端忽略了 stream 参数、直接返回整段 JSON 时，退化为一次性解析
        if (!sawSse) {
            val content = ChatResponseParser.parse(raw.toString(), allowEmptyContent = false)
            onDelta(content)
            return content
        }
        indicator?.checkCanceled()
        if (accumulated.isBlank()) throw LlmException("模型没有返回内容", LlmError.EMPTY_RESPONSE)
        return accumulated.toString()
    }

    private fun execute(
        messages: List<ChatMessage>,
        indicator: ProgressIndicator?,
        allowEmptyContent: Boolean,
        minimalPayload: Boolean = false,
    ): String {
        if (baseUrl.isBlank()) throw LlmException("未配置 Base URL", LlmError.CONFIG)
        if (model.isBlank()) throw LlmException("未配置模型名称", LlmError.CONFIG)

        val payload = buildPayload(messages, minimalPayload)

        val response = try {
            val builder = HttpRequests.post(endpoint(), "")
                .accept("application/json")
                .connectTimeout(connectTimeoutMs)
                .readTimeout(readTimeoutMs)
                .throwStatusCodeException(false)
                .tuner { connection ->
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    apiKey?.takeIf { it.isNotBlank() }?.let {
                        connection.setRequestProperty("Authorization", "Bearer $it")
                    }
                }
            builder.connect { request ->
                // 平台 262 的 HttpRequests.post(url, body) 不会把 body 写出去（实测只发请求头），
                // 必须显式 write，否则服务端收到空 body 会返回「参数无效」。
                request.write(payload)
                readResponse(request, indicator)
            }
        } catch (e: HttpRequests.HttpStatusException) {
            throw ChatResponseParser.mapHttpError(e.statusCode, e.message, e)
        } catch (e: SocketTimeoutException) {
            throw LlmException(
                "请求超时（连接 ${connectTimeoutMs}ms / 读取 ${readTimeoutMs}ms），可调大超时或减小 diff 预算",
                LlmError.TIMEOUT,
                e,
            )
        } catch (e: IOException) {
            throw LlmException("网络请求失败：${e.message}", LlmError.NETWORK, e)
        }

        if (response.code !in 200..299) {
            throw ChatResponseParser.mapHttpError(response.code, response.body)
        }
        return parseResponse(response.body, allowEmptyContent)
    }

    private data class RawResponse(val code: Int, val body: String)

    private fun readResponse(request: HttpRequests.Request, indicator: ProgressIndicator?): RawResponse {
        indicator?.checkCanceled()
        val code = (request.connection as? HttpURLConnection)?.responseCode ?: -1
        val body: String = if (code in 200..299) {
            val text = if (indicator != null) request.readString(indicator) else request.readString()
            text ?: ""
        } else {
            runCatching { request.readError() }.getOrNull().orEmpty()
        }
        indicator?.checkCanceled()
        return RawResponse(code, body)
    }

    fun endpoint(): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.endsWith("/chat/completions")) return base
        return if (VERSION_SUFFIX.containsMatchIn(base)) {
            "$base/chat/completions"
        } else {
            "$base/v1/chat/completions"
        }
    }

    internal fun buildPayload(
        messages: List<ChatMessage>,
        minimal: Boolean = false,
        stream: Boolean = false,
    ): String {
        val array = JsonArray()
        messages.forEach { message ->
            array.add(
                JsonObject().apply {
                    addProperty("role", message.role)
                    addProperty("content", message.content)
                },
            )
        }
        return JsonObject().apply {
            addProperty("model", model)
            add("messages", array)
            if (!minimal) {
                // 部分服务商对取值区间校验很严，这里夹到安全范围，避免 400
                addProperty("temperature", temperature.coerceIn(MIN_TEMPERATURE, 2.0))
                addProperty("max_tokens", maxTokens.coerceAtLeast(1))
                addProperty("stream", stream)
            }
        }.toString()
    }

    private fun parseResponse(raw: String, allowEmptyContent: Boolean): String =
        ChatResponseParser.parse(raw, allowEmptyContent)

    companion object {
        private const val MIN_TEMPERATURE = 0.01
        private val VERSION_SUFFIX = Regex("/v\\d+$")
    }
}
