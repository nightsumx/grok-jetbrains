package com.xai.grok.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.xai.grok.jetbrains.services.IdeBridgeService
import com.xai.grok.jetbrains.services.SelectionAutoInject
import com.xai.grok.jetbrains.services.SelectionTracker

class GrokStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        IdeBridgeService.getInstance().startIfEnabled(project)
        // Touch services so tracking + full-auto inject start.
        SelectionTracker.getInstance(project)
        SelectionAutoInject.getInstance(project).start()
    }
}
