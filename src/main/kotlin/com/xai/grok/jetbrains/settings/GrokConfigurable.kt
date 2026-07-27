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
    private lateinit var autoInjectSelection: JBCheckBox
    private lateinit var injectOnGrokFocus: JBCheckBox
    private lateinit var injectOnSelectionChange: JBCheckBox
    private lateinit var injectOpenFileOnChange: JBCheckBox
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

        autoInjectSelection = JBCheckBox(
            "Auto-inject selection into Grok prompt (full-auto, no hotkey required)",
            settings.autoInjectSelection,
        )
        injectOnGrokFocus = JBCheckBox(
            "Inject when Grok terminal is opened / focused",
            settings.injectOnGrokFocus,
        )
        injectOnSelectionChange = JBCheckBox(
            "Re-inject when selection changes while Grok is open",
            settings.injectOnSelectionChange,
        )
        injectOpenFileOnChange = JBCheckBox(
            "Also inject open-file (caret) on every caret move (noisier)",
            settings.injectOpenFileOnChange,
        )

        fun syncChildren() {
            val on = autoInjectSelection.isSelected
            injectOnGrokFocus.isEnabled = on
            injectOnSelectionChange.isEnabled = on
            injectOpenFileOnChange.isEnabled = on && injectOnSelectionChange.isSelected
        }
        autoInjectSelection.addChangeListener { syncChildren() }
        injectOnSelectionChange.addChangeListener { syncChildren() }
        syncChildren()

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Grok CLI path:", cliPath, 1, false)
            .addLabeledComponent("Launch args:", launchArgs, 1, false)
            .addComponent(enableMcp, 1)
            .addComponent(autoRegisterMcp, 1)
            .addLabeledComponent("MCP server name:", mcpServerName, 1, false)
            .addSeparator()
            .addComponent(autoInjectSelection, 1)
            .addComponent(injectOnGrokFocus, 1)
            .addComponent(injectOnSelectionChange, 1)
            .addComponent(injectOpenFileOnChange, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean =
        cliPath.text != settings.cliPath ||
            launchArgs.text != settings.launchArgs ||
            enableMcp.isSelected != settings.enableMcp ||
            autoRegisterMcp.isSelected != settings.autoRegisterMcp ||
            mcpServerName.text != settings.mcpServerName ||
            autoInjectSelection.isSelected != settings.autoInjectSelection ||
            injectOnGrokFocus.isSelected != settings.injectOnGrokFocus ||
            injectOnSelectionChange.isSelected != settings.injectOnSelectionChange ||
            injectOpenFileOnChange.isSelected != settings.injectOpenFileOnChange

    override fun apply() {
        settings.cliPath = cliPath.text.trim()
        settings.launchArgs = launchArgs.text.trim()
        settings.enableMcp = enableMcp.isSelected
        settings.autoRegisterMcp = autoRegisterMcp.isSelected
        settings.mcpServerName = mcpServerName.text.trim().ifEmpty { "jetbrains" }
        settings.autoInjectSelection = autoInjectSelection.isSelected
        settings.injectOnGrokFocus = injectOnGrokFocus.isSelected
        settings.injectOnSelectionChange = injectOnSelectionChange.isSelected
        settings.injectOpenFileOnChange = injectOpenFileOnChange.isSelected
    }

    override fun reset() {
        cliPath.text = settings.cliPath
        launchArgs.text = settings.launchArgs
        enableMcp.isSelected = settings.enableMcp
        autoRegisterMcp.isSelected = settings.autoRegisterMcp
        mcpServerName.text = settings.mcpServerName
        autoInjectSelection.isSelected = settings.autoInjectSelection
        injectOnGrokFocus.isSelected = settings.injectOnGrokFocus
        injectOnSelectionChange.isSelected = settings.injectOnSelectionChange
        injectOpenFileOnChange.isSelected = settings.injectOpenFileOnChange
    }
}
