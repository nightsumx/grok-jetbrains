package com.xai.grok.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.xai.grok.jetbrains.EditorContext
import com.xai.grok.jetbrains.EditorSelection
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Always-on IDE selection tracker (Claude-style).
 * Debounces editor events, persists to ~/.grok/ide/, notifies listeners.
 */
@Service(Service.Level.PROJECT)
class SelectionTracker(private val project: Project) : Disposable {
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val snapshot = AtomicReference(TrackedSelection.EMPTY)
    private val listeners = CopyOnWriteArrayList<(TrackedSelection) -> Unit>()

    data class TrackedSelection(
        val filePath: String?,
        val text: String?,
        val lineStart: Int?,
        val lineEnd: Int?,
        val prefix: String?,
        val hash: String,
    ) {
        val hasSelectionBody: Boolean get() = !text.isNullOrEmpty()
        val hasAnything: Boolean get() = !prefix.isNullOrEmpty()

        companion object {
            val EMPTY = TrackedSelection(null, null, null, null, null, "")
        }
    }

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                if (e.editor.project != project) return
                scheduleRefresh()
            }
        }, this)
        multicaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (event.editor.project != project) return
                // Caret-only moves update "open file" context when there is no selection.
                scheduleRefresh()
            }
        }, this)

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    scheduleRefresh()
                }
            },
        )

        // Initial snapshot
        ApplicationManager.getApplication().invokeLater { refreshNow() }
    }

    fun current(): TrackedSelection = snapshot.get()

    fun addListener(listener: (TrackedSelection) -> Unit): Disposable {
        listeners.add(listener)
        return Disposable { listeners.remove(listener) }
    }

    private fun scheduleRefresh() {
        alarm.cancelAllRequests()
        alarm.addRequest({ refreshNow() }, DEBOUNCE_MS)
    }

    private fun refreshNow() {
        if (project.isDisposed) return
        val sel = EditorContext.currentSelection(project)
        val next = toTracked(sel)
        val prev = snapshot.get()
        if (prev.hash == next.hash) return
        snapshot.set(next)
        persist(next)
        for (l in listeners) {
            try {
                l(next)
            } catch (_: Throwable) {
            }
        }
    }

    private fun toTracked(sel: EditorSelection): TrackedSelection {
        val path = sel.filePath
        if (path == null) return TrackedSelection.EMPTY
        val range = EditorContext.lineRange1Based(sel)
        val prefix = EditorContext.injectPromptPrefix(project) ?: return TrackedSelection.EMPTY
        val hash = listOf(
            path,
            range?.first?.toString() ?: "",
            range?.second?.toString() ?: "",
            sel.text ?: "",
        ).joinToString("\u0000").hashCode().toString()
        return TrackedSelection(
            filePath = path,
            text = sel.text,
            lineStart = range?.first,
            lineEnd = range?.second,
            prefix = prefix,
            hash = hash,
        )
    }

    private fun persist(t: TrackedSelection) {
        try {
            val dir = File(System.getProperty("user.home"), ".grok/ide")
            dir.mkdirs()
            val prefix = t.prefix
            if (prefix != null) {
                File(dir, "last-selection.md").writeText(prefix)
            }
            val json = buildString {
                append('{')
                append("\"filePath\":").append(jsonStr(t.filePath)).append(',')
                append("\"lineStart\":").append(t.lineStart ?: "null").append(',')
                append("\"lineEnd\":").append(t.lineEnd ?: "null").append(',')
                append("\"hasSelection\":").append(t.hasSelectionBody).append(',')
                append("\"textLength\":").append(t.text?.length ?: 0).append(',')
                append("\"updatedAt\":").append(System.currentTimeMillis())
                append('}')
            }
            File(dir, "selection.json").writeText(json)
        } catch (_: Throwable) {
        }
    }

    private fun jsonStr(s: String?): String {
        if (s == null) return "null"
        return buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
    }

    override fun dispose() {
        alarm.cancelAllRequests()
        listeners.clear()
    }

    companion object {
        private const val DEBOUNCE_MS = 250

        fun getInstance(project: Project): SelectionTracker =
            project.getService(SelectionTracker::class.java)
    }
}
