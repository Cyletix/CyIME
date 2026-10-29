# Build the event player dex into the demo data folder: bash tools/demo_typer/inject/build.sh
set -e
SDK=${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}
OUT=${CYIME_DEMO_DATA:-D:/CyIME-Data/demo-video}
TMP=$(mktemp -d)
javac --release 11 -cp "$SDK/platforms/android-36/android.jar" -d "$TMP" "$(dirname "$0")/Inject.java"
cmd //c "$(cygpath -w "$SDK/build-tools/36.0.0/d8.bat") --min-api 28 --output $(cygpath -w "$TMP") $(cygpath -w "$TMP/Inject.class")"
cp "$TMP/classes.dex" "$OUT/cyime-inject.dex"
rm -rf "$TMP"
echo "$OUT/cyime-inject.dex"
