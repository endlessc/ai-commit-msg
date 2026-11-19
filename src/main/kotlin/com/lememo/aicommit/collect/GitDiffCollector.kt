package com.lememo.aicommit.collect

import com.intellij.openapi.progress.ProgressIndicator
import com.lememo.aicommit.model.ChangeItem
import com.lememo.aicommit.model.CollectedDiff
import com.lememo.aicommit.model.FileDiff
import java.nio.file.Files
import java.nio.file.Path

/**
 * 用 `git diff HEAD` 采集「提交后等价」的差异。
 *
 * 选择命令行而非平台 `ContentRevision`：重命名、换行符、二进制识别、编码都由 Git 处理，
 * 边界情况远少于自己重算 diff。
 */
class GitDiffCollector(private val filter: DiffFilter) {

    fun collect(items: List<ChangeItem>, indicator: ProgressIndicator? = null): CollectedDiff {
        if (items.isEmpty()) return CollectedDiff(emptyList(), "", 0)

        val files = ArrayList<FileDiff>()
        val overviewParts = ArrayList<String>()

        for ((repoRoot, repoItems) in items.groupBy { it.repoRoot }) {
            indicator?.checkCanceled()
            val repoName = repoRoot.fileName?.toString().orEmpty()
            val paths = repoItems.map { it.relativePath }.distinct().toTypedArray()

            val fileList = repoItems
                .sortedBy { it.relativePath }
                .joinToString("\n") { "${it.status.label}  ${it.relativePath}" }
            overviewParts.add(fileList)

            val stat = GitCli.run(repoRoot, "diff", "--stat", "HEAD", "--", *paths, timeoutMs = 10_000)
            if (stat.isSuccess && stat.stdout.isNotBlank()) {
                overviewParts.add(stat.stdout.trim().lines().take(MAX_STAT_LINES).joinToString("\n"))
            }

            val diff = GitCli.run(
                repoRoot, "diff", "HEAD", "--no-color", "--no-ext-diff", "--unified=3", "--", *paths,
                timeoutMs = 20_000,
            )
            val parsed = if (diff.isSuccess) parseDiff(repoName, diff.stdout) else emptyList()
            files.addAll(parsed)

            // 未跟踪文件不在 `git diff HEAD` 里，按整文件新增补出来
            val parsedPaths = parsed.map { it.path }.toSet()
            for (item in repoItems) {
                if (item.status != ChangeItem.Status.ADDED) continue
                if (item.relativePath in parsedPaths) continue
                val absolute = item.absolutePath ?: continue
                indicator?.checkCanceled()
                syntheticAdded(repoName, item.relativePath, absolute)?.let(files::add)
            }
        }

        val included = files.filter { filter.isIncluded(it.path) }
        return CollectedDiff(
            files = included,
            overview = overviewParts.joinToString("\n\n"),
            totalFiles = items.size,
        )
    }

    private fun parseDiff(repoName: String, raw: String): List<FileDiff> {
        if (raw.isBlank()) return emptyList()

        val chunks = ArrayList<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in raw.lineSequence()) {
            if (line.startsWith("diff --git ")) {
                current = mutableListOf()
                chunks.add(current)
            }
            current?.add(line)
        }

        return chunks.mapNotNull { lines ->
            val path = extractPath(lines) ?: return@mapNotNull null
            var added = 0
            var deleted = 0
            for (line in lines) {
                if (line.startsWith("+++ ") || line.startsWith("--- ")) continue
                if (line.startsWith("+")) added++ else if (line.startsWith("-")) deleted++
            }
            FileDiff(
                repo = repoName,
                path = path,
                status = statusOf(lines),
                text = lines.joinToString("\n"),
                added = added,
                deleted = deleted,
            )
        }
    }

    private fun extractPath(lines: List<String>): String? {
        for (line in lines) {
            if (line.startsWith("+++ b/")) return cleanPath(line.removePrefix("+++ b/"))
        }
        // 删除文件时 +++ 是 /dev/null
        for (line in lines) {
            if (line.startsWith("--- a/")) return cleanPath(line.removePrefix("--- a/"))
        }
        return null
    }

    private fun cleanPath(raw: String): String =
        raw.substringBeforeLast('\t').trim().removeSurrounding("\"")

    private fun statusOf(lines: List<String>): ChangeItem.Status = when {
        lines.any { it.startsWith("new file mode") } -> ChangeItem.Status.ADDED
        lines.any { it.startsWith("deleted file mode") } -> ChangeItem.Status.DELETED
        lines.any { it.startsWith("rename from") } -> ChangeItem.Status.MOVED
        else -> ChangeItem.Status.MODIFIED
    }

    private fun syntheticAdded(repoName: String, relativePath: String, file: Path): FileDiff? {
        if (!Files.isRegularFile(file)) return null
        val size = runCatching { Files.size(file) }.getOrNull() ?: return null
        if (size > MAX_UNTRACKED_BYTES) return null

        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return null
        if (bytes.any { it == 0.toByte() }) return null

        val lines = String(bytes, Charsets.UTF_8).lines()
            .take(MAX_UNTRACKED_LINES)
            .ifEmpty { return null }

        val text = buildString {
            appendLine("diff --git a/$relativePath b/$relativePath")
            appendLine("new file mode 100644")
            appendLine("--- /dev/null")
            appendLine("+++ b/$relativePath")
            appendLine("@@ -0,0 +1,${lines.size} @@")
            append(lines.joinToString("\n") { "+$it" })
        }

        return FileDiff(
            repo = repoName,
            path = relativePath,
            status = ChangeItem.Status.ADDED,
            text = text,
            added = lines.size,
            deleted = 0,
        )
    }

    companion object {
        private const val MAX_STAT_LINES = 200
        private const val MAX_UNTRACKED_BYTES = 512 * 1024L
        private const val MAX_UNTRACKED_LINES = 400
    }
}
