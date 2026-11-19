package com.lememo.aicommit.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiCompatibleClientTest {

    private fun endpointOf(baseUrl: String): String =
        OpenAiCompatibleClient(baseUrl = baseUrl, model = "m", apiKey = null).endpoint()

    @Test
    fun `裸域名补 v1`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            endpointOf("https://api.deepseek.com"),
        )
    }

    @Test
    fun `已有版本段只补路径`() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            endpointOf("https://open.bigmodel.cn/api/paas/v4"),
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            endpointOf("https://dashscope.aliyuncs.com/compatible-mode/v1/"),
        )
    }

    @Test
    fun `已写全路径不重复拼接`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            endpointOf("https://example.com/v1/chat/completions"),
        )
    }

    @Test
    fun `本地 Ollama`() {
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            endpointOf("http://localhost:11434/v1"),
        )
    }
}
