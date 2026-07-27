package com.xai.grok.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.xai.grok.jetbrains.EditorContext
import com.xai.grok.jetbrains.services.IdeBridgeService
import com.xai.grok.jetbrains.services.SelectionAutoInject
import com.xai.grok.jetbrains.services.SelectionTracker
import com.xai.grok.jetbrains.terminal.GrokTerminal

/**
 * Manual force-inject. Full-auto usually makes this unnecessary;
 * keep it for re-send if paste was lost or auto-inject is disabled.
 */
class SendToGrokAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        IdeBridgeService.getInstance().startIfEnabled(project)

        val tracked = SelectionTracker.getInstance(project).current()
        var prefix = tracked.prefix ?: EditorContext.injectPromptPrefix(project)
        if (prefix == null) {
            val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
            if (vf == null) {
                notify(project, "No file or selection to send.", NotificationType.WARNING)
                return
            }
            prefix = buildString {
                appendLine("[IDE open file]")
                appendLine("File: ${vf.path}")
                appendLine()
            }
        }

        if (!GrokTerminal.sendPromptPrefix(project, prefix)) {
            notify(project, "Could not open Grok terminal.", NotificationType.ERROR)
            return
        }
        if (tracked.hash.isNotEmpty()) {
            SelectionAutoInject.getInstance(project).markInjected(tracked.hash)
        }

        val summary = if (tracked.hasSelectionBody) {
            val lines = if (tracked.lineStart != null) ":${tracked.lineStart}-${tracked.lineEnd}" else ""
            "Injected ${(tracked.filePath ?: "").substringAfterLast('/')}$lines"
        } else {
            "Injected IDE context"
        }
        notify(project, summary, NotificationType.INFORMATION)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun notify(project: com.intellij.openapi.project.Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GrokBuild")
            .createNotification("Grok Build", msg, type)
            .notify(project)
    }
}
