package com.lememo.aicommit.llm

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress

/**
 * 走真实 HTTP 回环，防止「请求体没发出去」这类静默失败再次出现。
 */
class OpenAiCompatibleClientRoundTripTest {

    private var server: HttpServer? = null

    private fun startServer(reply: (String) -> Pair<Int, String>): Pair<String, MutableList<String>> {
        val captured = mutableListOf<String>()
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/v1/chat/completions") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            captured.add(body)
            captured.add(exchange.requestHeaders.toString())
            val (code, text) = reply(body)
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        srv.start()
        server = srv
        return "http://127.0.0.1:${srv.address.port}/v1" to captured
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun client(baseUrl: String, maxTokens: Int = 128) = OpenAiCompatibleClient(
        baseUrl = baseUrl,
        model = "deepseek-ai/DeepSeek-V3.2",
        apiKey = "sk-test",
        temperature = 0.2,
        maxTokens = maxTokens,
    )

    private val okReply = """{"choices":[{"message":{"content":"feat: ok"},"finish_reason":"stop"}]}"""

    @Test
    fun `请求体与鉴权头真的发出去了`() {
        val (baseUrl, captured) = startServer { 200 to okReply }

        val text = client(baseUrl).complete(listOf(ChatMessage.user("ping")))

        assertEquals("feat: ok", text)
        val body = captured[0]
        assertTrue(body.contains("\"model\":\"deepseek-ai/DeepSeek-V3.2\""), "实际 body=<$body>")
        assertTrue(body.contains("\"content\":\"ping\""), "实际 body=<$body>")
        assertTrue(body.contains("\"max_tokens\":128"), "实际 body=<$body>")
        assertTrue(captured[1].contains("sk-test"), "缺少鉴权头")
    }

    @Test
    fun `测试连接只发最小请求体`() {
        val (baseUrl, captured) = startServer { 200 to okReply }

        client(baseUrl).testConnection(listOf(ChatMessage.user("ping")), null)

        val body = captured[0]
        assertTrue(body.contains("\"model\""), "实际 body=<$body>")
        assertTrue(body.contains("\"messages\""), "实际 body=<$body>")
        assertTrue(!body.contains("temperature"), "实际 body=<$body>")
        assertTrue(!body.contains("max_tokens"), "实际 body=<$body>")
    }

    @Test
    fun `4xx 时抛出带服务端原文的错误`() {
        val (baseUrl, _) = startServer {
            400 to """{"code":20015,"message":"The parameter is invalid. Please check again.","data":null}"""
        }

        val error = assertThrows<LlmException> {
            client(baseUrl).complete(listOf(ChatMessage.user("ping")))
        }
        assertTrue(error.message!!.contains("20015"), "实际=${error.message}")
        assertTrue(error.message!!.contains("400"))
    }

    @Test
    fun `空正文时连接测试通过但生成失败`() {
        val emptyReply = """{"choices":[{"message":{"content":null},"finish_reason":"length"}]}"""
        val (baseUrl, _) = startServer { 200 to emptyReply }

        assertEquals("", client(baseUrl).testConnection(listOf(ChatMessage.user("ping")), null))

        val error = assertThrows<LlmException> {
            client(baseUrl).complete(listOf(ChatMessage.user("ping")))
        }
        assertEquals(LlmError.TRUNCATED, error.kind)
    }

    @Test
    fun `流式响应按增量回调并拼接完整文本`() {
        val deltas = mutableListOf<String>()
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/v1/chat/completions") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody
            listOf("feat: ", "流式", "输出").forEach { piece ->
                out.write("data: {\"choices\":[{\"delta\":{\"content\":\"$piece\"}}]}\n\n".toByteArray())
                out.flush()
            }
            out.write("data: [DONE]\n\n".toByteArray())
            out.close()
        }
        srv.start()
        server = srv

        val text = client("http://127.0.0.1:${srv.address.port}/v1")
            .stream(listOf(ChatMessage.user("ping")), null) { deltas.add(it) }

        assertEquals("feat: 流式输出", text)
        assertEquals(listOf("feat: ", "流式", "输出"), deltas)
    }

    @Test
    fun `服务端忽略 stream 时退化为整段解析`() {
        val (baseUrl, _) = startServer { 200 to okReply }
        val deltas = mutableListOf<String>()

        val text = client(baseUrl).stream(listOf(ChatMessage.user("ping")), null) { deltas.add(it) }

        assertEquals("feat: ok", text)
        assertEquals(listOf("feat: ok"), deltas)
    }
}
