package com.lememo.aicommit.notify

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.lememo.aicommit.ui.AiCommitConfigurable

const val NOTIFICATION_GROUP = "AI Commit Message"

fun notifyInfo(project: Project, title: String, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(title, content, NotificationType.INFORMATION)
        .notify(project)
}

fun notifyWarning(project: Project, title: String, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(title, content, NotificationType.WARNING)
        .notify(project)
}

fun notifyError(project: Project, title: String, content: String, withSettingsAction: Boolean = false) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(title, content, NotificationType.ERROR)

    if (withSettingsAction) {
        notification.addAction(
            object : NotificationAction("打开设置") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, AiCommitConfigurable::class.java)
                }
            },
        )
    }
    notification.notify(project)
}
