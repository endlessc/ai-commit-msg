package com.lememo.aicommit.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChatResponseParserTest {

    private fun response(content: String?, finish: String, reasoning: String? = null): String {
        val message = buildString {
            append("\"role\":\"assistant\",")
            append(if (content == null) "\"content\":null" else "\"content\":\"$content\"")
            if (reasoning != null) append(",\"reasoning_content\":\"$reasoning\"")
        }
        return """{"choices":[{"index":0,"message":{$message},"finish_reason":"$finish"}]}"""
    }

    @Test
    fun `正常返回正文`() {
        val raw = response("feat: 新增功能", "stop")
        assertEquals("feat: 新增功能", ChatResponseParser.parse(raw))
    }

    @Test
    fun `推理模型耗尽预算时报可操作的错误`() {
        val raw = response(null, "length", reasoning = "让我先分析一下这个 diff……")
        val error = assertThrows<LlmException> { ChatResponseParser.parse(raw) }

        assertEquals(LlmError.TRUNCATED, error.kind)
        assertTrue(error.message!!.contains("reasoning_content"))
        assertTrue(error.message!!.contains("2048"))
    }

    @Test
    fun `仅验证连接时空正文视为成功`() {
        val raw = response(null, "length", reasoning = "思考中")
        assertEquals("", ChatResponseParser.parse(raw, allowEmptyContent = true))
    }

    @Test
    fun `连接测试遇到真实错误仍然抛异常`() {
        val raw = """{"code":30014,"data":null,"message":"Token is invalid."}"""
        val error = assertThrows<LlmException> { ChatResponseParser.parse(raw, allowEmptyContent = true) }
        assertEquals(LlmError.BAD_RESPONSE, error.kind)
        assertTrue(error.message!!.contains("choices"))
    }

    @Test
    fun `error 字段优先于 choices`() {
        val raw = """{"error":{"message":"Model does not exist"}}"""
        val error = assertThrows<LlmException> { ChatResponseParser.parse(raw) }
        assertTrue(error.message!!.contains("Model does not exist"))
    }

    @Test
    fun `非 JSON 响应给出截断预览`() {
        val error = assertThrows<LlmException> { ChatResponseParser.parse("<html>502 Bad Gateway</html>") }
        assertEquals(LlmError.BAD_RESPONSE, error.kind)
    }

    @Test
    fun `401 的错误信息带上服务端原文`() {
        val error = ChatResponseParser.mapHttpError(401, """{"code":30014,"message":"Token is invalid."}""")
        assertEquals(LlmError.AUTH, error.kind)
        assertTrue(error.message!!.contains("Token is invalid."))
    }

    @Test
    fun `404 提示地址或模型名有问题`() {
        val error = ChatResponseParser.mapHttpError(404, null)
        assertEquals(LlmError.NOT_FOUND, error.kind)
        assertTrue(error.message!!.contains("Base URL") || error.message!!.contains("模型名"))
    }

    @Test
    fun `不把 HttpURLConnection 的模板消息当服务端原文`() {
        val error = ChatResponseParser.mapHttpError(500, "Server returned HTTP response code: 500 for URL: x")
        assertTrue(!error.message!!.contains("Server returned HTTP"))
    }

    @Test
    fun `429 归为限流`() {
        assertEquals(LlmError.RATE_LIMIT, ChatResponseParser.mapHttpError(429, "rate limited").kind)
    }
}
