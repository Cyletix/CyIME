# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# Lua's Android fseeko guard comes from patches/cyime-lua-android.patch (Gradle applyCyimeSourcePatches).
include("${CMAKE_CURRENT_LIST_DIR}/CyimePatches.cmake")
# Nine-key whole-word grammar scoring and host replay counters; the plugin copy below picks it up.
cyime_apply_patch("${CMAKE_SOURCE_DIR}/librime-octagram" "${CMAKE_CURRENT_LIST_DIR}/OctagramT9.patch")

set(RIME_PLUGINS librime-octagram librime-predict librime-t9)

foreach(plugin ${RIME_PLUGINS})
  file(COPY "${CMAKE_SOURCE_DIR}/${plugin}/"
       DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}")
endforeach()

file(COPY "${CMAKE_SOURCE_DIR}/librime-lua/"
     DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/lua")

if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/lua/thirdparty")
  file(COPY "${CMAKE_SOURCE_DIR}/librime-lua-deps/"
       DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/lua/thirdparty")
endif()

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
option(CYIME_RIME_VERBOSE_TRACE "Trace every native dictionary lookup (slow)" OFF)
if(CYIME_RIME_VERBOSE_TRACE)
  add_compile_definitions(CYIME_RIME_VERBOSE_TRACE=1)
endif()
include("${CMAKE_CURRENT_LIST_DIR}/T9WorkCounters.cmake")
# Every CyIME change to librime; the same file as patches/cyime-librime.patch, which Gradle applies first.
cyime_apply_patch("${CMAKE_SOURCE_DIR}/librime" "${CMAKE_CURRENT_LIST_DIR}/RimeDictionary.patch")
add_subdirectory(librime)
include("${CMAKE_CURRENT_LIST_DIR}/CyimeRimeSources.cmake")
target_compile_options(
  rime-static PRIVATE "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=." "-Wno-error=deprecated-declarations")

target_compile_options(
  rime-lua-objs PRIVATE "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=.")
