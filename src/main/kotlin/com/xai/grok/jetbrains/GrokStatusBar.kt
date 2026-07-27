package com.xai.grok.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.xai.grok.jetbrains.services.IdeBridgeService
import java.awt.Component
import java.awt.event.MouseEvent

class GrokStatusBarFactory : StatusBarWidgetFactory {
    override fun getId(): String = "GrokBuildStatus"
    override fun getDisplayName(): String = "Grok Build"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = GrokStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class GrokStatusBarWidget(private val project: Project) :
    StatusBarWidget,
    StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = "GrokBuildStatus"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val bridge = IdeBridgeService.getInstance()
        return if (bridge.isRunning) "Grok:${bridge.port}" else "Grok"
    }

    override fun getTooltipText(): String {
        val bridge = IdeBridgeService.getInstance()
        return if (bridge.isRunning) {
            "Grok Build IDE bridge · MCP http://127.0.0.1:${bridge.port}/mcp (click to restart)"
        } else {
            "Grok Build · IDE bridge off (click to start)"
        }
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        IdeBridgeService.getInstance().let { bridge ->
            if (bridge.isRunning) bridge.stop()
            bridge.startIfEnabled(project)
        }
        statusBar?.updateWidget(ID())
    }
}
