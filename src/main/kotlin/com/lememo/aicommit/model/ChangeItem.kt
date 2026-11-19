package com.lememo.aicommit.model

import java.nio.file.Path

/**
 * 一次提交中的单个文件。路径统一使用相对仓库根的 POSIX 形式，便于拼进 git pathspec 与 prompt。
 */
data class ChangeItem(
    val repoRoot: Path,
    val relativePath: String,
    val status: Status,
    val absolutePath: Path?,
) {
    enum class Status(val label: String) {
        ADDED("A"),
        MODIFIED("M"),
        DELETED("D"),
        MOVED("R"),
        UNKNOWN("?"),
    }

    val fileName: String
        get() = relativePath.substringAfterLast('/')
}
