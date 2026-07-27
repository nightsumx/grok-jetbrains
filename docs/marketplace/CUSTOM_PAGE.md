# Grok Build for JetBrains

Bring the **Grok Build** coding agent into WebStorm, IntelliJ IDEA, PyCharm, and other JetBrains IDEs.

## Why this plugin?

Grok Build is a powerful terminal coding agent. This plugin closes the gap between the agent and your editor:

1. **Launch without leaving the IDE** — one shortcut opens a dedicated Terminal tab running `grok`.
2. **Selection-aware prompts** — send the current selection as a structured block (path, lines, body) so the model sees exactly what you mean.
3. **Optional MCP bridge** — Grok can open files, read diagnostics, reformat, and show diffs in the IDE.

## Quick start

1. Install the [Grok Build CLI](https://docs.x.ai/docs) and run `grok login`.
2. Install this plugin, restart the IDE if asked.
3. Press **Ctrl+Esc** (⌃Esc) → Grok opens in the Terminal.
4. Select code → **⌥⌘K** / **Ctrl+Alt+K** → selection is injected into the prompt.
5. Type your question and press Enter.

Look for **Grok:&lt;port&gt;** in the status bar — that means the IDE MCP bridge is live.

## How selection inject works

Unlike tools that force the model to call MCP just to learn what you selected, **Send to Grok** pastes a clear block at the front of your message:

```
[IDE selection]
File: src/app.ts
Lines: 42-68

// your selected code
```

You keep typing after the block. The model already has full context.

## MCP tools (for advanced use)

When the bridge is enabled (default), Grok can call:

- `get_editor_context` / `get_selection` / `get_open_files`
- `open_file` / `close_tab` / `reformat_file`
- `get_diagnostics` / `open_diff` / `apply_text`

The plugin writes a marked section into `~/.grok/config.toml` so Grok discovers the server automatically.

## Settings

**Settings → Tools → Grok Build**

- CLI path and launch args
- Enable / disable MCP
- Auto-register MCP in config

## Source & license

- Source: [github.com/nightsumx/grok-jetbrains](https://github.com/nightsumx/grok-jetbrains)
- License: Apache-2.0

Not affiliated with xAI.
