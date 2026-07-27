package com.xai.grok.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.xai.grok.jetbrains.services.IdeBridgeService
import com.xai.grok.jetbrains.services.SelectionAutoInject
import com.xai.grok.jetbrains.terminal.GrokTerminal

class OpenGrokInTerminalAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        IdeBridgeService.getInstance().startIfEnabled(project)
        val widget = GrokTerminal.openOrFocus(project)
        if (widget == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GrokBuildError")
                .createNotification(
                    "Grok Build",
                    "Could not open the Terminal tool window. Is the Terminal plugin enabled?",
                    NotificationType.ERROR,
                )
                .notify(project)
            return
        }
        // Full-auto: inject current selection after Grok is up.
        SelectionAutoInject.getInstance(project).onGrokOpened()
    }
}
