package com.xai.grok.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "GrokBuildSettings", storages = [Storage("grokBuild.xml")])
class GrokSettings : PersistentStateComponent<GrokSettings> {
    /** Path to the `grok` binary. Empty = resolve from PATH. */
    var cliPath: String = ""

    /** Extra args when launching Grok (space-separated). */
    var launchArgs: String = ""

    /** Start the local IDE MCP server on project open. */
    var enableMcp: Boolean = true

    /** Write/update [mcp_servers.jetbrains] in ~/.grok/config.toml. */
    var autoRegisterMcp: Boolean = true

    /** MCP server name used in config.toml. */
    var mcpServerName: String = "jetbrains"

    override fun getState(): GrokSettings = this

    override fun loadState(state: GrokSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): GrokSettings =
            ApplicationManager.getApplication().getService(GrokSettings::class.java)
    }
}
