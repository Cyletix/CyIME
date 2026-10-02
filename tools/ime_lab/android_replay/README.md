# Reusable native T9 replay

Use [`../t9_replay.py`](../t9_replay.py) as the normal entry point:

```powershell
python tools/ime_lab/t9_replay.py run --output D:/CyIME-Data/t9-runs/new-run
```

The normal `run` compiles/reuses the existing project native source on this
computer (Linux, or Windows through WSL). It never accesses an Android device.
`--fixture` supplies another reviewed corpus and `--baseline` compares a previous
host result directory. Device execution is available only through the explicit
`android-run` subcommand, whose `--apk` and `--library-dir` choose existing artifacts.
See [the regression workflow](../../../docs/development/t9-algorithm-regression.md)
for corpus preparation, reports, and review requirements. The instructions below
describe the optional Android runner for diagnosis. The same Java replay also
runs on a desktop JDK using `--host --resources resources.zip`; this mode requires
a new root directory and forbids reuse. Host compilation uses the real project
JNI, T9 plugin, candidate policy and model, with Android logging sent to stderr.

`ChatReplay.java` runs through Android `app_process` without building, installing,
or modifying the application APK. `RimeEngine.java` declares only the existing JNI
ABI. Compile these two Java files against the Android SDK's `android.jar`, then
dex the class files with the SDK's D8 tool. The installed native library directory
must be on `LD_LIBRARY_PATH` if its dependencies are not resolved automatically.

Example device command, after pushing the dex JAR and corpus:

```sh
CLASSPATH=/data/local/tmp/cyime-chat-audit-20261003/replay.jar \
LD_LIBRARY_PATH=/data/app/APP_PATH/lib/arm64 \
app_process /system/bin com.cyime.audit.ChatReplay \
  --apk /data/app/APP_PATH/base.apk \
  --lib-dir /data/app/APP_PATH/lib/arm64 \
  --root /data/local/tmp/cyime-chat-audit-20261003 \
  --corpus /data/local/tmp/cyime-chat-audit-20261003/corpus.json \
  --output /data/local/tmp/cyime-chat-audit-20261003/replay.jsonl
```

The corpus is an array, or an object with `cases` or `clauses` containing:

```json
{"id":"sample","text":"早上","pinyin":["zao","shang"],"keys":"92674264"}
```

Each case must have a unique, nonempty string `id`, nonempty `text`, and exactly
one string pinyin entry per Unicode target character. Pinyin accepts ASCII
letters (`v` or `ü` for ü) and an optional final tone digit 1–5; the runner
normalizes case and strips that tone digit. `keys` is optional but, when present,
must match the pinyin's standard 2–9 mapping. All cases, including cases excluded
by `--only-ids`, are validated before extracting resources or loading native code.
Unknown case IDs, malformed flags, duplicate flags/modes, and existing outputs
are rejected before extraction. Supply a reviewed pinyin reading for polyphones;
the runner does not guess pronunciation from expected text.

The runner extracts the APK's base `assets/rime` resources, then applies the
bundled manifest's source-to-destination mapping and verifies each manifest hash
and byte count. It copies the app's `default.custom.yaml`, restricts deployment
to `t9_pinyin`, applies the production JNI schema patches, and deploys the fresh
resources. It never reads application data or copies a user dictionary. The
dedicated root must start with `/data/local/tmp/cyime-chat-audit-`. An existing
`user` directory is rejected unless `--reuse` explicitly reuses this runner's
marked resources. Output files are never overwritten. Normal regression runs
must use a fresh root. `--reuse` is for deliberate ablations and diagnostics;
it requires the same APK path, APK SHA256, and native library SHA256 values.
Old markers without these hashes are rejected. Reuse permits configuration
changes in the isolated audit directory and records the active configuration
hashes; it must not be compared as if it were a fresh bundled-resource baseline.

Default modes are `continuous,paused`. Both call native key processing and flush
for every digit and inspect immediate candidates. Continuous mode waits for
neural refinement at clause endpoints; paused mode also waits at each syllable
boundary. The wait is bounded at 5 seconds and records timeout/readiness/refine
results. Each clause begins with composition reset, and no candidates are
committed or memorized. Refinement is never applied implicitly during a pass.

Options:

- `--modes continuous,paused` or `--modes endpoints` for endpoint-only diagnosis.
- `--key-delay-ms 35` specifies additional delay after each inspected digit;
  this is not a claim of exact inter-key timing because engine and logging time
  are additional. Zero requests an engine-throughput replay.
- `--limit 20 --boundary-limit 100` controls immediate and syllable row counts.
- `--settle-timeout-ms 5000` controls the model-ready deadline.
- `--only-ids id1,id2` (alias `--case-ids`) restricts cases.
- `--variant digits` is the ordinary unsegmented test. `separated` inserts an
  apostrophe between syllables; `locked` explicitly selects each supplied pinyin
  using the existing native method. The latter two are diagnostic interventions.
- `--reuse` skips extraction and deployment for the same marked APK and native
  libraries; it is not used by the standard regression workflow.

Protocol 1 JSONL includes APK, native library, corpus, source schema, deployed
schema, and default configuration SHA256 fingerprints as additive fields.
Metadata also records selected IDs, input variant, modes, candidate limits,
delays, timeout, and whether resources were reused. It captures candidate rows
up to the requested limit in native order, including their text,
comment, genuine type, quality, index, start/end span, phrase weight/code, and
sentence components. `target_prefix_rank` is the 1-based **exact** expected text
prefix rank at a completed syllable, or -1 if absent within the captured limit.
At an incomplete syllable this field is null; the current top candidate remains
available for language-quality review. `clause_settled` is the endpoint outcome.
`keys` is the accumulated digit sequence, `sent_keys` includes diagnostic
apostrophes, and `input`/`remaining_digits` are the actual native engine state.
This tests the installed native decoding stack with fresh bundled resources; it
does not establish acceptance of the keyboard UI or the user's personalized
dictionary and settings.

Exact top-1 equality at every completed syllable and at the clause endpoint is
an algorithmic assertion. A different top-1 is not automatically nonsense:
T9 ambiguity and an unfinished syllable can produce legitimate alternatives.
Review new unexpected text separately, save confirmed unnatural outputs as
regression expectations, and fail those expectations on recurrence. Keep the
raw per-key rows so the entire input path can be reviewed rather than only its
final result.

Identical input requires identical engine libraries, dictionaries/models,
configuration, clean learning state, and replay options for a useful comparison.
Immediate snapshots can still differ with asynchronous worker scheduling and
machine speed. Compare settled outcomes separately, and treat a refinement
timeout as incomplete evidence rather than a settled passing result. The
runner does not build or install an APK, select/commit candidate text, or
validate UI hitboxes or actual application text delivery.
