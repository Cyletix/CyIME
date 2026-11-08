cmake_minimum_required(VERSION 3.18)
project(cytranslate CXX C)

# slimt checkout (pinned commit, see build-android.sh). Not part of the app's own CMake build:
# libcytranslate.so is built once per release of slimt and pulled in prebuilt (tasks-offline-translate.gradle.kts).
set(SLIMT_SOURCE_DIR "" CACHE PATH "slimt source checkout")
option(CYTRANSLATE_CLI "Build the host check program instead of the JNI library" OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Same flags as slimt-sys: reproducible paths, no debug info, unused code dropped.
# -ffp-contract=off keeps ARM and x86 results identical (ARM would otherwise fuse multiply-adds).
set(COMMON_FLAGS "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${SLIMT_SOURCE_DIR}=slimt -Wno-builtin-macro-redefined -ffunction-sections -fdata-sections -fvisibility=hidden -g0 -ffp-contract=off")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -fvisibility-inlines-hidden -Wno-unused-command-line-argument -Wno-nan-infinity-disabled")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--strip-all -Wl,--exclude-libs,ALL")
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
  set(USE_NEON ON CACHE BOOL "" FORCE)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
  # Android x86_64 guarantees SSE4.2; ruy picks AVX2/AVX-512 kernels at run time.
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=x86-64-v2")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=x86-64-v2")
endif()

set(WITH_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_JNI OFF CACHE BOOL "" FORCE)
set(USE_BUILTIN_SENTENCEPIECE ON CACHE BOOL "" FORCE)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
include_directories(${SLIMT_SOURCE_DIR} ${CMAKE_BINARY_DIR}/slimt)
add_subdirectory(${SLIMT_SOURCE_DIR} slimt EXCLUDE_FROM_ALL)

if(CYTRANSLATE_CLI)
  add_executable(cytranslate-cli cli.cc cytranslate.cc)
  target_compile_definitions(cytranslate-cli PRIVATE CYTRANSLATE_NO_JNI)
  target_link_libraries(cytranslate-cli PRIVATE slimt-static)
else()
  add_library(cytranslate SHARED cytranslate.cc)
  target_link_libraries(cytranslate PRIVATE slimt-static)
  if(ANDROID)
    target_link_libraries(cytranslate PRIVATE log)
  endif()
endif()
