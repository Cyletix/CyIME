#!/bin/bash
# Sequential host replays with an explicit model per run (run inside WSL).
# Usage: eval_runs.sh OUT_DIR LABEL MODEL|none name=fixture.json [name=fixture.json ...]
# MODEL=none replays the previous scoring (no n-gram model in the resources).
set -u
REPO=/mnt/d/GitHub/CyIME
TARGET=$REPO/app/build/generated/t9-ngram/rime_chinese/t9_ngram.lm
JAR=/mnt/c/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
OUT=$1; LABEL=$2; MODEL=$3; shift 3
mkdir -p "$OUT" "$(dirname "$TARGET")"
rm -f "$TARGET"
[ "$MODEL" != none ] && cp "$MODEL" "$TARGET"
for spec in "$@"; do
  name=${spec%%=*}; fixture=${spec#*=}; dir="$OUT/$LABEL-$name"
  ( cd "$REPO" && nice -n 19 python3 tools/ime_lab/t9_replay.py run --fixture "$fixture" --output "$dir" --json-jar "$JAR" ) > "$dir.log" 2>&1
  echo "$LABEL:$name model=$(basename "$MODEL") $(tail -1 "$dir.log")" | tee -a "$OUT/runs.log"
done
