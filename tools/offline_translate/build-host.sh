#!/bin/sh
# Host check of the same engine the phone runs: builds cytranslate-cli and translates sample sentences
# with the downloaded models, printing load time and per-sentence milliseconds.
#   MODELS=/path/with/zh-en,en-zh tools/offline_translate/build-host.sh
# Put the experiment data under D:/CyIME-Data/eval/offline-translate-<date>/ (AGENTS.md storage rules).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${WORK:-$HERE/work}
: "${MODELS:?set MODELS to the folder holding zh-en/ and en-zh/}"
COMMIT=fdc0f98706b193bf17cb419f2fe08bf1d9913607
mkdir -p "$WORK"
[ -d "$WORK/slimt" ] || git clone https://github.com/DavidVentura/slimt.git "$WORK/slimt"
git -C "$WORK/slimt" checkout -q $COMMIT
git -C "$WORK/slimt" submodule update --init --recursive
cmake -S "$HERE" -B "$WORK/build-host" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
  -DSLIMT_SOURCE_DIR="$WORK/slimt" -DCYTRANSLATE_CLI=ON
cmake --build "$WORK/build-host" --target cytranslate-cli -j "${JOBS:-2}"
CLI="$WORK/build-host/cytranslate-cli"
Z="$MODELS/zh-en"; E="$MODELS/en-zh"
echo "== zh -> en"
"$CLI" "$Z/model.zhen.intgemm.alphas.bin" "$Z/vocab.zhen.spm" "$Z/vocab.zhen.spm" "$Z/lex.50.50.zhen.s2t.bin" < "$HERE/samples.zh.txt"
echo "== en -> zh"
"$CLI" "$E/model.enzh.intgemm.alphas.bin" "$E/srcvocab.enzh.spm" "$E/trgvocab.enzh.spm" "$E/lex.50.50.enzh.s2t.bin" < "$HERE/samples.en.txt"
