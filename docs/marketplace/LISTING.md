# JetBrains Marketplace listing copy

Paste these into the plugin dashboard while under review / after approval.

Plugin page: author dashboard → **Grok Build**

---

## Short name

```
Grok Build
```

## One-line preview (≤ ~200 chars, shown in search)

```
Open Grok Build in the IDE terminal, inject the current selection into your prompt, and share editor context via a local MCP bridge. Requires the grok CLI.
```

## Full description (HTML — also in plugin.xml)

Use the version in `plugin.xml` after the next release, or paste this now under **Overview / Description**:

```html
<blockquote>
  <p><b>Requires</b> the
  <a href="https://docs.x.ai/docs">Grok Build CLI</a>
  installed separately (<code>grok</code> on your PATH, or set the path under
  <b>Settings → Tools → Grok Build</b>).</p>
</blockquote>

<p><b>Grok Build</b> for JetBrains brings the coding agent into WebStorm, IntelliJ IDEA,
PyCharm, GoLand, PhpStorm, and other IDEs — similar in spirit to the Claude Code bridge.</p>

<h2>What you get</h2>
<ul>
  <li><b>Open Grok</b> — <code>Ctrl+Esc</code> (⌃Esc) opens a dedicated Terminal tab and runs <code>grok</code>.</li>
  <li><b>Send selection</b> — <code>Ctrl+Alt+K</code> / <code>⌥⌘K</code> injects the current file,
      line range, and selected code at the front of the Grok prompt. No MCP round-trip needed
      for selection context.</li>
  <li><b>IDE MCP bridge</b> — a local HTTP MCP server exposes open files, diagnostics, reformat,
      and diff tools. Auto-registers as <code>[mcp_servers.jetbrains]</code> in
      <code>~/.grok/config.toml</code>.</li>
  <li><b>Status bar</b> — shows the live MCP port (<code>Grok:12345</code>).</li>
</ul>

<h2>Shortcuts</h2>
<table>
  <tr><td><code>Ctrl+Esc</code></td><td>Open / focus Grok Build terminal</td></tr>
  <tr><td><code>Ctrl+Alt+K</code> / <code>⌥⌘K</code></td><td>Send selection into Grok prompt</td></tr>
</table>

<h2>Requirements</h2>
<ul>
  <li>JetBrains IDE 2024.2+ (WebStorm, IntelliJ, PyCharm, …)</li>
  <li><a href="https://docs.x.ai/docs">Grok Build CLI</a> authenticated on this machine</li>
  <li>Terminal plugin (bundled in WebStorm)</li>
</ul>

<h2>Open source</h2>
<p>Apache-2.0 ·
<a href="https://github.com/nightsumx/grok-jetbrains">github.com/nightsumx/grok-jetbrains</a></p>
<p><i>Not affiliated with xAI. “Grok” and “Grok Build” are marks of their respective owners.</i></p>
```

## Tags (pick all that apply)

```
AI
Code Tools
Productivity
Tools Integration
Editor
```

## Category

```
Tools Integration
```
(or **AI** if available)

## License

```
Apache-2.0
```

## Repository URL

```
https://github.com/nightsumx/grok-jetbrains
```

## Issue tracker

```
https://github.com/nightsumx/grok-jetbrains/issues
```

## Changelog (0.1.0)

```html
<ul>
  <li>Open Grok Build in the IDE terminal (<code>Ctrl+Esc</code>)</li>
  <li>Send selection: inject file path, line range, and selected body into the prompt</li>
  <li>Local IDE MCP server (context, files, diagnostics, reformat, diff)</li>
  <li>Auto-register MCP in <code>~/.grok/config.toml</code></li>
  <li>Status bar widget for MCP port</li>
</ul>
```

## Custom page (optional long-form)

Paste the contents of `CUSTOM_PAGE.md` into **自定义页面 / Custom page**.

## Screenshots to upload

From `docs/marketplace/media/` (1200×760):

| File | Caption (CN / EN) |
|------|-------------------|
| `01-open-grok.png` | 一键打开 Grok · Open Grok in the Terminal |
| `02-send-selection.png` | 发送选区到提示词 · Inject selection into the prompt |
| `03-settings.png` | 设置与 MCP · Settings & MCP bridge |
| `04-features.png` | 功能总览 · Feature overview |

Upload under **媒体 / Media → 插件截图**.
