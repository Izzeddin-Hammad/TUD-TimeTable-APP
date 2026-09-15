#!/usr/bin/env bash
#
# JVM test harness for the app's pure-Kotlin logic.
#
# Why this exists: `./gradlew test` needs Gradle's user home (~/.gradle, ~2.9 GB of cached
# dependencies) to be writable. In sandboxes and CI images where it is not, the Android unit
# tests cannot run at all — so this script compiles the pure-logic sources and tests directly
# with Kotlin's compiler and the JUnit jars found in the read-only Gradle caches.
#
# It is a *fallback*, not a replacement: it covers the logic that has no Android framework
# dependency on the execution path (event keys, delta engine, group matching, week maths,
# preference coercion, semver). Room, WorkManager, Compose and networking tests still need
# `./gradlew test` / `./gradlew connectedAndroidTest`.
#
# ── When `~/.gradle` is not writable (CI containers, sandboxes) ────────────────────────────
#
# `./gradlew` fails in that situation with
#   FileNotFoundException: …/gradle-9.3.1-bin.zip.lck (Operation not permitted)
# because the wrapper and Gradle's home both live there. A real Gradle build still works by
# combining a workspace-local Gradle home with a read-only dependency cache, which lets Gradle
# write its own state while reading the pre-populated artifacts:
#
#   GRADLE_DIST=$(ls -d ~/.gradle/wrapper/dists/gradle-*/bin/*/gradle-*/ | head -1)   # extracted dist
#   GRADLE_USER_HOME="$PWD/.gradle-local" \
#   GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches" \
#     "$GRADLE_DIST"bin/gradle --console=plain test assembleDebug
#
# Verified: `test` → 159 tests, 0 failures; `assembleDebug` → a 25 MB APK.
# `.gradle-local/` is git-ignored; delete it afterwards to reclaim ~1 GB.
#
# Usage:
#   tools/jvm-test-harness.sh              # compile + run every pure-logic test class
#   tools/jvm-test-harness.sh --compile    # type-check the non-UI production source set
#   tools/jvm-test-harness.sh --list       # show what would run
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT=".harness-out"
CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
GRADLE_LIB="$(echo "$HOME"/.gradle/wrapper/dists/gradle-*-bin/*/gradle-*/lib 2>/dev/null | tr ' ' '\n' | tail -1)"
ANDROID_JAR="$(ls /Users/*/Library/Android/sdk/platforms/*/android.jar 2>/dev/null | tail -1)"

# ── Production sources under test (pure logic, no Android on the execution path) ──
MAIN_SOURCES=(
  "app/src/main/java/com/example/timetablescraper/api/Models.kt"
  "app/src/main/java/com/example/timetablescraper/api/EventKey.kt"
  "app/src/main/java/com/example/timetablescraper/api/DublinTime.kt"
  "app/src/main/java/com/example/timetablescraper/api/GroupMatcher.kt"
  "app/src/main/java/com/example/timetablescraper/api/TimetableDiff.kt"
  "app/src/main/java/com/example/timetablescraper/api/Semver.kt"
  "app/src/main/java/com/example/timetablescraper/api/SyncStrategy.kt"
  "app/src/main/java/com/example/timetablescraper/api/TimetableUtils.kt"
  "app/src/main/java/com/example/timetablescraper/util/SafePrefs.kt"
  "app/src/main/java/com/example/timetablescraper/util/CrashFlags.kt"
)

TEST_SOURCES=(
  "app/src/test/java/com/example/timetablescraper/api/EventKeyTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/GroupMatcherTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/TimetableDiffTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/SemverTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/SyncStrategyContractTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/TimetableUtilsTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/TimetableUtilsEdgeCaseTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/GroupFilteringTest.kt"
  "app/src/test/java/com/example/timetablescraper/api/DublinTimeTest.kt"
  "app/src/test/java/com/example/timetablescraper/util/SafePrefsTest.kt"
  "app/src/test/java/com/example/timetablescraper/util/CrashFlagsTest.kt"
)

TEST_CLASSES=(
  "com.example.timetablescraper.api.EventKeyTest"
  "com.example.timetablescraper.api.GroupMatcherTest"
  "com.example.timetablescraper.api.TimetableDiffTest"
  "com.example.timetablescraper.api.SemverTest"
  "com.example.timetablescraper.api.SyncStrategyContractTest"
  "com.example.timetablescraper.api.TimetableUtilsTest"
  "com.example.timetablescraper.api.TimetableUtilsEdgeCaseTest"
  "com.example.timetablescraper.api.GroupFilteringTest"
  "com.example.timetablescraper.util.SafePrefsTest"
  "com.example.timetablescraper.util.CrashFlagsTest"
  "com.example.timetablescraper.util.CrashMarkerTest"
)

if [[ "${1:-}" == "--list" ]]; then
  printf 'main:  %s\n' "${MAIN_SOURCES[@]}"
  printf 'tests: %s\n' "${TEST_CLASSES[@]}"
  exit 0
fi

# ── --compile: type-check the non-UI production source set (Room + OkHttp + coroutines) ──
# The UI source set is deliberately excluded: codegen for @Composable functions needs the Compose
# compiler plugin, which must match the Kotlin compiler exactly. That pairing only exists inside
# the Gradle build, so the UI files are covered by `./gradlew test` / `./gradlew assembleDebug`.
if [[ "${1:-}" == "--compile" ]]; then
  ANDROID_JAR="$(ls /Users/*/Library/Android/sdk/platforms/*/android.jar 2>/dev/null | tail -1)"
  [[ -n "$ANDROID_JAR" ]] || { echo "harness: no android.jar found" >&2; exit 1; }
  STDLIB="$(find "$CACHE" -path "*org.jetbrains.kotlin/kotlin-stdlib/2.2.10*" -name "kotlin-stdlib-2.2.10.jar" 2>/dev/null | head -1)"
  [[ -n "$STDLIB" ]] || { echo "harness: kotlin-stdlib not found in $CACHE" >&2; exit 1; }
  mkdir -p "$OUT/lib"
  find_jar() { find "$CACHE" -path "*$1*" \( -name "*.aar" -o -name "*.jar" \) 2>/dev/null | grep -v sources | grep -v javadoc | sort | tail -1; }
  unpack() { # unpack <path-fragment> <target-name>
    j="$(find_jar "$1")"
    [[ -z "$j" ]] && { echo "harness: missing dependency $1" >&2; return 1; }
    if [[ "$j" == *.aar ]]; then ( cd "$OUT/lib" && unzip -o -q "$j" classes.jar && mv -f classes.jar "$2" )
    else cp -f "$j" "$OUT/lib/$2"; fi
  }
  unpack "androidx.room/room-runtime-android" room-runtime.jar
  unpack "androidx.room/room-common" room-common.jar
  unpack "androidx.sqlite/sqlite-android" sqlite.jar
  unpack "androidx.sqlite/sqlite-framework-android" sqlite-framework.jar
  unpack "androidx.annotation/annotation-jvm" annotation.jar
  unpack "androidx.arch.core/core-runtime" arch-core.jar
  unpack "com.squareup.okhttp3/okhttp" okhttp.jar
  unpack "com.squareup.okio/okio-jvm" okio.jar
  unpack "kotlinx-coroutines-core-jvm" coroutines.jar
  unpack "androidx.compose.runtime/runtime-android" compose-runtime.jar
  unpack "androidx.compose.runtime/runtime-annotation-android" compose-annotation.jar
  # androidx.core supplies FileProvider, used by the in-app update install path.
  unpack "androidx.core/core/" core.jar

  CPT="$STDLIB:$ANDROID_JAR"
  for jar in "$OUT"/lib/*.jar; do CPT="$CPT:$jar"; done
  rm -rf "$OUT/compile-check" && mkdir -p "$OUT/compile-check"
  echo "▸ type-checking the non-UI production source set"
  java -cp "$GRADLE_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -cp "$CPT" \
    -d "$OUT/compile-check" -nowarn \
    app/src/main/java/com/example/timetablescraper/SyncPreferences.kt \
    app/src/main/java/com/example/timetablescraper/CrashHandler.kt \
    app/src/main/java/com/example/timetablescraper/util/SafePrefs.kt \
    app/src/main/java/com/example/timetablescraper/api/*.kt \
    app/src/main/java/com/example/timetablescraper/api/cache/*.kt \
    app/src/main/java/com/example/timetablescraper/update/*.kt \
    tools/stubs/BuildConfigStub.kt 2>&1 | grep -v "^warning:" || true
  COUNT="$(find "$OUT/compile-check" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')"
  [[ "$COUNT" -gt 0 ]] || { echo "harness: compile check failed" >&2; exit 1; }
  echo "▸ compile check OK ($COUNT classes)"
  exit 0
fi

fail() { echo "harness: $*" >&2; exit 1; }

[[ -d "$GRADLE_LIB" ]] || fail "Gradle distribution libs not found (looked under ~/.gradle/wrapper/dists)"
[[ -n "$ANDROID_JAR" && -f "$ANDROID_JAR" ]] || fail "android.jar not found under ~/Library/Android/sdk/platforms"

find_jar() { find "$CACHE" -path "*$1*" -name "$2" 2>/dev/null | grep -v sources | head -1; }

STDLIB="$(find_jar "org.jetbrains.kotlin/kotlin-stdlib/2.2.10" "kotlin-stdlib-2.2.10.jar")"
JUNIT="$(find_jar "junit/junit/4.13.2" "junit-4.13.2.jar")"
HAMCREST="$(find_jar "org.hamcrest" "hamcrest-core-1.3.jar")"
[[ -n "$STDLIB" && -n "$JUNIT" && -n "$HAMCREST" ]] || fail "Kotlin stdlib / JUnit jars not found in $CACHE"

# @Immutable from Compose is an annotation on the pure models; unpack it from the AAR.
mkdir -p "$OUT/lib"
extract_aar() {
  local aar="$1" name="$2"
  [[ -f "$OUT/lib/$name" ]] && return 0
  [[ -n "$aar" && -f "$aar" ]] || return 1
  ( cd "$OUT/lib" && unzip -o -q "$aar" classes.jar && mv -f classes.jar "$name" )
}
extract_aar "$(find_jar "androidx.compose.runtime/runtime-android" "runtime.aar")" "compose-runtime.jar"
extract_aar "$(find_jar "androidx.compose.runtime/runtime-annotation-android" "runtime-annotation.aar")" "compose-annotation.jar"

CP_RUNTIME="$STDLIB:$JUNIT:$HAMCREST:$ANDROID_JAR"
for jar in "$OUT/lib/compose-runtime.jar" "$OUT/lib/compose-annotation.jar"; do
  [[ -f "$jar" ]] && CP_RUNTIME="$CP_RUNTIME:$jar"
done

rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"

echo "▸ compiling ${#MAIN_SOURCES[@]} production files + ${#TEST_SOURCES[@]} test files"
java -cp "$GRADLE_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$CP_RUNTIME" -d "$OUT/classes" -nowarn \
  "${MAIN_SOURCES[@]}" "${TEST_SOURCES[@]}" 2>&1 | grep -v "^warning:" || true

if ! ls "$OUT/classes" >/dev/null 2>&1 || [[ -z "$(find "$OUT/classes" -name '*.class' -print -quit)" ]]; then
  fail "compilation failed — see the errors above"
fi

echo "▸ running ${#TEST_CLASSES[@]} test classes"
java -cp "$OUT/classes:$CP_RUNTIME" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
STATUS=$?

echo "▸ harness finished (exit $STATUS); classes in $OUT/classes"
exit $STATUS
