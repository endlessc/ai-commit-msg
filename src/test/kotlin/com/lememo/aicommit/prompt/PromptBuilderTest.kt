package com.lememo.aicommit.prompt

import com.lememo.aicommit.model.DiffBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val bundle = DiffBundle(
        overview = "A  src/A.kt",
        diffText = "diff --git a/src/A.kt b/src/A.kt\n+fun a() = 1",
        totalFiles = 1,
        includedFiles = 1,
        omittedFiles = emptyList(),
    )

    @Test
    fun `system 与 user 角色分离`() {
        val messages = PromptBuilder("zh", "conventional").build(bundle, "demo", "main")

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertTrue(messages[0].content.contains("只输出提交信息"))
        assertTrue(messages[1].content.contains("分支：main"))
        assertTrue(messages[1].content.contains("src/A.kt"))
    }

    @Test
    fun `英文加纯文本模式`() {
        val messages = PromptBuilder("en", "plain").build(bundle, "demo", "main")
        assertTrue(messages[0].content.contains("imperative mood"))
        assertTrue(!messages[0].content.contains("<type>"))
    }

    @Test
    fun `最近提交只出现在 user 消息里`() {
        val messages = PromptBuilder("zh", "conventional", recentSubjects = listOf("feat: 旧功能"))
            .build(bundle, "demo", "main")

        assertTrue(messages[1].content.contains("feat: 旧功能"))
        assertTrue(!messages[0].content.contains("feat: 旧功能"))
    }

    @Test
    fun `自定义 system prompt 覆盖默认模板`() {
        val messages = PromptBuilder("zh", "conventional", customSystemPrompt = "只用一句话总结")
            .build(bundle, "demo", "main")

        assertEquals("只用一句话总结", messages[0].content)
    }

    @Test
    fun `空 diff 时有兜底文案`() {
        val empty = DiffBundle("", "", 0, 0, emptyList())
        val messages = PromptBuilder("zh", "conventional").build(empty, "", "")
        assertTrue(messages[1].content.contains("没有可用的 diff"))
        assertTrue(messages[1].content.contains("(未知)"))
    }
}
