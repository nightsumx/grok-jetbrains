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

    /**
     * Full-auto: track selection and inject into Grok prompt without a special key.
     * (Still never auto-submits — only pastes into the input buffer.)
     */
    var autoInjectSelection: Boolean = true

    /** When Grok terminal is focused/opened, inject the latest selection. */
    var injectOnGrokFocus: Boolean = true

    /**
     * When the editor selection changes and Grok is already open, re-inject.
     * Debounced; only selection body by default (see injectOpenFileOnChange).
     */
    var injectOnSelectionChange: Boolean = true

    /** Also re-inject "open file" caret context on every caret move (noisier). */
    var injectOpenFileOnChange: Boolean = false

    override fun getState(): GrokSettings = this

    override fun loadState(state: GrokSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): GrokSettings =
            ApplicationManager.getApplication().getService(GrokSettings::class.java)
    }
}
