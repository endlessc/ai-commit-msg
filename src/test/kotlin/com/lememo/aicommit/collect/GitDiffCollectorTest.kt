package com.lememo.aicommit.collect

import com.lememo.aicommit.model.ChangeItem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GitDiffCollectorTest {

    private fun git(repo: Path, vararg args: String): Boolean {
        val command = listOf("git", "-C", repo.toString()) + args
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0
    }

    private fun tempRepo(): Path? {
        val repo = Files.createTempDirectory("acm-git-test")
        val ok = git(repo, "init", "-q", "-b", "main") &&
            git(repo, "config", "user.email", "test@example.com") &&
            git(repo, "config", "user.name", "tester")
        return if (ok) repo else null
    }

    @Test
    fun `解析已修改文件并合成未跟踪文件`() {
        val repo = tempRepo()
        assumeTrue(repo != null, "git 不可用，跳过")

        val tracked = repo!!.resolve("a.kt")
        Files.writeString(tracked, "fun a() = 1\n")
        assertTrue(git(repo, "add", "."))
        assertTrue(git(repo, "commit", "-qm", "init"))

        Files.writeString(tracked, "fun a() = 2\n")
        val untracked = repo.resolve("b.kt")
        Files.writeString(untracked, "fun b() = 3\n")

        val items = listOf(
            ChangeItem(repo, "a.kt", ChangeItem.Status.MODIFIED, tracked),
            ChangeItem(repo, "b.kt", ChangeItem.Status.ADDED, untracked),
        )

        val collected = GitDiffCollector(DiffFilter(ignoreIdeaFiles = false)).collect(items)

        val modified = collected.files.firstOrNull { it.path == "a.kt" }
        assertTrue(modified != null, "应解析出 a.kt 的 diff")
        assertTrue(modified!!.text.contains("fun a() = 2"), "diff 应包含新内容")

        val added = collected.files.firstOrNull { it.path == "b.kt" }
        assertTrue(added != null, "未跟踪文件应被合成为新增 diff")
        assertTrue(added!!.text.contains("new file mode"))
        assertTrue(added.text.contains("fun b() = 3"))
        assertTrue(added.added >= 1)
    }

    @Test
    fun `过滤掉锁文件与生成物`() {
        val repo = tempRepo()
        assumeTrue(repo != null, "git 不可用，跳过")

        Files.writeString(repo!!.resolve("package-lock.json"), "{}\n")
        Files.writeString(repo.resolve("main.kt"), "fun main() {}\n")
        assertTrue(git(repo, "add", "."))
        assertTrue(git(repo, "commit", "-qm", "init"))

        Files.writeString(repo.resolve("package-lock.json"), "{\"a\":1}\n")
        Files.writeString(repo.resolve("main.kt"), "fun main() { println(1) }\n")

        val items = listOf(
            ChangeItem(repo, "package-lock.json", ChangeItem.Status.MODIFIED, repo.resolve("package-lock.json")),
            ChangeItem(repo, "main.kt", ChangeItem.Status.MODIFIED, repo.resolve("main.kt")),
        )

        val collected = GitDiffCollector(DiffFilter(ignoreIdeaFiles = false)).collect(items)

        assertTrue(collected.files.none { it.path == "package-lock.json" }, "锁文件不应进入 diff")
        assertTrue(collected.files.any { it.path == "main.kt" })
        assertTrue(collected.overview.contains("package-lock.json"), "锁文件仍应出现在文件清单里")
    }
}
