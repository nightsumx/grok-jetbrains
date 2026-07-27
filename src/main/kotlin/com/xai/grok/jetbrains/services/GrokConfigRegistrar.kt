package com.xai.grok.jetbrains.services

import com.xai.grok.jetbrains.settings.GrokSettings
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Keeps [mcp_servers.<name>] in ~/.grok/config.toml pointed at the live IDE MCP HTTP endpoint.
 * Hot-reload friendly: Grok re-reads MCP config; we only touch our marked block.
 */
object GrokConfigRegistrar {
    private val lock = ReentrantLock()
    private const val BEGIN = "# --- grok-jetbrains-plugin begin ---"
    private const val END = "# --- grok-jetbrains-plugin end ---"

    fun register(port: Int, authToken: String) {
        val settings = GrokSettings.getInstance()
        if (!settings.autoRegisterMcp) return
        val name = settings.mcpServerName.ifBlank { "jetbrains" }
        val block = buildString {
            appendLine(BEGIN)
            appendLine("[mcp_servers.$name]")
            appendLine("url = \"http://127.0.0.1:$port/mcp\"")
            appendLine("headers = { Authorization = \"Bearer $authToken\" }")
            appendLine("enabled = true")
            appendLine(END)
        }
        writeBlock(block)
    }

    fun unregister() {
        val settings = GrokSettings.getInstance()
        if (!settings.autoRegisterMcp) return
        writeBlock(null)
    }

    private fun writeBlock(block: String?) {
        lock.withLock {
            val file = configFile()
            file.parentFile?.mkdirs()
            val existing = if (file.exists()) file.readText() else ""
            val stripped = stripBlock(existing).trimEnd()
            val next = when {
                block == null && stripped.isEmpty() -> ""
                block == null -> stripped + "\n"
                stripped.isEmpty() -> block
                else -> stripped + "\n\n" + block
            }
            if (next != existing) {
                file.writeText(if (next.endsWith("\n") || next.isEmpty()) next else next + "\n")
            }
        }
    }

    private fun stripBlock(text: String): String {
        val begin = text.indexOf(BEGIN)
        if (begin < 0) return text
        val end = text.indexOf(END, begin)
        if (end < 0) return text
        val after = end + END.length
        val before = text.substring(0, begin).trimEnd()
        val rest = if (after < text.length) text.substring(after).trimStart() else ""
        return listOf(before, rest).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun configFile(): File =
        File(System.getProperty("user.home"), ".grok/config.toml")
}
