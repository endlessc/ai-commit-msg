package com.lememo.aicommit.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsDataKeys
import com.lememo.aicommit.AiCommitIcons

/**
 * 提交面板工具条上的按钮（动作组 Vcs.MessageActionGroup）。
 * 生成中再次点击即取消。
 */
class GenerateCommitMessageAction : AnAction(
    "AI 生成提交信息",
    "根据提交面板中勾选的变更生成提交信息",
    AiCommitIcons.Generate,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.isVisible = false
            return
        }

        val service = project.service<CommitGenerationService>()
        if (service.isRunning()) {
            e.presentation.text = "停止生成"
            e.presentation.description = "取消当前的生成请求"
            e.presentation.isEnabled = true
            return
        }

        e.presentation.text = "AI 生成提交信息"
        e.presentation.description = "根据提交面板中勾选的变更生成提交信息"
        e.presentation.isEnabled = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) != null ||
            e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null ||
            e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<CommitGenerationService>().start(e)
    }
}
