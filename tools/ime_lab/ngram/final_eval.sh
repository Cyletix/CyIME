#!/bin/bash
# Final schema check for a word n-gram model (run inside WSL, one replay at a time, nice 19):
# quality on every fixed set, then the speed gate sessions (B C B C B) on the chat fixture with an
# empty and with the preset user dictionary. Baseline = current source without the model.
# Usage: final_eval.sh OUT_DIR MODEL.lm
set -u
REPO=/mnt/d/GitHub/CyIME
OUT=$1; MODEL=$2
TARGET=$REPO/app/build/generated/t9-ngram/rime_chinese/t9_ngram.lm
JAR=/mnt/c/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
SEED=tools/ime_lab/fixtures/user_dict_seed_20261009.userdb.txt
A=.codex-artifacts/t9-ngram-20261010
cd "$REPO"
mkdir -p "$OUT" "$(dirname "$TARGET")"
run() {  # run NAME [replay arguments...]
  local name=$1; shift
  nice -n 19 python3 tools/ime_lab/t9_replay.py run --output "$OUT/$name" --json-jar "$JAR" "$@" > "$OUT/$name.log" 2>&1
  echo "$name $(tail -1 "$OUT/$name.log")" | tee -a "$OUT/runs.log"
}
cp "$MODEL" "$TARGET"
for spec in reported=tools/ime_lab/fixtures/user_reported_20261009.json anota=$A/anota/fixture.json \
    everyday120=tools/ime_lab/fixtures/everyday_clauses_20261009.json gen-everyday=$A/gen/everyday/fixture.json \
    constructions=$A/gen/constructions/fixture.json; do
  run "quality-${spec%%=*}" --fixture "${spec#*=}"
done
for scenario in empty seed; do
  extra=(); [ "$scenario" = seed ] && extra=(--user-dict "$SEED")
  rm -f "$TARGET"; run "$scenario-B1" "${extra[@]}"
  cp "$MODEL" "$TARGET"; run "$scenario-C1" "${extra[@]}"
  run "$scenario-B2" --engine-from "$OUT/$scenario-B1" "${extra[@]}"
  run "$scenario-C2" --engine-from "$OUT/$scenario-C1" "${extra[@]}"
  run "$scenario-B3" --engine-from "$OUT/$scenario-B1" "${extra[@]}"
  nice -n 19 python3 tools/ime_lab/t9_replay.py compare "$OUT/$scenario-B1" "$OUT/$scenario-C1" \
    --noise-baseline "$OUT/$scenario-B2" --noise-baseline "$OUT/$scenario-B3" --candidate-repeat "$OUT/$scenario-C2" \
    --output "$OUT/$scenario-compare" > "$OUT/$scenario-compare.log" 2>&1
  echo "$scenario-compare exit=$? $(tail -1 "$OUT/$scenario-compare.log")" | tee -a "$OUT/runs.log"
done
echo FINAL-DONE | tee -a "$OUT/runs.log"
