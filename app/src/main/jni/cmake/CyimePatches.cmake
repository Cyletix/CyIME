# Apply a tracked patch to a source directory once: skipped when it is already applied, a build error
# when it no longer fits. Gradle's applyCyimeSourcePatches does the same for patches/ before CMake runs.
find_package(Git REQUIRED)
function(cyime_apply_patch directory patch)
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${patch}")
  execute_process(COMMAND "${GIT_EXECUTABLE}" apply ${ARGN} --reverse --check "${patch}"
      WORKING_DIRECTORY "${directory}" RESULT_VARIABLE applied OUTPUT_QUIET ERROR_QUIET)
  if(applied EQUAL 0)
    return()
  endif()
  execute_process(COMMAND "${GIT_EXECUTABLE}" apply ${ARGN} "${patch}"
      WORKING_DIRECTORY "${directory}" RESULT_VARIABLE result ERROR_VARIABLE error)
  if(NOT result EQUAL 0)
    message(FATAL_ERROR "${patch} does not apply to ${directory}: ${error}")
  endif()
endfunction()
