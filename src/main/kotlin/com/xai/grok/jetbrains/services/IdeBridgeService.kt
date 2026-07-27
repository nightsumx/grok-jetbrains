package com.xai.grok.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.xai.grok.jetbrains.mcp.IdeTools
import com.xai.grok.jetbrains.mcp.McpHttpServer
import com.xai.grok.jetbrains.settings.GrokSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-scoped IDE bridge: one MCP HTTP port for the whole IDE.
 * Tool calls are routed to the currently focused project (fallback: first open).
 */
@Service(Service.Level.APP)
class IdeBridgeService : Disposable {
    private val log = Logger.getInstance(IdeBridgeService::class.java)
    private val serverRef = AtomicReference<McpHttpServer?>(null)
    private val authTokenRef = AtomicReference<String?>(null)

    val port: Int get() = serverRef.get()?.port ?: 0
    val isRunning: Boolean get() = port > 0
    val authToken: String? get() = authTokenRef.get()

    fun startIfEnabled(hintProject: Project? = null) {
        val settings = GrokSettings.getInstance()
        if (!settings.enableMcp) {
            stop()
            return
        }
        if (isRunning) {
            // Refresh lockfile workspace for the active project.
            refreshLockfile(hintProject)
            return
        }
        try {
            val token = LockFile.generateAuthToken()
            val server = McpHttpServer(projectResolver = { resolveProject(hintProject) })
            val p = server.start(token)
            authTokenRef.set(token)
            serverRef.set(server)
            val project = resolveProject(hintProject)
            if (project != null) {
                LockFile.write(p, project, token)
            }
            GrokConfigRegistrar.register(p, token)
            log.info("Grok IDE bridge started on port $p")
        } catch (t: Throwable) {
            log.warn("Failed to start Grok IDE bridge", t)
        }
    }

    fun stop() {
        val server = serverRef.getAndSet(null) ?: return
        val p = server.port
        try {
            server.stop()
        } catch (_: Exception) {
        }
        if (p > 0) {
            try {
                LockFile.delete(p)
            } catch (_: Exception) {
            }
        }
        authTokenRef.set(null)
        try {
            GrokConfigRegistrar.unregister()
        } catch (_: Exception) {
        }
    }

    fun refreshLockfile(hint: Project?) {
        val p = port
        val token = authToken ?: return
        if (p <= 0) return
        val project = resolveProject(hint) ?: return
        try {
            LockFile.write(p, project, token)
        } catch (_: Exception) {
        }
    }

    private fun resolveProject(hint: Project?): Project? {
        if (hint != null && !hint.isDisposed) return hint
        // Prefer project whose frame is focused.
        val open = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        if (open.isEmpty()) return null
        for (p in open) {
            val frame = WindowManager.getInstance().getFrame(p) ?: continue
            if (frame.isActive || frame.isFocused) return p
        }
        return open.first()
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(): IdeBridgeService =
            ApplicationManager.getApplication().getService(IdeBridgeService::class.java)

        /** Convenience for call sites that still have a Project. */
        fun getInstance(project: Project): IdeBridgeService = getInstance().also {
            // Touch ensures bridge is aware of this project.
            if (it.isRunning) it.refreshLockfile(project)
        }
    }
}
