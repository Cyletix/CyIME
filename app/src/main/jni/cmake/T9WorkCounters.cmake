# Per-key work counters for the host replay speed gate (tools/ime_lab).
# OFF by default: the Android build never sets it, so its generated sources and
# objects stay unchanged. The host build passes -DCYIME_T9_WORK_COUNTERS=ON.
option(CYIME_T9_WORK_COUNTERS "Count per-key T9 lookup work (host replay only)" OFF)
if(CYIME_T9_WORK_COUNTERS)
  add_compile_definitions(CYIME_T9_WORK_COUNTERS=1)
endif()

# Insert a counter into a build-local source copy held in `variable`. The anchor
# must occur exactly `expected` times, so a changed source fails the host build
# instead of silently miscounting.
function(t9_work_replace variable old new expected)
  set(text "${${variable}}")
  string(REPLACE "${old}" "" stripped "${text}")
  string(LENGTH "${text}" total)
  string(LENGTH "${stripped}" rest)
  string(LENGTH "${old}" size)
  math(EXPR found "(${total} - ${rest}) / ${size}")
  if(NOT found EQUAL expected)
    message(FATAL_ERROR "T9 work counter anchor found ${found} times, expected ${expected}: ${old}")
  endif()
  string(REPLACE "${old}" "${new}" text "${text}")
  set(${variable} "${text}" PARENT_SCOPE)
endfunction()
