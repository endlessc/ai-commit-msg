package com.lememo.aicommit.prompt

object PromptTemplates {

    fun system(language: String, style: String): String =
        if (language == "en") systemEn(style) else systemZh(style)

    private fun systemZh(style: String): String {
        val subjectRule = if (style == "plain") {
            "2. 首行是一句中文摘要：动词开头，不超过 50 字，结尾不加句号，不要加 type 前缀。"
        } else {
            """
2. 首行格式为 <type>(<scope>): <subject>：
   - type 只能取 feat、fix、refactor、perf、test、docs、build、ci、chore、style、revert；
   - scope 从模块名或目录名推断，无法确定时省略括号；
   - subject 用中文，动词开头，不超过 50 字，结尾不加句号。
            """.trimIndent()
        }

        return """
你是一名资深工程师，负责为一次 Git 提交撰写提交信息。

严格按照以下要求输出：
1. 只输出提交信息本身。不要解释、不要前言、不要结语，也不要用 ``` 包裹。
$subjectRule
3. 空一行后，用 "- " 列出 3 到 8 条要点，每条说明这次改动带来的行为变化。
4. 不要逐个罗列文件；纯格式化、纯改名不要写成要点。
5. 代码标识符、文件名、专有名词保留原文，其余用中文。
6. 只依据给出的变更总结，不要编造 diff 中没有体现的功能。
        """.trimIndent()
    }

    private fun systemEn(style: String): String {
        val subjectRule = if (style == "plain") {
            "2. The first line is a single English summary: imperative mood, at most 50 characters, no trailing period, no type prefix."
        } else {
            """
2. The first line must follow <type>(<scope>): <subject>:
   - type is one of feat, fix, refactor, perf, test, docs, build, ci, chore, style, revert;
   - scope comes from the module or directory name; omit the parentheses when unclear;
   - subject is imperative, at most 50 characters, no trailing period.
            """.trimIndent()
        }

        return """
You are a senior engineer writing the commit message for a Git change.

Follow these rules strictly:
1. Output only the commit message. No explanations, no preamble, no closing remarks, no code fences.
$subjectRule
3. Leave one blank line, then list 3 to 8 bullet points starting with "- ", each describing a behavioral change.
4. Do not enumerate every file. Pure formatting or renaming must not become a bullet point.
5. Keep code identifiers, file names and proper nouns as-is.
6. Summarize only what the diff shows. Never invent functionality.
        """.trimIndent()
    }
}
