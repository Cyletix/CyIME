#!/bin/bash
# Tuning runs on tuning/dev sets only (never the evaluation sets). Run inside WSL.
set -u
REPO=/mnt/d/GitHub/CyIME
SCHEMA=$REPO/app/src/main/assets/rime_chinese/t9_pinyin.schema.yaml
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/tune
M=${M:-$HOME/cyime-work/ngram}  # build workspace; inputs archived in D:/CyIME-Data/corpus, models in D:/CyIME-Data/models
SETS="tune=.codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json dev=.codex-artifacts/t9-ngram-20261010/dev/fixture.json"
config() {  # label model start
  sed -i "s/^  ngram_sentence_start: .*/  ngram_sentence_start: $3/" "$SCHEMA"
  bash $REPO/tools/ime_lab/ngram/eval_runs.sh "$OUT" "$1" "$2" $SETS
}
config p00-ml $M/model-v1-mc2.bin false
config p10-ml $M/model-v2-p0.1.lm false
config p30-ml $M/model-v2-p0.3.lm false
config p10-bos $M/model-v2-p0.1.lm true
config p30-bos $M/model-v2-p0.3.lm true
sed -i "s/^  ngram_sentence_start: .*/  ngram_sentence_start: false/" "$SCHEMA"
echo TUNE-DONE | tee -a "$OUT/runs.log"
