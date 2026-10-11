#!/usr/bin/env bash
# 构建通用 maccms 爬虫 jar（TVBox / CatVod 兼容的 DEX 格式）
# 本地构建需要：JDK（含 jar 命令）+ Android build-tools 的 d8 在 PATH 中
# CI（.github/workflows/build-spider.yml）会自动准备这些环境
set -euo pipefail
cd "$(dirname "$0")"

JSOUP_VER="1.17.2"
ANDROID_VER="4.1.1.4"
D8_MIN_API="21"

mkdir -p lib build/classes build/dex ../jar

if [ ! -f lib/jsoup.jar ]; then
  echo "==> 下载 jsoup ${JSOUP_VER}"
  curl -sSL -o lib/jsoup.jar "https://repo1.maven.org/maven2/org/jsoup/jsoup/${JSOUP_VER}/jsoup-${JSOUP_VER}.jar"
fi
if [ ! -f lib/android.jar ]; then
  echo "==> 下载 android.jar（仅编译期 stub，不打进包）"
  curl -sSL -o lib/android.jar "https://repo1.maven.org/maven2/com/google/android/android/${ANDROID_VER}/android-${ANDROID_VER}.jar"
fi

echo "==> javac 编译"
find src -name "*.java" > build/sources.txt
javac -source 8 -target 8 -nowarn -cp "lib/jsoup.jar:lib/android.jar" -d build/classes @build/sources.txt

echo "==> d8 打包 DEX（jsoup 一起打进包）"
D8_BIN="$(command -v d8 || true)"
if [ -z "$D8_BIN" ]; then
  echo "错误：找不到 d8，请先安装 Android build-tools 并加入 PATH（CI 会自动安装）" >&2
  exit 1
fi
"$D8_BIN" --lib lib/android.jar --min-api "$D8_MIN_API" --output build/dex   $(find build/classes -name "*.class") lib/jsoup.jar

echo "==> 打包 jar/maccms-spider.jar"
rm -f ../jar/maccms-spider.jar
jar cf ../jar/maccms-spider.jar -C build/dex classes.dex

echo "==> 完成"
ls -lh ../jar/maccms-spider.jar
unzip -l ../jar/maccms-spider.jar
