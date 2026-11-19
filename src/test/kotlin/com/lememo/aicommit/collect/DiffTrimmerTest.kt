package com.lememo.aicommit.collect

import com.lememo.aicommit.model.ChangeItem
import com.lememo.aicommit.model.CollectedDiff
import com.lememo.aicommit.model.FileDiff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffTrimmerTest {

    private fun file(path: String, lines: Int = 3): FileDiff {
        val text = "diff --git a/$path b/$path\n" + "+changed line\n".repeat(lines)
        return FileDiff("demo", path, ChangeItem.Status.MODIFIED, text, lines, 0)
    }

    @Test
    fun `排序时源码优先于文档`() {
        val docs = file("docs/guide.md")
        val source = file("src/Main.kt")
        val bundle = DiffTrimmer(maxChars = 10_000)
            .trim(CollectedDiff(listOf(docs, source), "", 2))

        assertTrue(bundle.diffText.indexOf("src/Main.kt") < bundle.diffText.indexOf("docs/guide.md"))
    }

    @Test
    fun `超出预算的文件进入省略清单`() {
        val files = (1..10).map { file("src/File$it.kt", lines = 100) }
        val bundle = DiffTrimmer(maxChars = 500, maxFileLines = 400)
            .trim(CollectedDiff(files, "概览", files.size))

        assertTrue(bundle.diffText.length <= 500 + 100, "实际长度 ${bundle.diffText.length}")
        assertTrue(bundle.includedFiles >= 1)
        assertTrue(bundle.omittedFiles.isNotEmpty())
        assertTrue(bundle.overview.contains("未展开"))
    }

    @Test
    fun `单文件按行数截断`() {
        val big = file("src/Big.kt", lines = 500)
        val bundle = DiffTrimmer(maxChars = 100_000, maxFileLines = 100)
            .trim(CollectedDiff(listOf(big), "", 1))

        assertTrue(bundle.diffText.contains("已省略"))
        assertTrue(bundle.diffText.lines().size < 120)
    }

    @Test
    fun `多仓库文件带仓库前缀`() {
        val f = FileDiff("repo-a", "src/A.kt", ChangeItem.Status.ADDED, "+a", 1, 0)
        val bundle = DiffTrimmer().trim(CollectedDiff(listOf(f), "", 1))
        assertTrue(bundle.diffText.contains("repo-a:src/A.kt"))
        assertEquals(1, bundle.includedFiles)
    }
}
