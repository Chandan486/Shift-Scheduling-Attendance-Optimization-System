#!/usr/bin/env bash
# Same steps as run-offline-harness.bat, for Linux/macOS.
set -euo pipefail
cd "$(dirname "$0")/../.."
H=verification/offline-harness
if command -v javac >/dev/null 2>&1; then JAVAC="javac"; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
rm -rf "$H/out" && mkdir -p "$H/out"
{ cat "$H/real-sources.txt"; find "$H/stubs" -name '*.java'; echo "$H/src/harness/Harness.java"; } > "$H/javac-args.txt"
$JAVAC -proc:none -d "$H/out" @"$H/javac-args.txt"
rm -f "$H/javac-args.txt"
java -cp "$H/out" harness.Harness
