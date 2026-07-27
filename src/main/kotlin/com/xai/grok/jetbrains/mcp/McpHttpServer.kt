package com.xai.grok.jetbrains.mcp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Minimal MCP Streamable-HTTP server for Grok.
 *
 * Supports JSON-RPC methods: initialize, tools/list, tools/call, ping.
 * Auth: Authorization: Bearer <token>
 */
class McpHttpServer(
    private val projectResolver: () -> Project?,
) {
    private val log = Logger.getInstance(McpHttpServer::class.java)
    private var server: HttpServer? = null
    private val started = AtomicBoolean(false)
    private val authToken = AtomicReference<String?>(null)
    var port: Int = 0
        private set

    fun start(token: String, preferredPort: Int = 0): Int {
        if (!started.compareAndSet(false, true)) return port
        authToken.set(token)
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", preferredPort), 0)
        http.executor = Executors.newCachedThreadPool { r ->
            Thread(r, "grok-mcp").apply { isDaemon = true }
        }
        http.createContext("/health") { ex ->
            respond(ex, 200, """{"ok":true,"port":$port}""")
        }
        http.createContext("/mcp") { ex -> handleMcp(ex) }
        http.createContext("/") { ex ->
            if (ex.requestURI.path == "/" || ex.requestURI.path.isEmpty()) {
                respond(ex, 200, """{"name":"grok-jetbrains","mcp":"/mcp"}""")
            } else {
                respond(ex, 404, """{"error":"not found"}""")
            }
        }
        http.start()
        server = http
        port = http.address.port
        log.info("Grok IDE MCP listening on 127.0.0.1:$port")
        return port
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        try {
            server?.stop(0)
        } catch (_: Exception) {
        }
        server = null
        port = 0
        authToken.set(null)
    }

    private fun toolsFor(project: Project): IdeTools = IdeTools(project)

    private fun handleMcp(ex: HttpExchange) {
        try {
            if (!authorize(ex)) {
                respond(ex, 401, """{"error":"unauthorized"}""")
                return
            }
            when (ex.requestMethod.uppercase()) {
                "GET" -> respond(
                    ex,
                    200,
                    """{"jsonrpc":"2.0","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"grok-jetbrains","version":"0.1.0"}}}""",
                )
                "POST" -> {
                    val body = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                    val response = handleJsonRpc(body)
                    if (response.isEmpty()) {
                        // notification ack
                        ex.sendResponseHeaders(202, -1)
                        ex.close()
                    } else {
                        respond(ex, 200, response, contentType = "application/json")
                    }
                }
                "OPTIONS" -> {
                    ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
                    ex.responseHeaders.add(
                        "Access-Control-Allow-Headers",
                        "Authorization, Content-Type, Mcp-Session-Id, Accept",
                    )
                    ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    ex.sendResponseHeaders(204, -1)
                    ex.close()
                }
                else -> respond(ex, 405, """{"error":"method not allowed"}""")
            }
        } catch (t: Throwable) {
            log.warn("MCP request failed", t)
            respond(
                ex,
                500,
                """{"jsonrpc":"2.0","error":{"code":-32603,"message":"${jsonEscape(t.message ?: "error")}"}}""",
            )
        }
    }

    private fun authorize(ex: HttpExchange): Boolean {
        val expected = authToken.get() ?: return false
        val header = ex.requestHeaders.getFirst("Authorization") ?: return false
        return header.trim() == "Bearer $expected"
    }

    private fun handleJsonRpc(body: String): String {
        val method = extractString(body, "method") ?: return rpcError(null, -32600, "missing method")
        val id = extractId(body)
        return when (method) {
            "initialize" -> rpcResult(
                id,
                """{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"grok-jetbrains","version":"0.1.0"}}""",
            )
            "notifications/initialized", "initialized" -> ""
            "ping" -> rpcResult(id, """{}""")
            "tools/list" -> rpcResult(id, """{"tools":${toolDefinitions()}}""")
            "tools/call" -> {
                val name = extractNestedString(body, "params", "name")
                    ?: extractString(body, "name")
                    ?: return rpcError(id, -32602, "missing tool name")
                val paramsObj = extractObject(body, "params") ?: body
                val argsJson = extractObject(paramsObj, "arguments") ?: "{}"
                try {
                    val project = projectResolver()
                        ?: return rpcResult(
                            id,
                            """{"content":[{"type":"text","text":"No open project"}],"isError":true}""",
                        )
                    val result = callTool(toolsFor(project), name, argsJson)
                    rpcResult(
                        id,
                        """{"content":[{"type":"text","text":${jsonString(result)}}],"isError":false}""",
                    )
                } catch (t: Throwable) {
                    log.warn("tool $name failed", t)
                    rpcResult(
                        id,
                        """{"content":[{"type":"text","text":${jsonString(t.message ?: "error")}}],"isError":true}""",
                    )
                }
            }
            else -> rpcError(id, -32601, "Method not found: $method")
        }
    }

    private fun callTool(tools: IdeTools, name: String, args: String): String {
        val map = when (name) {
            "get_editor_context" -> tools.getEditorContext()
            "get_selection" -> tools.getSelection()
            "get_open_files" -> tools.getOpenFiles()
            "open_file" -> tools.openFile(
                path = requireString(args, "path"),
                line = extractInt(args, "line"),
                preview = extractBool(args, "preview") ?: false,
            )
            "close_tab" -> tools.closeTab(requireString(args, "path"))
            "reformat_file" -> tools.reformatFile(requireString(args, "path"))
            "get_diagnostics" -> tools.getDiagnostics(
                extractString(args, "uri") ?: extractString(args, "path"),
            )
            "open_diff" -> tools.openDiff(
                path = requireString(args, "path"),
                newText = requireString(args, "newText"),
                tabName = extractString(args, "tabName"),
            )
            "apply_text" -> tools.applyText(
                path = requireString(args, "path"),
                content = requireString(args, "content"),
            )
            else -> mapOf("error" to "Unknown tool: $name")
        }
        return toJson(map)
    }

    private fun toolDefinitions(): String = """
        [
          {"name":"get_editor_context","description":"Current IDE workspace, active file, selection, and open editors.","inputSchema":{"type":"object","properties":{}}},
          {"name":"get_selection","description":"Current text selection (path, range, text, Grok @mention).","inputSchema":{"type":"object","properties":{}}},
          {"name":"get_open_files","description":"List files open in the IDE editor tabs.","inputSchema":{"type":"object","properties":{}}},
          {"name":"open_file","description":"Open a file in the IDE. Optional 1-based line.","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"line":{"type":"integer"},"preview":{"type":"boolean"}},"required":["path"]}},
          {"name":"close_tab","description":"Close an editor tab by absolute path.","inputSchema":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}},
          {"name":"reformat_file","description":"Reformat a file with the IDE code style.","inputSchema":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}},
          {"name":"get_diagnostics","description":"IDE diagnostics (errors/warnings). Optional path limits to one file; otherwise open files.","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"uri":{"type":"string"}}}},
          {"name":"open_diff","description":"Show a proposed edit in the IDE diff viewer (left=disk, right=newText).","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"newText":{"type":"string"},"tabName":{"type":"string"}},"required":["path","newText"]}},
          {"name":"apply_text","description":"Write full file content to disk via the IDE VFS.","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}}
        ]
    """.trimIndent().replace('\n', ' ')

    private fun respond(
        ex: HttpExchange,
        code: Int,
        body: String,
        contentType: String = "application/json",
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", contentType)
        ex.responseHeaders.set("Access-Control-Allow-Origin", "*")
        try {
            if (body.isEmpty()) {
                ex.sendResponseHeaders(if (code == 200) 202 else code, -1)
            } else {
                ex.sendResponseHeaders(code, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        } catch (_: IOException) {
        } finally {
            ex.close()
        }
    }

    private fun rpcResult(id: Any?, resultJson: String): String {
        val idPart = when (id) {
            null -> "null"
            is Number -> id.toString()
            else -> "\"${jsonEscape(id.toString())}\""
        }
        return """{"jsonrpc":"2.0","id":$idPart,"result":$resultJson}"""
    }

    private fun rpcError(id: Any?, code: Int, message: String): String {
        val idPart = when (id) {
            null -> "null"
            is Number -> id.toString()
            else -> "\"${jsonEscape(id.toString())}\""
        }
        return """{"jsonrpc":"2.0","id":$idPart,"error":{"code":$code,"message":"${jsonEscape(message)}"}}"""
    }

    private fun jsonString(s: String): String = "\"${jsonEscape(s)}\""

    private fun toJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is String -> jsonString(value)
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            "${jsonString(k.toString())}:${toJson(v)}"
        }
        is Collection<*> -> value.joinToString(",", "[", "]") { toJson(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { toJson(it) }
        else -> jsonString(value.toString())
    }

    private fun extractId(json: String): Any? {
        val key = "\"id\""
        val i = json.indexOf(key)
        if (i < 0) return null
        var j = i + key.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':')) j++
        if (j >= json.length) return null
        return when (json[j]) {
            '"' -> extractString(json, "id")
            in '0'..'9', '-' -> {
                val start = j
                while (j < json.length && (json[j].isDigit() || json[j] == '-' || json[j] == '.')) j++
                json.substring(start, j).toLongOrNull() ?: json.substring(start, j)
            }
            else -> null
        }
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = "\"$key\""
        val i = json.indexOf(pattern)
        if (i < 0) return null
        var j = i + pattern.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':' || json[j] == '\n' || json[j] == '\r')) j++
        if (j >= json.length || json[j] != '"') return null
        j++
        val sb = StringBuilder()
        while (j < json.length) {
            val c = json[j]
            when {
                c == '\\' && j + 1 < json.length -> {
                    val n = json[j + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'u' -> {
                            if (j + 5 < json.length) {
                                val hex = json.substring(j + 2, j + 6)
                                sb.append(hex.toInt(16).toChar())
                                j += 4
                            }
                        }
                        else -> sb.append(n)
                    }
                    j += 2
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    j++
                }
            }
        }
        return null
    }

    private fun extractNestedString(json: String, outer: String, inner: String): String? {
        val obj = extractObject(json, outer) ?: return null
        return extractString(obj, inner)
    }

    private fun extractObject(json: String, key: String): String? {
        val pattern = "\"$key\""
        val i = json.indexOf(pattern)
        if (i < 0) return null
        var j = i + pattern.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':' || json[j] == '\n' || json[j] == '\r')) j++
        if (j >= json.length || json[j] != '{') return null
        var depth = 0
        val start = j
        while (j < json.length) {
            val c = json[j]
            if (c == '"') {
                j++
                while (j < json.length) {
                    if (json[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (json[j] == '"') break
                    j++
                }
            } else if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return json.substring(start, j + 1)
            }
            j++
        }
        return null
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = "\"$key\""
        val i = json.indexOf(pattern)
        if (i < 0) return null
        var j = i + pattern.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':')) j++
        val start = j
        if (j < json.length && json[j] == '-') j++
        while (j < json.length && json[j].isDigit()) j++
        if (start == j || (j == start + 1 && json[start] == '-')) return null
        return json.substring(start, j).toIntOrNull()
    }

    private fun extractBool(json: String, key: String): Boolean? {
        val pattern = "\"$key\""
        val i = json.indexOf(pattern)
        if (i < 0) return null
        var j = i + pattern.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':')) j++
        return when {
            json.startsWith("true", j) -> true
            json.startsWith("false", j) -> false
            else -> null
        }
    }

    private fun requireString(json: String, key: String): String =
        extractString(json, key) ?: throw IllegalArgumentException("missing $key")
}
