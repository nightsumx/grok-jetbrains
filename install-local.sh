#!/usr/bin/env bash
# Build + sideload into the latest local WebStorm plugins directory.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "→ building plugin (offline if network to JetBrains is flaky)"
if ./gradlew buildPlugin --offline; then
  :
else
  echo "offline build failed, trying online…"
  ./gradlew buildPlugin
fi

ZIP=$(ls -1 "$ROOT"/build/distributions/grok-jetbrains-*.zip | tail -1)
echo "→ artifact: $ZIP"

# Prefer newest WebStorm* under Application Support/JetBrains
JB_SUPPORT="${HOME}/Library/Application Support/JetBrains"
TARGET=""
if [[ -d "$JB_SUPPORT" ]]; then
  # shellcheck disable=SC2012
  TARGET=$(ls -1d "$JB_SUPPORT"/WebStorm* 2>/dev/null | sort -V | tail -1 || true)
fi
if [[ -z "${TARGET}" ]]; then
  echo "No WebStorm config dir found under $JB_SUPPORT"
  echo "Install manually: Settings → Plugins → ⚙ → Install Plugin from Disk… → $ZIP"
  exit 0
fi

PLUGINS="$TARGET/plugins"
mkdir -p "$PLUGINS"
TMP=$(mktemp -d)
unzip -q -o "$ZIP" -d "$TMP"
NAME=$(ls "$TMP" | head -1)
rm -rf "$PLUGINS/$NAME"
cp -R "$TMP/$NAME" "$PLUGINS/"
rm -rf "$TMP"

echo "→ installed to $PLUGINS/$NAME"
echo "→ restart WebStorm (or disable/enable the plugin) to load it."
echo "   Then: Ctrl+Esc open Grok · ⌥⌘K / Ctrl+Alt+K send selection."
