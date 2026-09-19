#!/usr/bin/env bash
# ============================================================================
# 本地构建环境自检 / 引导
#
# 为什么需要它（2026-09-19，M1-5）
# --------------------------------
# 本仓的既定做法是「沙箱/Agent 环境没有 JDK 与 Android SDK ⇒ CI 是唯一的编译裁判」
# （CLAUDE.md §4.4、docs/WORKFLOW.md §3）。过去一年多里它是**事实陈述**，
# 但它同时在悄悄决定架构走向：没有本地编译器，就没人敢做"改动量大而结构更对"的重构
# （#10c 每屏一 VM、DataStore→Room、per-screen VM + Factory），因为一次编译失败要烧掉
# 一整轮 CI（约 4 分钟，concurrency 还会取消同分支中间的 run），错误信息得从 actions
# 日志里人肉捞。于是"改到能编译"退化成"改到能对上文档里的守卫数字"。
#
# 这个脚本不承诺"一键装好一切"，只把三件事拆开、各自可单独跑：
#   1. --check        只读自检：说清楚缺什么，非零退出码 = 这台机器编不了（不改任何东西）
#   2. --install-sdk  能自动装的就装：command-line tools + 许可 + platform（build-tools 交给 AGP）
#   3. --verify       装完立刻用真构建验证，并把结果如实报出来（不给假的绿）
#   --bootstrap     = 2 + 3（--check 的结果由 3 自己体现）
#
# Agent 沙箱里通常连 JDK 都没有、外网还被墙 —— 那时 --check 会明确告诉你"只能靠 CI"，
# 这比留一句"沙箱编不了"的口头约定有用：判据、修法、以及"缺哪个组件"都当场给出来。
#
# 退出码：0 = 就绪 / 指定动作全部成功；1 = 缺东西且没法自动补；2 = 参数错。
# ============================================================================
set -uo pipefail

# ⚠️ 下面三个常量的**唯一事实源在别处**，改那边必须改这里，否则会出现"本地能编、CI 红"：
#   REQUIRED_JDK   ← app/build.gradle.kts 的 jvmToolchain(21) 与 .github/workflows/build.yml 的 setup-java
#   ANDROID_PLATFORM ← gradle/libs.versions.toml 的 compileSdk
#   CMDLINE_TOOLS_BUILD ← https://developer.android.com/studio#command-line-tools-only
REQUIRED_JDK=21
ANDROID_PLATFORM="android-36"
CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-13114758}" # 404 就说明有新版，去上面那页抄个号

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_ROOT" ]; then
  case "$(uname -s)" in
    Darwin) SDK_ROOT="$HOME/Library/Android/sdk" ;;
    *)      SDK_ROOT="$HOME/Android/Sdk" ;;
  esac
fi

PASS=0
WARN=0
FAIL=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
note() { printf '  \033[33m!\033[0m %s\n' "$1"; WARN=$((WARN + 1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
hdr()  { printf '\n\033[1m%s\033[0m\n' "$1"; }
hint() { printf '    %s\n' "$1"; }

# PATH 上的 java 或 JAVA_HOME/bin/java 的主版本号；拿不到就输出空串。
# 刻意以"能不能真跑出 version"为准：macOS 未装 JDK 时 /usr/bin/java 是个会弹安装页的 stub。
jdk_major() {
  local bin="java"
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    bin="$JAVA_HOME/bin/java"
  fi
  command -v "$bin" >/dev/null 2>&1 || return 0
  local v
  v="$("$bin" -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')"
  case "$v" in
    1.*) printf '%s' "$v" | cut -d. -f2 ;; # 老式 1.8.0_412 ⇒ 8
    *)   printf '%s' "$v" | cut -d. -f1 ;;
  esac
}

check_env() {
  hdr "1/3 JDK（本仓要求 $REQUIRED_JDK）"
  local major
  major="$(jdk_major)"
  if [ -z "$major" ]; then
    bad "没找到可用的 java"
    hint "macOS: brew install --cask temurin@${REQUIRED_JDK}"
    hint "Debian/Ubuntu: sudo apt install openjdk-${REQUIRED_JDK}-jdk"
  elif [ "$major" -lt "$REQUIRED_JDK" ]; then
    note "java 是 $major，低于 $REQUIRED_JDK"
    hint "jvmToolchain($REQUIRED_JDK) 会让 Gradle 试着自动下工具链，但 Kotlin DSL 与 AGP 9 的插件"
    hint "类路径本身就吃 JDK 17+；把 JAVA_HOME 指到 $REQUIRED_JDK 最省事。"
  else
    ok "java $major（JAVA_HOME=${JAVA_HOME:-未设，用 PATH 上的 java}）"
  fi

  hdr "2/3 Android SDK（$SDK_ROOT）"
  if [ ! -d "$SDK_ROOT" ]; then
    bad "SDK 目录不存在"
    hint "装 Android Studio（自带 SDK），或 bash tools/bootstrap-build-env.sh --install-sdk 只装命令行那套"
  else
    ok "SDK 目录在位"
    if [ -f "$SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar" ]; then
      ok "platforms/$ANDROID_PLATFORM"
    else
      bad "缺 platforms/$ANDROID_PLATFORM（compileSdk 要求）"
      hint "sdkmanager \"platforms;$ANDROID_PLATFORM\""
    fi
    local n
    n="$(find "$SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l | tr -d '[:space:]')"
    if [ "${n:-0}" = "0" ]; then
      bad "缺 build-tools"
      hint "sdkmanager \"build-tools;<本机可用的最新>\"（AGP 会拒绝它要求之外的版本）"
    else
      ok "build-tools $n 个版本"
    fi
    if [ -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
      ok "sdkmanager 可用（缺的组件可以自动补）"
    else
      note "没有 cmdline-tools/latest/bin/sdkmanager ⇒ --install-sdk 会装它"
    fi
    if [ -n "$(find "$SDK_ROOT/licenses" -maxdepth 1 -type f 2>/dev/null | head -1)" ]; then
      ok "许可已接受"
    else
      bad "许可未接受（AGP 会直接失败）"
      hint "yes | \"$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager\" --licenses"
    fi
  fi

  hdr "3/3 仓库侧"
  if [ -f "$REPO_ROOT/local.properties" ]; then
    if grep -q '^sdk\.dir=' "$REPO_ROOT/local.properties"; then
      ok "local.properties 里有 sdk.dir"
    else
      note "local.properties 存在但没有 sdk.dir ⇒ AGP 退回 ANDROID_HOME/ANDROID_SDK_ROOT"
    fi
  else
    note "没有 local.properties（纯命令行靠环境变量也能过；本仓 .gitignore 已忽略它，别提交）"
  fi
  if [ -f "$REPO_ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
    ok "gradle wrapper jar 在位（不需要系统装 Gradle）"
  else
    bad "缺 gradle/wrapper/gradle-wrapper.jar ⇒ ./gradlew 跑不起来"
    hint "用同版本 Gradle 重新生成：gradle wrapper（版本见 gradle/wrapper/gradle-wrapper.properties），别手抄 jar"
  fi
  if grep -q 'kotlin.compiler.execution.strategy=in-process' "$REPO_ROOT/gradle.properties" 2>/dev/null; then
    note "gradle.properties 设了 kotlin.compiler.execution.strategy=in-process：官方**不推荐**"
    hint "（daemon 参数被忽略、复用性变差）。本脚本只提示不改：动它会让全部增量构建重编，属独立一轮。"
  fi
}

install_sdk() {
  hdr "装 Android SDK 命令行工具到 $SDK_ROOT"
  if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
    bad "需要 curl 与 unzip（macOS: brew install curl unzip）"
    return 1
  fi
  local os
  case "$(uname -s)" in
    Darwin) os="mac" ;;
    Linux)  os="linux" ;;
    *)      bad "不支持的平台 $(uname -s)（只在 macOS/Linux 上装过）"; return 1 ;;
  esac
  local url="https://dl.google.com/android/repository/commandlinetools-${os}-${CMDLINE_TOOLS_BUILD}_latest.zip"
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  echo "  下载 $url"
  if ! curl -fL --retry 3 -o "$tmp/clt.zip" "$url"; then
    bad "下载失败。这台机器多半没有外网（Agent 沙箱就是这样）⇒ 本地编译在这里无解，按既定做法让 CI 当编译裁判。"
    hint "先自查：curl -sI https://dl.google.com/ 通不通；不通就别再折腾本机环境了。"
    hint "若网络是通的而 404：CMDLINE_TOOLS_BUILD=<新构建号> 再跑一次（号在 developer.android.com/studio#command-line-tools-only）"
    return 1
  fi
  mkdir -p "$SDK_ROOT/cmdline-tools"
  unzip -q "$tmp/clt.zip" -d "$tmp/clt" || { bad "解压失败"; return 1; }
  # 包里是 cmdline-tools/，要摆成 SDK 要求的 cmdline-tools/latest/
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/clt/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest" || { bad "摆放目录失败"; return 1; }
  local mgr="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  yes | "$mgr" --licenses >/dev/null 2>&1 || note "licenses 没能在非交互下全部接受，稍后手动跑一次 --licenses"
  if "$mgr" "platform-tools" "platforms;$ANDROID_PLATFORM"; then
    ok "SDK 就位（build-tools 刻意不钉版本号，让 AGP 自己选）"
  else
    bad "sdkmanager 装 platform 失败（看上面的原始输出）"
    return 1
  fi
  printf '\n  ⚠️ 把这两行写进 shell 配置，否则下次开终端又要重装：\n'
  printf '     export ANDROID_HOME="%s"\n' "$SDK_ROOT"
  printf '     export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"\n'
}

verify_build() {
  hdr "跑一次 :app:assembleDebug（首次要下依赖，几分钟）"
  cd "$REPO_ROOT" || return 1
  if [ ! -x ./gradlew ]; then
    bad "./gradlew 不可执行：chmod +x gradlew"
    return 1
  fi
  # 不加 --stacktrace：日志会长到把真正的错误挤出屏幕；失败时 Gradle 自己会打可复现命令行。
  if ./gradlew :app:assembleDebug --console=plain; then
    ok ":app:assembleDebug 成功 ⇒ 这台机器可以自己判编译，不必等 CI"
    hint "顺手再跑：./gradlew :app:testDebugUnitTest && bash tools/ci-gates.sh"
  else
    bad "构建失败（上面是 Gradle 自己的输出）。修好之前本地结论不可信，仍以 CI 为准。"
    return 1
  fi
}

MODE="check"
case "${1:-}" in
  "") ;;
  --check) MODE="check" ;;
  --install-sdk) MODE="install" ;;
  --verify) MODE="verify" ;;
  --bootstrap) MODE="bootstrap" ;;
  -h|--help) sed -n '2,32p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *) printf '未知参数：%s（--check / --install-sdk / --verify / --bootstrap）\n' "$1" >&2; exit 2 ;;
esac

echo "chileme 构建环境自检 · 仓库根 $REPO_ROOT"
rc=0
case "$MODE" in
  check)     check_env ;;
  install)   install_sdk || rc=1 ;;
  verify)    verify_build || rc=1 ;;
  bootstrap) install_sdk || rc=1
             verify_build || rc=1 ;;
esac

if [ "$MODE" = "check" ]; then
  hdr "小结"
  printf '  就绪 %d · 提示 %d · 阻塞 %d\n' "$PASS" "$WARN" "$FAIL"
  if [ "$FAIL" -gt 0 ]; then
    printf '\n  这台机器**不能**本地编译 ⇒ 按既定做法推分支让 CI 判：\n'
    hint "git push -u origin <分支> && gh pr create … && gh pr checks --watch"
    hint "取失败详情：gh run list --limit 3 · gh run view <id> --log-failed"
    exit 1
  fi
  printf '\n  本地可用 ⇒ 提交前跑：./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest && bash tools/ci-gates.sh\n'
fi
exit "$rc"
