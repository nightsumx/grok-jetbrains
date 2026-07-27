package com.xai.grok.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.xai.grok.jetbrains.EditorContext
import com.xai.grok.jetbrains.services.IdeBridgeService
import com.xai.grok.jetbrains.terminal.GrokTerminal

/**
 * Inject the current IDE selection (file + lines + body) at the front of the
 * Grok prompt — same idea as Claude Code's automatic `selected_lines_in_ide`
 * attachment. The model sees the code without calling any MCP tool.
 */
class SendToGrokAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        IdeBridgeService.getInstance().startIfEnabled(project)

        var prefix = EditorContext.injectPromptPrefix(project)
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

        val sel = EditorContext.currentSelection(project)
        val summary = when {
            !sel.text.isNullOrEmpty() && sel.filePath != null -> {
                val range = EditorContext.lineRange1Based(sel)
                val lines = if (range != null) ":${range.first}-${range.second}" else ""
                "Injected selection ${sel.filePath.substringAfterLast('/')}$lines (${sel.text!!.length} chars)"
            }
            sel.filePath != null -> "Injected open file ${sel.filePath.substringAfterLast('/')}"
            else -> "Injected IDE context"
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
