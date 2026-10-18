#!/bin/bash
# Corpus weightings on the tuning/dev sets only; waits for the pruning runs and the models.
set -u
REPO=/mnt/d/GitHub/CyIME
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/v3
M=/home/priestess/cyime-ngram
SETS="tune=.codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json dev=.codex-artifacts/t9-ngram-20261010/dev/fixture.json"
until grep -q TUNE-PRUNE-DONE "$OUT/runs.log" 2>/dev/null && [ -s "$M/build-v5-w21.json" ]; do sleep 30; done
cd "$REPO"
for w in w12 w21; do
  bash tools/ime_lab/ngram/eval_runs.sh "$OUT" "ls-$w" "$M/model-v5-$w.lm" $SETS
done
echo TUNE-WEIGHTS-DONE | tee -a "$OUT/runs.log"
