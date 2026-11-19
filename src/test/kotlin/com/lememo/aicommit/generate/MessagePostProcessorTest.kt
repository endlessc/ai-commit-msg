package com.lememo.aicommit.generate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessagePostProcessorTest {

    @Test
    fun `剥离代码块与前缀`() {
        val raw = "```\n提交信息：feat(core): 新增能力\n\n- 改动一\n- 改动二\n```"
        assertEquals(
            "feat(core): 新增能力\n\n- 改动一\n- 改动二",
            MessagePostProcessor.clean(raw),
        )
    }

    @Test
    fun `折叠多余空行`() {
        assertEquals("feat: a\n\n- b", MessagePostProcessor.clean("feat: a\n\n\n\n- b"))
    }

    @Test
    fun `没有前缀时保持原样`() {
        val text = "fix(ui): 修复按钮错位\n\n- 调整间距"
        assertEquals(text, MessagePostProcessor.clean(text))
    }

    @Test
    fun `前缀单独成行时整行删除`() {
        val raw = "提交信息：\nfeat: 新功能\n\n- 细节"
        assertEquals("feat: 新功能\n\n- 细节", MessagePostProcessor.clean(raw))
    }

    @Test
    fun `去掉首尾空白与回车符`() {
        assertEquals("feat: a", MessagePostProcessor.clean("\r\n  feat: a  \r\n"))
    }
}
