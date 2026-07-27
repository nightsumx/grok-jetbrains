package com.xai.grok.jetbrains.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.xai.grok.jetbrains.settings.GrokSettings
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object GrokTerminal {
    private val log = Logger.getInstance(GrokTerminal::class.java)
    private const val TAB_NAME = "Grok Build"

    /** Bracketed paste: terminals treat the payload as a single paste, so newlines don't submit. */
    private const val BRACKETED_PASTE_START = "\u001b[200~"
    private const val BRACKETED_PASTE_END = "\u001b[201~"

    fun resolveCli(): String? {
        val configured = GrokSettings.getInstance().cliPath.trim()
        if (configured.isNotEmpty()) {
            val f = File(configured)
            if (f.isFile && f.canExecute()) return f.absolutePath
            if (!configured.contains('/') && !configured.contains('\\')) return configured
        }
        val pathEnv = System.getenv("PATH") ?: return "grok"
        for (dir in pathEnv.split(File.pathSeparator)) {
            val candidate = File(dir, "grok")
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
            val win = File(dir, "grok.exe")
            if (win.isFile) return win.absolutePath
        }
        return "grok"
    }

    fun openOrFocus(project: Project): TerminalWidget? {
        val result = AtomicReference<TerminalWidget?>()
        ApplicationManager.getApplication().invokeAndWait {
            try {
                result.set(openOrFocusInternal(project))
            } catch (t: Throwable) {
                log.warn("Failed to open Grok terminal", t)
                result.set(null)
            }
        }
        return result.get()
    }

    private fun openOrFocusInternal(project: Project): TerminalWidget? {
        val twm = ToolWindowManager.getInstance(project)
        val toolWindow = twm.getToolWindow("Terminal") ?: return null
        toolWindow.show()

        val manager = TerminalToolWindowManager.getInstance(project)
        val existing = findGrokWidget(toolWindow.contentManager.contents)
        if (existing != null) {
            selectContentForWidget(toolWindow.contentManager.contents, existing)
            return existing
        }

        val cwd = project.basePath
        val widget = manager.createShellWidget(cwd, TAB_NAME, true, true)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Thread.sleep(400)
            } catch (_: InterruptedException) {
            }
            ApplicationManager.getApplication().invokeLater {
                launchGrok(widget)
            }
        }
        return widget
    }

    fun launchGrok(widget: TerminalWidget) {
        val cli = resolveCli() ?: "grok"
        val extra = GrokSettings.getInstance().launchArgs.trim()
        val cmd = if (extra.isEmpty()) cli else "$cli $extra"
        try {
            widget.sendCommandToExecute(cmd)
        } catch (t: Throwable) {
            log.warn("sendCommandToExecute failed, typing instead", t)
            typeText(widget, cmd + "\n")
        }
    }

    /** Type raw bytes into the terminal TTY (no bracketed paste). */
    fun typeText(widget: TerminalWidget, text: String) {
        try {
            val connector = widget.ttyConnector
            if (connector != null) {
                connector.write(text)
                return
            }
        } catch (t: Throwable) {
            log.warn("tty write failed", t)
        }
        try {
            // Flatten newlines so we never auto-submit multi-line via execute.
            widget.sendCommandToExecute(text.replace('\n', ' ').trimEnd())
        } catch (t: Throwable) {
            log.warn("fallback type failed", t)
        }
    }

    /**
     * Paste [text] into the Grok prompt via bracketed-paste so multi-line
     * content lands as one buffer, not a series of Enter submits.
     */
    fun pasteText(widget: TerminalWidget, text: String) {
        val payload = BRACKETED_PASTE_START + text + BRACKETED_PASTE_END
        try {
            val connector = widget.ttyConnector
            if (connector != null) {
                connector.write(payload)
                return
            }
        } catch (t: Throwable) {
            log.warn("bracketed paste failed, falling back to raw write", t)
        }
        typeText(widget, text)
    }

    fun sendAtMention(project: Project, mention: String): Boolean {
        val widget = openOrFocus(project) ?: return false
        val payload = if (mention.endsWith(" ")) mention else "$mention "
        typeText(widget, payload)
        return true
    }

    /**
     * Inject a multi-line prompt prefix (file + selection body) into Grok.
     * If the Grok tab is still launching, wait briefly then paste.
     */
    fun sendPromptPrefix(project: Project, prefix: String): Boolean {
        val widget = openOrFocus(project) ?: return false
        // Persist a copy so the user / tools can re-read it if paste is lost.
        writeLastSelectionFile(prefix)

        val isNew = widget.ttyConnector == null
        if (isNew) {
            // Shell just opened; grok is launched async. Wait for TTY + TUI.
            ApplicationManager.getApplication().executeOnPooledThread {
                waitForTty(widget, timeoutMs = 4000)
                // Extra beat for grok TUI to take over the prompt.
                try {
                    Thread.sleep(600)
                } catch (_: InterruptedException) {
                }
                ApplicationManager.getApplication().invokeLater {
                    pasteText(widget, prefix)
                }
            }
        } else {
            pasteText(widget, prefix)
        }
        return true
    }

    private fun waitForTty(widget: TerminalWidget, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                if (widget.ttyConnector != null) return
            } catch (_: Throwable) {
            }
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun writeLastSelectionFile(prefix: String) {
        try {
            val dir = File(System.getProperty("user.home"), ".grok/ide")
            dir.mkdirs()
            File(dir, "last-selection.md").writeText(prefix)
        } catch (t: Throwable) {
            log.warn("could not write last-selection.md", t)
        }
    }

    private fun findGrokWidget(contents: Array<Content>): TerminalWidget? {
        for (content in contents) {
            if (content.displayName?.contains("Grok", ignoreCase = true) == true) {
                val w = TerminalToolWindowManager.findWidgetByContent(content)
                if (w != null) return w
            }
        }
        for (content in contents) {
            if (content.displayName == TAB_NAME) {
                return TerminalToolWindowManager.findWidgetByContent(content)
            }
        }
        return null
    }

    private fun selectContentForWidget(contents: Array<Content>, widget: TerminalWidget) {
        for (content in contents) {
            if (TerminalToolWindowManager.findWidgetByContent(content) === widget) {
                content.manager?.setSelectedContent(content, true)
                return
            }
        }
    }
}
