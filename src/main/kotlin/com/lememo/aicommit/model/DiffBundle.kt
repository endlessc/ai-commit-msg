package com.lememo.aicommit.model

/**
 * 单个文件的 unified diff 片段。`repo` 用于多仓库项目里区分来源。
 */
data class FileDiff(
    val repo: String,
    val path: String,
    val status: ChangeItem.Status,
    val text: String,
    val added: Int,
    val deleted: Int,
) {
    val changes: Int get() = added + deleted
}

/**
 * 采集结果（尚未按预算裁剪）。
 */
data class CollectedDiff(
    val files: List<FileDiff>,
    val overview: String,
    val totalFiles: Int,
)

/**
 * 送入 prompt 的最终结果。
 */
data class DiffBundle(
    val overview: String,
    val diffText: String,
    val totalFiles: Int,
    val includedFiles: Int,
    val omittedFiles: List<String>,
) {
    val isEmpty: Boolean get() = diffText.isBlank() && overview.isBlank()
}
