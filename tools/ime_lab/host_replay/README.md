# Local Rime/T9 engine

The normal entry point is `python tools/ime_lab/t9_replay.py run`. Windows uses
WSL; Linux runs directly. No Android device, emulator, APK build or installation
is involved. See [the fixed workflow](../../../docs/development/t9-algorithm-regression.md).

`build_host.py --repo PATH --cache PATH` builds the existing production JNI,
candidate policy, patched librime and Lua/octagram/predict/T9 plugins for Linux
x86_64. It copies the native source into a private cache before the production
CMake patch/copy steps execute. Windows CRLF is normalized in that copy for Git
patch checks; the source tree is unchanged. `host_tail.cmake` replaces only the
Android application/JNI linking tail. `android/log.h` routes logs to stderr and
`FindBoost.cmake` keeps the vendored Boost targets instead of a system version.

The helper downloads pinned official CMake/Ninja wheels, the Linux ONNX Runtime
1.28 library and JNI headers into its cache, without a global package install.
It checks the ONNX C API against the production headers and records dependency,
source, adapter and library hashes. A Java runtime containing the compiler
module is sufficient; the helper provides a local `javac` launcher for that
module. Native compilation is limited to two jobs. No source algorithm is
reimplemented in Python, and the model is never disabled to make a run pass.

`ChatReplayHostChecks.java` exercises host-only path/flag guards. The replay
itself is shared with `../android_replay/ChatReplay.java` and invoked with
`--host --resources ZIP`; every run requires a new data directory. Resource ZIPs
contain the existing project dictionaries, schemas, grammar, sentence model and
vocabulary, with per-file SHA256 manifests. They are test inputs, not APKs.

Build logs and artifacts live in the private cache, and each result directory
retains build/source manifests as well as raw replay evidence. A successful
build or replay does not claim that candidate quality passed; check quality and
comparison exit codes separately.
