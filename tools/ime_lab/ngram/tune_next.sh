#!/bin/bash
# Tuning-set experiments with the trigram model (run inside WSL, one replay at a time, nice 19).
# Only the tuning sets (gen/tune, LCCC dev) choose settings; the fixed test sets are never used here.
# Each setting is a schema value; the schema is restored at the end. The reference run keeps its
# deployed copy for ChunkReplay (left context); other runs drop their regenerable copies.
set -u
REPO=/mnt/d/GitHub/CyIME
SCHEMA=$REPO/app/src/main/assets/rime_chinese/t9_pinyin.schema.yaml
OUT=${OUT:-$REPO/.codex-artifacts/t9-ngram-20261010/next}
M=/home/priestess/cyime-ngram
TARGET=$REPO/app/build/generated/t9-ngram/rime_chinese/t9_ngram.lm
JAR=/mnt/c/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
A=.codex-artifacts/t9-ngram-20261010
cd "$REPO"
mkdir -p "$OUT" "$(dirname "$TARGET")"
setting() { sed -i "s/^  $1: .*/  $1: $2/" "$SCHEMA"; grep -q "^  $1: $2$" "$SCHEMA" || { echo "missing $1" | tee -a "$OUT/runs.log"; exit 1; }; }
run() {  # run LABEL MODEL [keep]
  cp "$2" "$TARGET"
  for set in tune=$A/gen/tune/fixture.json dev=$A/dev/fixture.json; do
    local dir="$OUT/$1-${set%%=*}"
    nice -n 19 python3 tools/ime_lab/t9_replay.py run --fixture "${set#*=}" --output "$dir" --json-jar "$JAR" > "$dir.log" 2>&1
    echo "$1:${set%%=*} $(tail -1 "$dir.log")" | tee -a "$OUT/runs.log"
    [ "${3:-}" = keep ] || rm -rf "$dir/native-run" "$dir/resources.zip"
  done
}
LM=$M/model-v3-ls-tri.lm
run base "$LM" keep
setting ngram_merge_segmentations true; run merge "$LM"; setting ngram_merge_segmentations false
for eos in 0.3 0.6 1.0; do setting ngram_eos_weight "$eos"; run "eos$eos" "$LM"; done
setting ngram_eos_weight 0
setting ngram_sentence_start true; run bos "$LM"; setting ngram_sentence_start false
setting dictionary_beam 64; run beam64 "$LM"; setting dictionary_beam 16
for model in w12 w21; do run "$model" "$M/model-v5-$model.lm"; done
# Left context (committed text before the composition), engine and deployed data of the reference run.
RUN=$OUT/base-tune
LIB=$(python3 -c "import json;import os;print(os.path.dirname(json.load(open('$RUN/engine-build.json'))['library']))")
W=$HOME/cyime-chunk; rm -rf "$W"; mkdir -p "$W/classes" "$W/user"
"$HOME/.cache/cyime-t9-host/dependencies/javac" -encoding UTF-8 -cp "$RUN/runner/classes:$JAR" -d "$W/classes" tools/ime_lab/ngram/ChunkReplay.java
rsync -a --exclude build --exclude '*.userdb' "$RUN/native-run/user/" "$W/user/"
for cases in tune-dev test; do
  nice -n 19 /usr/bin/java -cp "$W/classes:$RUN/runner/classes:$JAR" com.cyime.audit.ChunkReplay "$LIB" "$W/user" \
    "$OUT/chunk-$cases.jsonl" "$A/chunk/$cases.json" > "$OUT/chunk-$cases.summary" 2> "$OUT/chunk-$cases.log"
  echo "chunk:$cases exit=$? $(cat "$OUT/chunk-$cases.summary")" | tee -a "$OUT/runs.log"
done
grep -n "ngram_\|dictionary_beam" "$SCHEMA" | tee -a "$OUT/runs.log"
echo NEXT-DONE | tee -a "$OUT/runs.log"
