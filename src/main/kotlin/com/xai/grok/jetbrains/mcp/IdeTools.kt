package com.xai.grok.jetbrains.mcp

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.xai.grok.jetbrains.EditorContext
import java.util.concurrent.atomic.AtomicReference

/**
 * JetBrains IDE operations exposed as MCP tools.
 * All UI/document work is marshalled onto the correct threads.
 */
class IdeTools(private val project: Project) {

    fun getEditorContext(): Map<String, Any?> = read {
        val sel = EditorContext.currentSelection(project)
        val open = EditorContext.openEditors(project)
        mapOf(
            "workspace" to project.basePath,
            "activeFile" to sel.filePath,
            "selection" to sel.selection?.let {
                mapOf(
                    "start" to mapOf("line" to it.start.line, "character" to it.start.character),
                    "end" to mapOf("line" to it.end.line, "character" to it.end.character),
                )
            },
            "selectedText" to sel.text,
            "openFiles" to open.map {
                mapOf(
                    "path" to it.path,
                    "isActive" to it.isActive,
                    "cursorLine" to it.cursorLine,
                    "cursorCharacter" to it.cursorCharacter,
                )
            },
        )
    }

    fun getSelection(): Map<String, Any?> = read {
        val sel = EditorContext.currentSelection(project)
        mapOf(
            "filePath" to sel.filePath,
            "text" to sel.text,
            "lineCount" to sel.lineCount,
            "selection" to sel.selection?.let {
                mapOf(
                    "start" to mapOf("line" to it.start.line, "character" to it.start.character),
                    "end" to mapOf("line" to it.end.line, "character" to it.end.character),
                )
            },
            "atMention" to EditorContext.atMention(project),
        )
    }

    fun getOpenFiles(): Map<String, Any?> = read {
        mapOf(
            "files" to EditorContext.openEditors(project).map {
                mapOf(
                    "path" to it.path,
                    "isActive" to it.isActive,
                    "cursorLine" to it.cursorLine,
                    "cursorCharacter" to it.cursorCharacter,
                )
            },
        )
    }

    fun openFile(path: String, line: Int? = null, preview: Boolean = false): Map<String, Any?> {
        val result = AtomicReference<Map<String, Any?>>()
        ApplicationManager.getApplication().invokeAndWait {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            if (vf == null) {
                result.set(mapOf("ok" to false, "error" to "File not found: $path"))
                return@invokeAndWait
            }
            val descriptor = if (line != null && line > 0) {
                OpenFileDescriptor(project, vf, line - 1, 0)
            } else {
                OpenFileDescriptor(project, vf)
            }
            FileEditorManager.getInstance(project).openTextEditor(descriptor, !preview)
            result.set(mapOf("ok" to true, "path" to path, "line" to line))
        }
        return result.get()
    }

    fun closeTab(path: String): Map<String, Any?> {
        val result = AtomicReference<Map<String, Any?>>()
        ApplicationManager.getApplication().invokeAndWait {
            val vf = LocalFileSystem.getInstance().findFileByPath(path)
            if (vf == null) {
                result.set(mapOf("ok" to false, "error" to "File not open / not found: $path"))
                return@invokeAndWait
            }
            FileEditorManager.getInstance(project).closeFile(vf)
            result.set(mapOf("ok" to true, "path" to path))
        }
        return result.get()
    }

    fun reformatFile(path: String): Map<String, Any?> {
        val result = AtomicReference<Map<String, Any?>>()
        ApplicationManager.getApplication().invokeAndWait {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            if (vf == null) {
                result.set(mapOf("ok" to false, "error" to "File not found: $path"))
                return@invokeAndWait
            }
            val psi = PsiManager.getInstance(project).findFile(vf)
            if (psi == null) {
                result.set(mapOf("ok" to false, "error" to "Not a PSI file: $path"))
                return@invokeAndWait
            }
            WriteCommandAction.runWriteCommandAction(project) {
                CodeStyleManager.getInstance(project).reformat(psi)
            }
            result.set(mapOf("ok" to true, "path" to path))
        }
        return result.get()
    }

    fun getDiagnostics(path: String? = null): Map<String, Any?> = read {
        val files = if (path != null) {
            listOfNotNull(LocalFileSystem.getInstance().findFileByPath(path))
        } else {
            FileEditorManager.getInstance(project).openFiles.toList()
        }
        val out = mutableListOf<Map<String, Any?>>()
        for (vf in files) {
            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: continue
            val infos = mutableListOf<HighlightInfo>()
            DaemonCodeAnalyzerEx.processHighlights(
                doc,
                project,
                HighlightSeverity.WEAK_WARNING,
                0,
                doc.textLength,
            ) { info ->
                infos.add(info)
                true
            }
            for (info in infos) {
                val start = info.startOffset.coerceIn(0, doc.textLength)
                val line = if (doc.textLength == 0) 0 else doc.getLineNumber(start)
                out.add(
                    mapOf(
                        "file" to vf.path,
                        "severity" to (info.severity?.myVal ?: 0),
                        "message" to (info.description ?: info.toolTip ?: ""),
                        "line" to line,
                        "startOffset" to info.startOffset,
                        "endOffset" to info.endOffset,
                        "range" to TextRange(info.startOffset, info.endOffset).toString(),
                    ),
                )
            }
        }
        mapOf("diagnostics" to out, "count" to out.size)
    }

    fun openDiff(path: String, newText: String, tabName: String? = null): Map<String, Any?> {
        val result = AtomicReference<Map<String, Any?>>()
        ApplicationManager.getApplication().invokeAndWait {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            val factory = DiffContentFactory.getInstance()
            val left = if (vf != null) {
                factory.create(project, vf)
            } else {
                factory.create(project, "")
            }
            val right = factory.create(project, newText, vf?.fileType)
            val title = tabName ?: "Grok: ${vf?.name ?: path}"
            val request = SimpleDiffRequest(title, left, right, path, "Proposed")
            DiffManager.getInstance().showDiff(project, request)
            result.set(mapOf("ok" to true, "path" to path, "tabName" to title))
        }
        return result.get()
    }

    fun applyText(path: String, content: String): Map<String, Any?> {
        val result = AtomicReference<Map<String, Any?>>()
        ApplicationManager.getApplication().invokeAndWait {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            if (vf == null) {
                result.set(mapOf("ok" to false, "error" to "File not found: $path"))
                return@invokeAndWait
            }
            WriteCommandAction.runWriteCommandAction(project) {
                VfsUtil.saveText(vf, content)
                FileDocumentManager.getInstance().reloadFiles(vf)
            }
            result.set(mapOf("ok" to true, "path" to path))
        }
        return result.get()
    }

    private fun <T> read(block: () -> T): T =
        ReadAction.compute(ThrowableComputable { block() })
}

internal fun jsonEscape(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
