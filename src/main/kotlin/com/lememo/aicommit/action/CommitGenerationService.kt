package com.lememo.aicommit.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.commit.CommitMessageUi
import com.lememo.aicommit.collect.CommitChangesCollector
import com.lememo.aicommit.generate.CommitMessageGenerator
import com.lememo.aicommit.llm.LlmError
import com.lememo.aicommit.llm.LlmException
import com.lememo.aicommit.notify.notifyError
import com.lememo.aicommit.notify.notifyInfo
import com.lememo.aicommit.notify.notifyWarning
import com.lememo.aicommit.settings.AiCommitSettings

/**
 * 生成任务的生命周期：同一项目内串行，可取消，写回时保留编辑器的 undo 栈。
 */
@Service(Service.Level.PROJECT)
class CommitGenerationService(private val project: Project) {

    private companion object {
        /** 流式刷新间隔：太快会频繁重排编辑器，太慢会显得卡顿 */
        const val STREAM_FLUSH_INTERVAL_MS = 80L
    }

    private val lock = Any()
    private var indicator: ProgressIndicator? = null

    fun isRunning(): Boolean = synchronized(lock) { indicator != null }

    fun cancel() {
        synchronized(lock) { indicator?.cancel() }
    }

    fun start(e: AnActionEvent) {
        if (isRunning()) {
            cancel()
            return
        }

        // DataContext 只在 actionPerformed 期间有效，先同步取出所有依赖
        val settings = AiCommitSettings.getInstance()
        val state = settings.state
        val items = CommitChangesCollector.collect(e)
        val document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT)
        val control = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val messageUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.commitMessageUi

        if (items.isEmpty()) {
            notifyWarning(
                project,
                "没有可分析的变更",
                "请先在提交面板勾选要提交的文件。插件当前只处理 Git 仓库中的变更。",
            )
            return
        }
        if (state.writeMode == "onlyIfEmpty" && document != null && document.text.isNotBlank()) {
            notifyInfo(
                project,
                "跳过生成",
                "提交信息框已有内容，当前设置为「仅当输入框为空时写入」。",
            )
            return
        }
        if (!settings.isConfigured()) {
            notifyError(
                project,
                "尚未配置模型服务",
                "请在设置中填写 Base URL、模型名称和 API Key 后再试。",
                withSettingsAction = true,
            )
            return
        }

        runCatching { messageUi?.startLoading() }

        val task = object : Task.Backgroundable(project, "AI 生成提交信息", true) {
            override fun run(indicator: ProgressIndicator) {
                synchronized(lock) { this@CommitGenerationService.indicator = indicator }
                try {
                    val generator = CommitMessageGenerator(project, state)
                    if (state.streamEnabled && document != null) {
                        val accumulated = StringBuilder()
                        var lastFlush = 0L
                        val text = generator.generate(items, indicator) { delta ->
                            accumulated.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= STREAM_FLUSH_INTERVAL_MS) {
                                lastFlush = now
                                flush(document, accumulated.toString())
                            }
                        }
                        ApplicationManager.getApplication().invokeLater {
                            // 流式期间已经写过内容，收尾不再重复判断「仅空时写入」
                            write(document, control, text, state.writeMode, enforceMode = false)
                        }
                    } else {
                        val text = generator.generate(items, indicator)
                        ApplicationManager.getApplication().invokeLater {
                            write(document, control, text, state.writeMode)
                        }
                    }
                } catch (cancelled: ProcessCanceledException) {
                    // 用户取消：保持输入框原样
                } catch (e: LlmException) {
                    val needsSettings = e.kind == LlmError.CONFIG || e.kind == LlmError.AUTH || e.kind == LlmError.NOT_FOUND
                    notifyError(project, "生成失败", e.message ?: "未知错误", withSettingsAction = needsSettings)
                } catch (e: Exception) {
                    notifyError(project, "生成失败", e.message ?: e.javaClass.simpleName)
                } finally {
                    synchronized(lock) { this@CommitGenerationService.indicator = null }
                    ApplicationManager.getApplication().invokeLater {
                        runCatching { messageUi?.stopLoading() }
                    }
                }
            }
        }

        ProgressManager.getInstance().run(task)
    }

    private fun flush(document: Document, text: String) {
        ApplicationManager.getApplication().invokeLater {
            if (document.text == text) return@invokeLater
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(text)
            }
        }
    }

    private fun write(
        document: Document?,
        control: CommitMessageI?,
        text: String,
        writeMode: String,
        enforceMode: Boolean = true,
    ) {
        if (document == null) {
            control?.setCommitMessage(text)
            return
        }
        if (enforceMode && writeMode == "onlyIfEmpty" && document.text.isNotBlank()) {
            notifyInfo(project, "已生成但未写入", "提交信息框已有内容，按当前设置保持原样。")
            return
        }
        if (document.text == text) return
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(text)
        }
    }
}
