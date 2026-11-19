package com.lememo.aicommit.collect

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 极简 git 命令行封装。只读命令，不涉及任何写操作。
 */
object GitCli {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    fun run(repoRoot: Path, vararg args: String, timeoutMs: Long = 20_000): Result {
        val command = buildList {
            add("git")
            add("-C")
            add(repoRoot.toString())
            // 避免非 ASCII 路径被转义成八进制
            add("-c")
            add("core.quotepath=false")
            addAll(args)
        }
        return try {
            val process = ProcessBuilder(command)
                .apply {
                    environment()["GIT_TERMINAL_PROMPT"] = "0"
                    environment()["GIT_PAGER"] = "cat"
                    environment()["LC_ALL"] = "C"
                }
                .start()

            // 并发读取，避免输出超过管道缓冲导致进程卡死
            val stdout = CompletableFuture.supplyAsync {
                process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            }
            val stderr = CompletableFuture.supplyAsync {
                process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            }

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return Result(-1, "", "git 命令超时（${timeoutMs}ms）：${args.joinToString(" ")}")
            }
            Result(
                process.exitValue(),
                stdout.get(2, TimeUnit.SECONDS),
                stderr.get(2, TimeUnit.SECONDS),
            )
        } catch (t: Throwable) {
            Result(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    fun branch(repoRoot: Path): String =
        run(repoRoot, "rev-parse", "--abbrev-ref", "HEAD", timeoutMs = 5_000)
            .stdout.trim()
            .ifBlank { "HEAD" }

    fun recentSubjects(repoRoot: Path, count: Int): List<String> {
        if (count <= 0) return emptyList()
        return run(repoRoot, "log", "-n", count.toString(), "--pretty=format:%s", timeoutMs = 5_000)
            .stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
