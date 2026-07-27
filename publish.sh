#!/usr/bin/env bash
# Sign + publish Grok Build to JetBrains Marketplace (or build signed zip only).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

load_token() {
  if [[ -n "${PUBLISH_TOKEN:-}" ]]; then return 0; fi
  if [[ -n "${JETBRAINS_MARKETPLACE_TOKEN:-}" ]]; then
    export PUBLISH_TOKEN="$JETBRAINS_MARKETPLACE_TOKEN"
    return 0
  fi
  if [[ -f "$ROOT/.marketplace-token" ]]; then
    export PUBLISH_TOKEN="$(tr -d '[:space:]' < "$ROOT/.marketplace-token")"
    return 0
  fi
  if [[ -f "$HOME/.grok/jetbrains-marketplace.token" ]]; then
    export PUBLISH_TOKEN="$(tr -d '[:space:]' < "$HOME/.grok/jetbrains-marketplace.token")"
    return 0
  fi
  return 1
}

echo "→ ensure signing certs"
if [[ ! -f signing/chain.crt || ! -f signing/private.pem ]]; then
  mkdir -p signing
  openssl genrsa -out signing/private.pem 4096
  openssl req -new -x509 \
    -key signing/private.pem \
    -out signing/chain.crt \
    -days 3650 \
    -subj "/CN=Grok Build Plugin/O=sum/C=CN"
  chmod 600 signing/private.pem
fi

export CERTIFICATE_CHAIN="$(cat signing/chain.crt)"
export PRIVATE_KEY="$(cat signing/private.pem)"
export PRIVATE_KEY_PASSWORD="${PRIVATE_KEY_PASSWORD:-}"

echo "→ signPlugin + buildPlugin"
if ./gradlew signPlugin buildPlugin --offline 2>/dev/null; then
  :
else
  ./gradlew signPlugin buildPlugin
fi

ZIP=$(ls -1 "$ROOT"/build/distributions/grok-jetbrains-*.zip | tail -1)
echo "→ signed artifact: $ZIP"
ls -la "$ZIP"

if [[ "${1:-}" == "--zip-only" ]]; then
  echo "zip-only mode; not uploading."
  open "$(dirname "$ZIP")" 2>/dev/null || true
  exit 0
fi

if ! load_token; then
  cat <<'EOF'

════════════════════════════════════════════════════════════════
 Marketplace 发布还差一步：Permanent Token

 1. 浏览器打开（你应已登录 JetBrains 账号）:
    https://plugins.jetbrains.com/author/me
    或  https://plugins.jetbrains.com/organization/edit

 2. 找到 "API Tokens" / "Permanent Tokens" → Generate

 3. 把 token 写到本仓库（已 gitignore）:
      echo '你的token' > /Users/sum/Documents/grok-jetbrains/.marketplace-token

 4. 再跑:
      ./publish.sh

 也可以网页手动上传 zip（同样需要登录）:
    https://plugins.jetbrains.com/plugin/upload
════════════════════════════════════════════════════════════════
EOF
  open "https://plugins.jetbrains.com/author/me" 2>/dev/null || true
  open "https://plugins.jetbrains.com/plugin/upload" 2>/dev/null || true
  open "$(dirname "$ZIP")" 2>/dev/null || true
  exit 2
fi

echo "→ publishPlugin (Marketplace)"
if ! ./gradlew publishPlugin; then
  cat <<'EOF'

  若报 "Cannot find plugin"：说明这是**第一次**上架。
  JetBrains 要求首次必须网页上传（选 license / 标签）：

    1. 打开 https://plugins.jetbrains.com/plugin/add
    2. 选 build/distributions/*-signed.zip
    3. License / tags 填好 → Submit
    4. 审核通过后，以后版本直接 ./publish.sh 即可
EOF
  open "https://plugins.jetbrains.com/plugin/add" 2>/dev/null || true
  open "$ROOT/build/distributions" 2>/dev/null || true
  exit 1
fi
echo "✓ submitted. JetBrains will review (often days)."
echo "  Check: https://plugins.jetbrains.com/author/me"
