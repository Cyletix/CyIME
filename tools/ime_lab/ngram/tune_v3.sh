#!/bin/bash
# Compare data sources and model order on the tuning/dev sets only. Run inside WSL.
set -u
REPO=/mnt/d/GitHub/CyIME
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/v3
M=${M:-$HOME/cyime-work/ngram}  # build workspace; inputs archived in D:/CyIME-Data/corpus, models in D:/CyIME-Data/models
SETS="tune=.codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json dev=.codex-artifacts/t9-ngram-20261010/dev/fixture.json"
cd "$M"
[ -f model-v3-l-bi.lm ] || nice -n 19 ./ngram_build3 vocab.tsv train.txt model-v3-l-bi.lm --prior-weight 0.1 --no-trigrams --tmp "$M/tmp" > build-v3-l-bi.json 2> build-v3-l-bi.log
cd "$REPO"
for model in l-bi l-tri ls-bi ls-tri; do
  bash tools/ime_lab/ngram/eval_runs.sh "$OUT" "$model" "$M/model-v3-$model.lm" $SETS
done
echo TUNE-V3-DONE | tee -a "$OUT/runs.log"
