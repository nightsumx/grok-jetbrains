# Grok Build for JetBrains

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Open-source WebStorm / IntelliJ / PyCharm / GoLand / … plugin that wires **[Grok Build](https://docs.x.ai)** into the IDE the way Claude Code’s JetBrains bridge does:

| Feature | Shortcut | What it does |
|--------|----------|--------------|
| **Open Grok** | `Ctrl+Esc` (⌃Esc) | Opens a **Grok Build** tab in the built-in Terminal and runs `grok` |
| **Send selection** | `Ctrl+Alt+K` / `⌥⌘K` | Injects file path + line range + **selected body** at the front of the Grok prompt (no MCP needed) |
| **Editor MCP** | (auto) | Local HTTP MCP: selection, open files, diagnostics, open/close tabs, reformat, diff |

> **Requires** the [Grok Build CLI](https://docs.x.ai) (`grok` on `PATH`, or set it under **Settings → Tools → Grok Build**).

## Install

### From disk (today)

```bash
./gradlew buildPlugin --offline   # or without --offline
# → build/distributions/grok-jetbrains-0.1.0.zip
```

IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip.

Or one-shot for local WebStorm:

```bash
./install-local.sh
```

### Marketplace

Upload / search **Grok Build** once the JetBrains review lands  
(first upload is manual at https://plugins.jetbrains.com/plugin/add).

## How it works

```
┌─────────────────── WebStorm ───────────────────┐
│  Editors / selection / diagnostics              │
│         ▲                                       │
│         │ MCP tools (optional pull)             │
│  IdeBridgeService  ──HTTP──► 127.0.0.1:{port}/mcp│
│         │                                       │
│  Terminal: grok   ◄── [IDE selection] paste     │
└─────────┬───────────────────────────────────────┘
          │ reads ~/.grok/config.toml
          ▼
     Grok Build CLI
```

On project open the plugin:

1. Starts a loopback MCP HTTP server  
2. Writes `~/.grok/ide/{port}.lock` (+ `active.json`)  
3. Upserts a marked block in `~/.grok/config.toml` for `[mcp_servers.jetbrains]`

### Full-auto selection inject

Selection is tracked continuously (Claude-style). When Grok is open — or when you open it — the plugin pastes:

```text
[IDE selection]
File: src/foo.ts
Absolute: /Users/…/src/foo.ts
Lines: 10-42

```
// selected code
```

```

via **bracketed paste** (never auto-submits). Also written to `~/.grok/ide/last-selection.md` + `selection.json`.

Default: auto on selection change while Grok is open, and on Grok open/focus. Hotkey `⌥⌘K` remains for manual re-send. Toggle under **Settings → Tools → Grok Build**.

## MCP tools

| Tool | Purpose |
|------|---------|
| `get_editor_context` | Workspace, active file, selection, open tabs |
| `get_selection` | Selection + Grok `@mention` string |
| `get_open_files` | Open editor tabs |
| `open_file` | Open path (optional 1-based line) |
| `close_tab` | Close tab by path |
| `reformat_file` | IDE reformat |
| `get_diagnostics` | Squiggles for open files (or one path) |
| `open_diff` | Proposed text vs disk in Diff |
| `apply_text` | Write full file content through VFS |

## Settings

**Settings → Tools → Grok Build**

| Setting | Default |
|---------|---------|
| Grok CLI path | `grok` on PATH |
| Launch args | (empty) |
| Enable IDE MCP server | on |
| Auto-register MCP in `~/.grok/config.toml` | on |
| MCP server name | `jetbrains` |

## Develop

Requires **JDK 21**.

```bash
./gradlew runIde          # sandbox IDE
./gradlew buildPlugin     # zip
./publish.sh --zip-only   # signed zip for Marketplace
```

Signing certs live in `signing/` (gitignored). First Marketplace publish must be a **manual** upload of the signed zip; later versions:

```bash
export PUBLISH_TOKEN=…    # or .marketplace-token
./publish.sh
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Not affiliated with xAI. “Grok” and “Grok Build” are marks of their respective owners.
