package com.xai.grok.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.xai.grok.jetbrains.services.IdeBridgeService

class GrokStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        IdeBridgeService.getInstance().startIfEnabled(project)
    }
}
