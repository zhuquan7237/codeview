#!/usr/bin/env bash
# Release CodeView: build the signed APK, mirror it on the relay server and refresh
# the in-app update manifest, then verify what the phone will actually download.
#
#   ./scripts/release.sh 1.2.0 "应用内更新：有新版直接在 App 里下载安装"
#
# Why a mirror: github.com asset downloads are unreliable from mainland China, and the
# in-app updater must work without a VPN. relay.zhuquan.xyz is the Cloudflare-fronted
# mirror on the server; cn.zhuquan.xyz:8443 is the same disk over a direct port.
#
# Env overrides: JAVA_HOME, ANDROID_HOME, RELAY_KEY, RELAY_HOST, RELAY_DIR, SKIP_SSH=1
set -euo pipefail

VERSION="${1:?用法: scripts/release.sh <version> [notes]}"
NOTES="${2:-CodeView $VERSION}"
: "${JAVA_HOME:=C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot}"
: "${ANDROID_HOME:=C:/Users/zhuquan/AppData/Local/Android/Sdk}"
: "${RELAY_KEY:=C:/Users/zhuquan/.ssh/aliyun-light-8.148.233.40.pem}"
: "${RELAY_HOST:=root@8.148.233.40}"
: "${RELAY_DIR:=/opt/dsh-relay/dl}"
export JAVA_HOME ANDROID_HOME

cd "$(dirname "$0")/.."

# The manifest must never claim a version the APK does not have.
DECLARED=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -1)
if [ "$DECLARED" != "$VERSION" ]; then
  echo "✗ app/build.gradle.kts 里是 $DECLARED，脚本要发 $VERSION —— 先改版本号" >&2
  exit 1
fi

echo "▸ 构建 release APK…"
./gradlew.bat :app:assembleRelease --console=plain -q

APK="app/build/outputs/apk/release/app-release.apk"
SIZE=$(stat -c%s "$APK")
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
echo "▸ $APK  $SIZE 字节  sha256=$SHA"

mkdir -p build
# One-line notes: quotes/backslashes escaped, newlines flattened.
SAFE_NOTES=$(printf '%s' "$NOTES" | tr '\n\r' '  ' | sed 's/\\/\\\\/g; s/"/\\"/g')
cat > build/codeview-latest.json <<EOF
{
  "version": "$VERSION",
  "versionCode": $(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts | head -1),
  "apkUrl": "https://relay.zhuquan.xyz/dl/codeview-$VERSION.apk",
  "sha256": "$SHA",
  "size": $SIZE,
  "notes": "$SAFE_NOTES",
  "releasedAt": "$(date +%Y-%m-%d)"
}
EOF
cat build/codeview-latest.json

if [ "${SKIP_SSH:-0}" != "1" ]; then
  echo "▸ 上传到镜像服务器…"
  scp -i "$RELAY_KEY" -o StrictHostKeyChecking=no "$APK" "$RELAY_HOST:$RELAY_DIR/codeview-$VERSION.apk"
  scp -i "$RELAY_KEY" -o StrictHostKeyChecking=no "$APK" "$RELAY_HOST:$RELAY_DIR/codeview.apk"
  scp -i "$RELAY_KEY" -o StrictHostKeyChecking=no build/codeview-latest.json "$RELAY_HOST:$RELAY_DIR/codeview-latest.json"
fi

echo "▸ 校验：手机看到的那份"
curl -s --noproxy '*' "https://relay.zhuquan.xyz/dl/codeview-latest.json"; echo
curl -s --noproxy '*' -o build/relay-check.apk "https://relay.zhuquan.xyz/dl/codeview-$VERSION.apk"
RELAY_SHA=$(sha256sum build/relay-check.apk | cut -d' ' -f1)
if [ "$RELAY_SHA" != "$SHA" ]; then
  echo "✗ 镜像上的 APK 与本地不一致：$RELAY_SHA" >&2
  exit 1
fi
echo "✓ 镜像 sha256 一致"

echo
echo "下一步（GitHub Release + 源码推送）："
echo "  git add -A && git commit -m \"CodeView $VERSION：$NOTES\""
echo "  git push origin main --force-with-lease"
echo "  gh release create v$VERSION \"$APK\" --title \"CodeView $VERSION\" --notes-file release-notes-$VERSION.md"
