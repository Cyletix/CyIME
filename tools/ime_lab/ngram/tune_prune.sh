#!/bin/bash
# Pruning thresholds on the tuning/dev sets only. Waits for tune_v3.sh to finish. Run inside WSL.
set -u
REPO=/mnt/d/GitHub/CyIME
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/v3
M=${M:-$HOME/cyime-work/ngram}  # build workspace; inputs archived in D:/CyIME-Data/corpus, models in D:/CyIME-Data/models
SETS="tune=.codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json dev=.codex-artifacts/t9-ngram-20261010/dev/fixture.json"
until grep -q TUNE-V3-DONE "$OUT/runs.log" 2>/dev/null; do sleep 30; done
cd "$REPO"
for t in 2e-7 5e-8 1e-8; do
  bash tools/ime_lab/ngram/eval_runs.sh "$OUT" "ls-p$t" "$M/model-v4-ls-p$t.lm" $SETS
done
echo TUNE-PRUNE-DONE | tee -a "$OUT/runs.log"
