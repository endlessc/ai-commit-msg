package com.lememo.aicommit.generate

/**
 * 模型输出常有代码块包裹、前缀说明、多余空行，统一在这里洗干净。
 */
object MessagePostProcessor {

    private val FENCE = Regex("^```[\\w+#-]*\\R([\\s\\S]*?)\\R```\\s*$")

    private val PREFIXES = listOf(
        "提交信息：", "提交信息:", "commit message:", "commit message：",
        "提交说明：", "提交说明:", "生成结果：", "以下是提交信息：",
    )

    fun clean(raw: String): String {
        var text = raw.replace("\r\n", "\n").trim()

        FENCE.find(text)?.let { text = it.groupValues[1].trim() }

        val lines = text.lines().toMutableList()
        while (lines.isNotEmpty()) {
            val first = lines.first().trim()
            if (first.isEmpty()) {
                lines.removeAt(0)
                continue
            }
            val matched = PREFIXES.firstOrNull { first.lowercase().startsWith(it.lowercase()) }
            if (matched == null) break

            val rest = first.substring(matched.length).trim()
            if (rest.isEmpty()) {
                lines.removeAt(0)
            } else {
                lines[0] = rest
                break
            }
        }

        return lines.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
