package com.xai.grok.jetbrains

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class SelectionPoint(val line: Int, val character: Int)

data class SelectionRange(
    val start: SelectionPoint,
    val end: SelectionPoint,
)

data class EditorSelection(
    val filePath: String?,
    val text: String?,
    val selection: SelectionRange?,
    val lineCount: Int,
)

data class OpenEditorInfo(
    val path: String,
    val isActive: Boolean,
    val cursorLine: Int?,
    val cursorCharacter: Int?,
    val selection: SelectionRange?,
)

object EditorContext {
    fun activeEditor(project: Project): Editor? =
        FileEditorManager.getInstance(project).selectedTextEditor

    fun virtualFile(editor: Editor): VirtualFile? =
        FileDocumentManager.getInstance().getFile(editor.document)

    fun currentSelection(project: Project): EditorSelection {
        val editor = activeEditor(project)
            ?: return EditorSelection(null, null, null, 0)
        val file = virtualFile(editor)
        val model = editor.selectionModel
        val hasSel = model.hasSelection()
        val startOffset = if (hasSel) model.selectionStart else editor.caretModel.offset
        val endOffset = if (hasSel) model.selectionEnd else editor.caretModel.offset
        val start = offsetToPoint(editor, startOffset)
        val end = offsetToPoint(editor, endOffset)
        val text = if (hasSel) model.selectedText else null
        var lineCount = if (hasSel) end.line - start.line + 1 else 0
        if (hasSel && end.character == 0 && lineCount > 0) lineCount--
        return EditorSelection(
            filePath = file?.path,
            text = text,
            selection = if (hasSel) SelectionRange(start, end) else null,
            lineCount = lineCount.coerceAtLeast(0),
        )
    }

    fun openEditors(project: Project): List<OpenEditorInfo> {
        val fem = FileEditorManager.getInstance(project)
        val active = fem.selectedTextEditor?.let { virtualFile(it)?.path }
        return fem.openFiles.map { vf ->
            val editor = (fem.getSelectedEditor(vf) as? TextEditor)?.editor
            val caret = editor?.caretModel
            val selModel = editor?.selectionModel
            val sel = if (editor != null && selModel != null && selModel.hasSelection()) {
                SelectionRange(
                    offsetToPoint(editor, selModel.selectionStart),
                    offsetToPoint(editor, selModel.selectionEnd),
                )
            } else null
            OpenEditorInfo(
                path = vf.path,
                isActive = vf.path == active,
                cursorLine = caret?.logicalPosition?.line,
                cursorCharacter = caret?.logicalPosition?.column,
                selection = sel,
            )
        }
    }

    /**
     * Grok @-ref form: `@path:start-end` (1-based inclusive lines).
     * Bare file when there is no multi-line selection.
     */
    fun atMention(project: Project): String? {
        val path = currentSelection(project).filePath ?: return null
        return atMentionFor(currentSelection(project), path)
    }

    /**
     * Prompt prefix injected in front of the user's message when they hit
     * "Send to Grok". Mirrors Claude Code's `selected_lines_in_ide` attachment:
     * path + line range + actual selected text — no MCP round-trip needed.
     *
     * Returns null when there is nothing to send.
     */
    fun injectPromptPrefix(project: Project, maxContentChars: Int = DEFAULT_MAX_CONTENT): String? {
        val sel = currentSelection(project)
        val path = sel.filePath ?: return null
        val relative = relativeToProject(project, path)
        val range = lineRange1Based(sel)

        val text = sel.text
        return if (text != null && text.isNotEmpty() && range != null) {
            val (start, end) = range
            val body = truncate(text, maxContentChars)
            buildString {
                appendLine("[IDE selection]")
                appendLine("File: $relative")
                appendLine("Absolute: $path")
                appendLine("Lines: $start-$end")
                appendLine()
                appendLine("```")
                append(body)
                if (!body.endsWith("\n")) appendLine()
                appendLine("```")
                appendLine()
                // Leave the cursor ready for the user's actual prompt.
            }
        } else {
            // No selection text — just tell Grok which file is focused (Claude's opened_file_in_ide).
            buildString {
                appendLine("[IDE open file]")
                appendLine("File: $relative")
                appendLine("Absolute: $path")
                if (range != null) {
                    appendLine("Cursor line: ${range.first}")
                }
                appendLine()
            }
        }
    }

    fun lineRange1Based(sel: EditorSelection): Pair<Int, Int>? {
        val range = sel.selection ?: return null
        if (sel.lineCount <= 0 && sel.text.isNullOrEmpty()) {
            // caret only
            return (range.start.line + 1) to (range.start.line + 1)
        }
        val start = range.start.line + 1
        val end = if (range.end.character == 0 && range.end.line > range.start.line) {
            range.end.line
        } else {
            range.end.line + 1
        }
        return start to end.coerceAtLeast(start)
    }

    private fun atMentionFor(sel: EditorSelection, path: String): String {
        val range = lineRange1Based(sel)
        return if (range != null && sel.lineCount > 0) {
            val (start, end) = range
            if (start == end) "@$path:$start" else "@$path:$start-$end"
        } else {
            "@$path"
        }
    }

    private fun relativeToProject(project: Project, absolute: String): String {
        val base = project.basePath ?: return absolute
        val prefix = if (base.endsWith("/")) base else "$base/"
        return if (absolute.startsWith(prefix)) absolute.removePrefix(prefix) else absolute
    }

    private fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.substring(0, max) + "\n... (truncated, ${text.length - max} more chars)"
    }

    private fun offsetToPoint(editor: Editor, offset: Int): SelectionPoint {
        val pos = editor.offsetToLogicalPosition(offset)
        return SelectionPoint(pos.line, pos.column)
    }

    const val DEFAULT_MAX_CONTENT: Int = 32_000
}
