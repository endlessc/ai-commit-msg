package com.lememo.aicommit.collect

import com.lememo.aicommit.model.CollectedDiff
import com.lememo.aicommit.model.DiffBundle
import com.lememo.aicommit.model.FileDiff

/**
 * 按预算裁剪 diff：先保证每个文件都露一次面，再按重要性分配剩余空间。
 */
class DiffTrimmer(
    private val maxChars: Int = 40_000,
    private val maxFileLines: Int = 400,
    private val maxFiles: Int = 100,
) {

    fun trim(collected: CollectedDiff): DiffBundle {
        // 源码优先；同级按改动量从小到大，避免一个巨型改动吃掉全部预算
        val sorted = collected.files.sortedWith(
            compareBy({ priority(it.path) }, { it.changes }),
        )

        val sb = StringBuilder()
        val included = ArrayList<String>()
        val omitted = ArrayList<String>()

        for ((index, file) in sorted.withIndex()) {
            if (index >= maxFiles) {
                omitted.add(display(file))
                continue
            }

            val remaining = maxChars - sb.length
            if (remaining <= 0) {
                omitted.add(display(file))
                continue
            }

            val block = render(file)
            if (block.length > remaining) {
                if (sb.isNotEmpty()) {
                    omitted.add(display(file))
                    continue
                }
                // 单个文件就超预算：硬截断，至少保留一段可读内容
                sb.append(block.take(remaining)).append("\n... (已按预算截断)\n")
                included.add(display(file))
                continue
            }

            sb.append(block).append('\n')
            included.add(display(file))
        }

        val overview = buildString {
            if (collected.overview.isNotBlank()) appendLine(collected.overview.trim())
            if (omitted.isNotEmpty()) {
                val names = omitted.take(20).joinToString("，") + if (omitted.size > 20) " …" else ""
                appendLine()
                appendLine("（另有 ${omitted.size} 个文件的差异未展开：$names）")
            }
        }.trim()

        return DiffBundle(
            overview = overview,
            diffText = sb.toString().trim(),
            totalFiles = collected.totalFiles,
            includedFiles = included.size,
            omittedFiles = omitted,
        )
    }

    private fun render(file: FileDiff): String {
        val header = "### ${display(file)} [${file.status.label}] +${file.added}/-${file.deleted}"
        return buildString {
            appendLine(header)
            append(cap(file))
        }
    }

    private fun cap(file: FileDiff): String {
        val lines = file.text.lines()
        if (lines.size <= maxFileLines) return file.text
        return lines.take(maxFileLines).joinToString("\n") +
            "\n... (此文件剩余 ${lines.size - maxFileLines} 行差异已省略)"
    }

    private fun display(file: FileDiff): String =
        if (file.repo.isBlank()) file.path else "${file.repo}:${file.path}"

    private fun priority(path: String): Int {
        val lower = path.lowercase()
        val name = lower.substringAfterLast('/')
        if (name.contains("test") || lower.contains("/test/") || lower.contains("/tests/")) return 3

        val ext = name.substringAfterLast('.', "")
        return when (ext) {
            "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "go", "rs",
            "c", "h", "cpp", "hpp", "cc", "cs", "rb", "php", "swift", "m", "mm",
            "scala", "sh", "sql", "vue", "svelte", "dart", "lua", "pl", "r",
            -> 0

            "json", "yaml", "yml", "toml", "ini", "xml", "properties", "env", "conf", "cfg",
            -> 1

            else -> 2
        }
    }
}
