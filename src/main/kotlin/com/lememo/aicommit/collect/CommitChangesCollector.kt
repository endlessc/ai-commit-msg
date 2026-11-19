package com.lememo.aicommit.collect

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.lememo.aicommit.model.ChangeItem
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 读取「这次提交勾选的文件」。
 *
 * [VcsDataKeys.COMMIT_WORKFLOW_UI] 由新的非模态提交面板提供，`getIncludedChanges()` 返回
 * 变更树里被勾选的那些项（而不是全部本地改动），这正是生成提交信息需要的范围。
 */
object CommitChangesCollector {

    fun collect(e: AnActionEvent): List<ChangeItem> {
        val ui = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) ?: return emptyList()

        val items = ArrayList<ChangeItem>()
        ui.getIncludedChanges().forEach { change -> change.toItem()?.let(items::add) }
        ui.getIncludedUnversionedFiles().forEach { file -> file.toNewItem()?.let(items::add) }
        return items.distinctBy { it.absolutePath?.toString() ?: it.relativePath }
    }

    private fun Change.toItem(): ChangeItem? {
        val path = pathOf(this) ?: return null
        val repoRoot = GitRepoResolver.rootFor(path) ?: return null
        return ChangeItem(
            repoRoot = repoRoot,
            relativePath = GitRepoResolver.relativePath(repoRoot, path),
            status = statusOf(type),
            absolutePath = if (type == Change.Type.DELETED) null else path,
        )
    }

    private fun FilePath.toNewItem(): ChangeItem? {
        val path = Paths.get(this.path)
        val repoRoot = GitRepoResolver.rootFor(path) ?: return null
        return ChangeItem(
            repoRoot = repoRoot,
            relativePath = GitRepoResolver.relativePath(repoRoot, path),
            status = ChangeItem.Status.ADDED,
            absolutePath = path,
        )
    }

    private fun pathOf(change: Change): Path? {
        val filePath: FilePath? = change.afterRevision?.file ?: change.beforeRevision?.file
        return filePath?.let { Paths.get(it.path) }
    }

    private fun statusOf(type: Change.Type): ChangeItem.Status = when (type) {
        Change.Type.NEW -> ChangeItem.Status.ADDED
        Change.Type.DELETED -> ChangeItem.Status.DELETED
        Change.Type.MOVED -> ChangeItem.Status.MOVED
        Change.Type.MODIFICATION -> ChangeItem.Status.MODIFIED
    }
}
