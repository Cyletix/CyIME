# Appended to the production CMake prefix in an isolated source snapshot.
# Keep the same Rime patches, plugin source, candidate policy and JNI entrypoints.
set(ONNXRUNTIME_DIR "${HOST_ORT_DIR}")
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
  IMPORTED_LOCATION "${HOST_ORT_LIBRARY}"
  INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_SOURCE_DIR}/onnxruntime/include")
target_link_libraries(rime-static "$<LINK_ONLY:onnxruntime>")
target_include_directories(rime-t9-objs PRIVATE "${CMAKE_SOURCE_DIR}/onnxruntime/include")
add_library(rime_jni SHARED librime_jni/rime_jni.cc librime_jni/candidate_policy.cc)
target_compile_definitions(rime_jni PRIVATE RIME_JNI_VERBOSE_LOGGING=0)
target_include_directories(rime_jni PRIVATE
  "${HOST_JNI_INCLUDE}" "${HOST_JNI_INCLUDE}/linux" "${HOST_ADAPTER_DIR}"
  "${CMAKE_SOURCE_DIR}/librime/include" "${CMAKE_SOURCE_DIR}/librime/src"
  "${CMAKE_BINARY_DIR}/librime/src" "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_link_libraries(rime_jni PRIVATE -Wl,--whole-archive rime-static -Wl,--no-whole-archive onnxruntime dl pthread)
set_target_properties(rime_jni PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
  BUILD_RPATH "${HOST_ORT_DIR}" INSTALL_RPATH "$ORIGIN")
