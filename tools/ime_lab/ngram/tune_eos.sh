#!/bin/bash
# Sentence-end weight on the tuning/dev sets only; waits for the weighting runs. Run inside WSL.
set -u
REPO=/mnt/d/GitHub/CyIME
SCHEMA=$REPO/app/src/main/assets/rime_chinese/t9_pinyin.schema.yaml
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/v3
M=${M:-$HOME/cyime-work/ngram}  # build workspace; inputs archived in D:/CyIME-Data/corpus, models in D:/CyIME-Data/models
SETS="tune=.codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json dev=.codex-artifacts/t9-ngram-20261010/dev/fixture.json"
until grep -q TUNE-WEIGHTS-DONE "$OUT/runs.log" 2>/dev/null; do sleep 30; done
cd "$REPO"
for eos in 0.3 0.6 1.0; do
  sed -i "s/^  ngram_eos_weight: .*/  ngram_eos_weight: $eos/" "$SCHEMA"
  bash tools/ime_lab/ngram/eval_runs.sh "$OUT" "ls-tri-eos$eos" "$M/model-v3-ls-tri.lm" $SETS
done
sed -i "s/^  ngram_eos_weight: .*/  ngram_eos_weight: 0/" "$SCHEMA"
echo TUNE-EOS-DONE | tee -a "$OUT/runs.log"
