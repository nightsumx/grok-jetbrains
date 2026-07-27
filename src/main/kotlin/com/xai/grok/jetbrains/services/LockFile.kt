package com.xai.grok.jetbrains.services

import com.intellij.openapi.project.Project
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * Claude-style lockfile so local tools can discover the IDE bridge.
 * Path: ~/.grok/ide/{port}.lock
 */
object LockFile {
    private val random = SecureRandom()

    fun ideDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".grok/ide").also { it.mkdirs() }
    }

    fun generateAuthToken(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun write(port: Int, project: Project, authToken: String, pid: Long = ProcessHandle.current().pid()) {
        val base = project.basePath ?: return
        val content = buildString {
            append('{')
            append("\"workspaceFolders\":[\"")
            append(escape(base))
            append("\"],")
            append("\"pid\":").append(pid).append(',')
            append("\"ideName\":\"").append(escape(ideName())).append("\",")
            append("\"transport\":\"http\",")
            append("\"runningInWindows\":").append(isWindows()).append(',')
            append("\"authToken\":\"").append(escape(authToken)).append('"')
            append('}')
        }
        File(ideDir(), "$port.lock").writeText(content)
        File(ideDir(), "active.json").writeText(
            """{"port":$port,"authToken":"${escape(authToken)}","workspace":"${escape(base)}"}""",
        )
    }

    fun delete(port: Int) {
        File(ideDir(), "$port.lock").delete()
        val active = File(ideDir(), "active.json")
        if (active.exists()) {
            val text = active.readText()
            if (text.contains("\"port\":$port")) active.delete()
        }
    }

    private fun ideName(): String {
        val name = com.intellij.openapi.application.ApplicationInfo.getInstance().fullApplicationName
        return when {
            name.contains("WebStorm", true) -> "WebStorm"
            name.contains("IntelliJ", true) -> "IntelliJ IDEA"
            name.contains("PyCharm", true) -> "PyCharm"
            name.contains("GoLand", true) -> "GoLand"
            name.contains("PhpStorm", true) -> "PhpStorm"
            name.contains("CLion", true) -> "CLion"
            name.contains("Rider", true) -> "Rider"
            name.contains("DataGrip", true) -> "DataGrip"
            name.contains("RubyMine", true) -> "RubyMine"
            name.contains("Android Studio", true) -> "Android Studio"
            else -> name.substringBefore(" ").ifEmpty { "JetBrains" }
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
