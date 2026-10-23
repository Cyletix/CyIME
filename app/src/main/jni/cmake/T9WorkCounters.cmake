# Per-key work counters for the host replay speed gate (tools/ime_lab).
# OFF by default: the Android build never sets it, so every T9_WORK_* macro in the
# patched sources compiles to nothing. The host build passes -DCYIME_T9_WORK_COUNTERS=ON.
option(CYIME_T9_WORK_COUNTERS "Count per-key T9 lookup work (host replay only)" OFF)
if(CYIME_T9_WORK_COUNTERS)
  add_compile_definitions(CYIME_T9_WORK_COUNTERS=1)
endif()
