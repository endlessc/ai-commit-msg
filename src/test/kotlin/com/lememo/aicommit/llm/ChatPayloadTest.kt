package com.lememo.aicommit.llm

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatPayloadTest {

    private fun payload(
        temperature: Double,
        maxTokens: Int = 2_048,
        messages: List<ChatMessage> = listOf(ChatMessage.user("ping")),
        minimal: Boolean = false,
    ): String = OpenAiCompatibleClient(
        baseUrl = "https://api.siliconflow.cn/v1",
        model = "deepseek-ai/DeepSeek-V3.2",
        apiKey = "sk-test",
        temperature = temperature,
        maxTokens = maxTokens,
    ).buildPayload(messages, minimal)

    @Test
    fun `temperature 为 0 时夹到合法区间`() {
        val json = JsonParser.parseString(payload(0.0)).asJsonObject
        val temperature = json.get("temperature").asDouble
        assertTrue(temperature > 0.0, "实际 $temperature")
        assertTrue(temperature <= 2.0)
    }

    @Test
    fun `超出上限的 temperature 被压回 2`() {
        val json = JsonParser.parseString(payload(3.5)).asJsonObject
        assertEquals(2.0, json.get("temperature").asDouble)
    }

    @Test
    fun `正常 temperature 原样透传`() {
        val json = JsonParser.parseString(payload(0.2)).asJsonObject
        assertEquals(0.2, json.get("temperature").asDouble)
    }

    @Test
    fun `max_tokens 至少为 1 且 stream 关闭`() {
        val json = JsonParser.parseString(payload(0.2, maxTokens = 0)).asJsonObject
        assertEquals(1, json.get("max_tokens").asInt)
        assertEquals(false, json.get("stream").asBoolean)
        assertEquals("deepseek-ai/DeepSeek-V3.2", json.get("model").asString)
    }

    @Test
    fun `最小请求体只含 model 与 messages`() {
        val json = JsonParser.parseString(payload(0.0, minimal = true)).asJsonObject
        assertEquals("deepseek-ai/DeepSeek-V3.2", json.get("model").asString)
        assertTrue(json.has("messages"))
        assertTrue(!json.has("temperature"))
        assertTrue(!json.has("max_tokens"))
        assertTrue(!json.has("stream"))
    }

    @Test
    fun `流式请求里 stream 为 true`() {
        val json = JsonParser.parseString(
            OpenAiCompatibleClient("https://example.com/v1", "m", null)
                .buildPayload(listOf(ChatMessage.user("hi")), stream = true),
        ).asJsonObject
        assertEquals(true, json.get("stream").asBoolean)
    }

    @Test
    fun `system 与 user 消息顺序保留`() {
        val json = JsonParser.parseString(
            payload(0.2, messages = listOf(ChatMessage.system("s"), ChatMessage.user("u"))),
        ).asJsonObject
        val messages = json.getAsJsonArray("messages")
        assertEquals("system", messages[0].asJsonObject.get("role").asString)
        assertEquals("user", messages[1].asJsonObject.get("role").asString)
    }
}
