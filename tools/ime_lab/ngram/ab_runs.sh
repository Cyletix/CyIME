#!/bin/bash
# Replay fixtures with the same engine twice: n-gram model present (new) and absent (old).
# Usage: ab_runs.sh OUT_DIR name=fixture.json [name=fixture.json ...]   (run inside WSL)
set -u
REPO=/mnt/d/GitHub/CyIME
MODEL_DIR=$REPO/app/build/generated/t9-ngram
PARKED=$REPO/app/build/generated/t9-ngram.parked
JAR=/mnt/c/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
OUT=$1; shift
mkdir -p "$OUT"
run() {  # $1 label, $2 fixture, $3 output
  ( cd "$REPO" && nice -n 19 python3 tools/ime_lab/t9_replay.py run --fixture "$2" --output "$3" --json-jar "$JAR" ) \
    > "$3.log" 2>&1
  echo "$1 $(tail -1 "$3.log")" | tee -a "$OUT/runs.log"
}
for spec in "$@"; do
  name=${spec%%=*}; fixture=${spec#*=}
  [ -d "$PARKED" ] && mv "$PARKED" "$MODEL_DIR"
  run "new:$name" "$fixture" "$OUT/new-$name"
  mv "$MODEL_DIR" "$PARKED"
  run "old:$name" "$fixture" "$OUT/old-$name"
  mv "$PARKED" "$MODEL_DIR"
done
