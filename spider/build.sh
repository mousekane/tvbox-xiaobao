#!/usr/bin/env bash
# 构建通用 maccms 爬虫 jar（TVBox / CatVod 兼容的 DEX 格式）
# 本地构建需要：JDK（含 javac / jar 命令）。d8 会自动下载，无需 Android SDK。
# CI（.github/workflows/build-spider.yml）会自动准备 JDK 并运行此脚本。
set -euo pipefail
cd "$(dirname "$0")"

JSOUP_VER="1.17.2"
ANDROID_VER="4.1.1.4"
D8_MIN_API="21"
BT_URL="https://dl.google.com/android/repository/build-tools_r34-linux.zip"

mkdir -p lib build/classes build/dex ../jar

if [ ! -f lib/jsoup.jar ]; then
  echo "==> 下载 jsoup ${JSOUP_VER}"
  curl -sSL --fail -o lib/jsoup.jar "https://repo1.maven.org/maven2/org/jsoup/jsoup/${JSOUP_VER}/jsoup-${JSOUP_VER}.jar"
fi
if [ ! -f lib/android.jar ]; then
  echo "==> 下载 android.jar（仅编译期 stub，不打进包）"
  curl -sSL --fail -o lib/android.jar "https://repo1.maven.org/maven2/com/google/android/android/${ANDROID_VER}/android-${ANDROID_VER}.jar"
fi

echo "==> javac 编译"
find src -name "*.java" > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "lib/jsoup.jar:lib/android.jar" -d build/classes @build/sources.txt

echo "==> 定位 d8"
D8_BIN=""
if command -v d8 >/dev/null 2>&1; then
  D8_BIN="$(command -v d8)"
else
  BT_DIR="build/android-bt"
  D8_BIN="$BT_DIR/android-14/d8"
  if [ ! -x "$D8_BIN" ]; then
    echo "==> 下载 Android build-tools（提供 d8，一次性约 60MB）"
    mkdir -p "$BT_DIR"
    curl -sSL --fail -o "$BT_DIR/bt.zip" "$BT_URL"
    unzip -q -o "$BT_DIR/bt.zip" -d "$BT_DIR"
    D8_BIN="$(find "$BT_DIR" -name d8 -type f | head -1)"
  fi
fi
if [ -z "$D8_BIN" ] || [ ! -x "$D8_BIN" ]; then
  echo "错误：找不到可用的 d8" >&2
  exit 1
fi
echo "使用 d8：$D8_BIN"

echo "==> d8 打包 DEX（jsoup 一起打进包）"
# shellcheck disable=SC2046
"$D8_BIN" --lib lib/android.jar --min-api "$D8_MIN_API" --output build/dex \
  $(find build/classes -name "*.class") lib/jsoup.jar

echo "==> 打包 jar/maccms-spider.jar"
rm -f ../jar/maccms-spider.jar
jar cf ../jar/maccms-spider.jar -C build/dex classes.dex

echo "==> 完成"
ls -lh ../jar/maccms-spider.jar
unzip -l ../jar/maccms-spider.jar
