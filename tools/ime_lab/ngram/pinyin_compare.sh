#!/bin/bash
# 26-key full pinyin (rime_ice) first-candidate check, plain and with the same grammar as T9.
# Uses the engine library of an existing host run. Run inside WSL.
set -u
REPO=/mnt/d/GitHub/CyIME
RUN=$1   # a completed host run directory (engine library + deployed user data)
OUT=$REPO/.codex-artifacts/t9-ngram-20261010/pinyin
W=$HOME/cyime-pinyin
JAR=/mnt/c/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
JAVAC=$HOME/.cache/cyime-t9-host/dependencies/javac
LIB=$(python3 -c "import json;print(json.load(open('$RUN/engine-build.json'))['library_dir'])" 2>/dev/null || true)
[ -n "$LIB" ] || LIB=$(python3 -c "import json;d=json.load(open('$RUN/build-result.txt'));print(d['library_dir'])")
mkdir -p "$OUT" "$W/classes"
"$JAVAC" -encoding UTF-8 -cp "$RUN/runner/classes:$JAR" -d "$W/classes" "$REPO/tools/ime_lab/ngram/PinyinReplay.java"
for variant in plain gram; do
  rm -rf "$W/$variant"; mkdir -p "$W/$variant"
  rsync -a --exclude build --exclude '*.userdb' "$RUN/native-run/user/" "$W/$variant/"
  sed -i 's/^    - schema: t9_pinyin$/    - schema: rime_ice/' "$W/$variant/default.custom.yaml"
  if [ "$variant" = gram ]; then
    printf 'patch:\n  grammar:\n    language: wanxiang-lts-zh-hans\n    collocation_max_length: 6\n    collocation_min_length: 2\n    collocation_penalty: -14\n    non_collocation_penalty: -6\n    rear_penalty: -18\n    weak_collocation_penalty: -100\n' > "$W/$variant/rime_ice.custom.yaml"
  fi
  cd "$REPO"
  nice -n 19 /usr/bin/java -cp "$W/classes:$RUN/runner/classes:$JAR" com.cyime.audit.PinyinReplay "$LIB" "$W/$variant" rime_ice \
    "$OUT/$variant.jsonl" \
    tools/ime_lab/fixtures/user_reported_20261009.json .codex-artifacts/t9-ngram-20261010/anota/fixture.json \
    tools/ime_lab/fixtures/everyday_clauses_20261009.json .codex-artifacts/t9-ngram-20261010/gen/everyday/fixture.json \
    .codex-artifacts/t9-ngram-20261010/gen/constructions/fixture.json tools/ime_lab/fixtures/daily_chat_20261003.json \
    .codex-artifacts/t9-ngram-20261010/gen/tune/fixture.json .codex-artifacts/t9-ngram-20261010/dev/fixture.json \
    > "$OUT/$variant.summary.jsonl" 2> "$OUT/$variant.log"
  echo "$variant exit=$?" >> "$OUT/done.log"
done
echo PINYIN-DONE >> "$OUT/done.log"
