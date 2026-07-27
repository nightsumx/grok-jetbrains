package com.xai.grok.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.Alarm
import com.xai.grok.jetbrains.settings.GrokSettings
import com.xai.grok.jetbrains.terminal.GrokTerminal
import java.util.concurrent.atomic.AtomicReference

/**
 * Full-auto selection → Grok prompt inject (Claude-style host attachment).
 *
 * Strategy:
 * 1. Always persist latest selection (via SelectionTracker).
 * 2. When Grok terminal opens / is focused → inject current selection.
 * 3. When selection changes and Grok tab is already open → re-inject (debounced).
 * 4. Never auto-submits (bracketed paste only).
 */
@Service(Service.Level.PROJECT)
class SelectionAutoInject(private val project: Project) : Disposable {
    private val log = Logger.getInstance(SelectionAutoInject::class.java)
    private val injectAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val lastInjectedHash = AtomicReference("")
    private val pendingHash = AtomicReference<String?>(null)

    init {
        val tracker = SelectionTracker.getInstance(project)
        val d = tracker.addListener { snap ->
            onSelectionChanged(snap)
        }
        // Dispose listener with this service
        com.intellij.openapi.util.Disposer.register(this, d)

        project.messageBus.connect(this).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    maybeInjectOnGrokVisible()
                }

                override fun toolWindowShown(toolWindow: com.intellij.openapi.wm.ToolWindow) {
                    if (toolWindow.id == "Terminal") {
                        maybeInjectOnGrokVisible(force = true)
                    }
                }
            },
        )
    }

    fun start() {
        // Warm tracker + inject if Grok already open
        ApplicationManager.getApplication().invokeLater {
            maybeInjectOnGrokVisible(force = false)
        }
    }

    /** Manual / Open-Grok path: inject now if settings allow. */
    fun injectNow(force: Boolean = true): Boolean {
        if (!GrokSettings.getInstance().autoInjectSelection && !force) return false
        val snap = SelectionTracker.getInstance(project).current()
        val prefix = snap.prefix ?: return false
        return doInject(prefix, snap.hash, openIfNeeded = force)
    }

    /** After a manual paste, keep auto-inject hash in sync to avoid double paste. */
    fun markInjected(hash: String) {
        lastInjectedHash.set(hash)
        pendingHash.set(null)
    }

    private fun onSelectionChanged(snap: SelectionTracker.TrackedSelection) {
        val settings = GrokSettings.getInstance()
        if (!settings.autoInjectSelection) return
        if (!snap.hasAnything) return

        // Always keep "dirty" so focusing Grok later injects the latest.
        pendingHash.set(snap.hash)

        if (!settings.injectOnSelectionChange) return
        // Only live-re-inject when Grok tab already exists (don't open terminal spontaneously).
        if (!GrokTerminal.isGrokOpen(project)) return

        // Prefer injecting selection body; open-file-only on focus to reduce noise.
        if (!snap.hasSelectionBody && !settings.injectOpenFileOnChange) return

        scheduleInject(snap.hash, snap.prefix ?: return, openIfNeeded = false)
    }

    private fun maybeInjectOnGrokVisible(force: Boolean = false) {
        val settings = GrokSettings.getInstance()
        if (!settings.autoInjectSelection) return
        if (!settings.injectOnGrokFocus && !force) return
        if (!GrokTerminal.isGrokOpen(project) && !force) return

        val snap = SelectionTracker.getInstance(project).current()
        val prefix = snap.prefix ?: return
        if (snap.hash == lastInjectedHash.get() && !force) return
        scheduleInject(snap.hash, prefix, openIfNeeded = false)
    }

    private fun scheduleInject(hash: String, prefix: String, openIfNeeded: Boolean) {
        injectAlarm.cancelAllRequests()
        injectAlarm.addRequest({
            doInject(prefix, hash, openIfNeeded)
        }, INJECT_DEBOUNCE_MS)
    }

    private fun doInject(prefix: String, hash: String, openIfNeeded: Boolean): Boolean {
        if (project.isDisposed) return false
        if (hash == lastInjectedHash.get() && !openIfNeeded) {
            // Still allow re-inject when forcing open path
        }
        if (hash == lastInjectedHash.get()) {
            // Skip identical re-paste unless openIfNeeded was requested for a brand-new terminal
            if (!openIfNeeded) return true
        }

        val ok = if (openIfNeeded) {
            GrokTerminal.sendPromptPrefix(project, prefix)
        } else {
            GrokTerminal.injectIfOpen(project, prefix)
        }
        if (ok) {
            lastInjectedHash.set(hash)
            pendingHash.set(null)
            log.info("Auto-injected IDE selection into Grok (hash=$hash)")
        }
        return ok
    }

    /** Called after Grok terminal is opened by user action. */
    fun onGrokOpened() {
        val settings = GrokSettings.getInstance()
        if (!settings.autoInjectSelection) return
        val snap = SelectionTracker.getInstance(project).current()
        val prefix = snap.prefix ?: return
        // Wait for TUI; sendPromptPrefix already waits when new.
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
            }
            ApplicationManager.getApplication().invokeLater {
                doInject(prefix, snap.hash, openIfNeeded = true)
            }
        }
    }

    override fun dispose() {
        injectAlarm.cancelAllRequests()
    }

    companion object {
        private const val INJECT_DEBOUNCE_MS = 400

        fun getInstance(project: Project): SelectionAutoInject =
            project.getService(SelectionAutoInject::class.java)
    }
}
