package com.xai.grok.jetbrains.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class GrokConfigurable : Configurable {
    private val settings = GrokSettings.getInstance()
    private lateinit var cliPath: JBTextField
    private lateinit var launchArgs: JBTextField
    private lateinit var enableMcp: JBCheckBox
    private lateinit var autoRegisterMcp: JBCheckBox
    private lateinit var mcpServerName: JBTextField
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Grok Build"

    override fun createComponent(): JComponent {
        cliPath = JBTextField(settings.cliPath, 40)
        cliPath.emptyText.text = "grok (from PATH)"
        launchArgs = JBTextField(settings.launchArgs, 40)
        launchArgs.emptyText.text = "optional extra args"
        enableMcp = JBCheckBox("Enable IDE MCP server (editor context for Grok)", settings.enableMcp)
        autoRegisterMcp = JBCheckBox("Auto-register MCP in ~/.grok/config.toml", settings.autoRegisterMcp)
        mcpServerName = JBTextField(settings.mcpServerName, 20)

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Grok CLI path:", cliPath, 1, false)
            .addLabeledComponent("Launch args:", launchArgs, 1, false)
            .addComponent(enableMcp, 1)
            .addComponent(autoRegisterMcp, 1)
            .addLabeledComponent("MCP server name:", mcpServerName, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean =
        cliPath.text != settings.cliPath ||
            launchArgs.text != settings.launchArgs ||
            enableMcp.isSelected != settings.enableMcp ||
            autoRegisterMcp.isSelected != settings.autoRegisterMcp ||
            mcpServerName.text != settings.mcpServerName

    override fun apply() {
        settings.cliPath = cliPath.text.trim()
        settings.launchArgs = launchArgs.text.trim()
        settings.enableMcp = enableMcp.isSelected
        settings.autoRegisterMcp = autoRegisterMcp.isSelected
        settings.mcpServerName = mcpServerName.text.trim().ifEmpty { "jetbrains" }
    }

    override fun reset() {
        cliPath.text = settings.cliPath
        launchArgs.text = settings.launchArgs
        enableMcp.isSelected = settings.enableMcp
        autoRegisterMcp.isSelected = settings.autoRegisterMcp
        mcpServerName.text = settings.mcpServerName
    }
}
