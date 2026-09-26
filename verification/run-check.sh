#!/usr/bin/env bash
set -euo pipefail
here="$(cd -- "$(dirname -- "$0")" && pwd)"
helper="$here/../app/src/main/java/com/kingzcheung/xime/settings/KeyboardHeightProfiles.kt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
kotlinc -J-Xmx512m "$here/AndroidStubs.kt" "$here/SettingsPreferencesStub.kt" "$here/DragBarConstant.kt" "$here/ConfirmationHarness.kt" "$here/HeightRegression.kt" "$here/KeyboardGeometry.kt" "$helper" -include-runtime -d "$tmp/test.jar"
java -Xmx128m -jar "$tmp/test.jar"
