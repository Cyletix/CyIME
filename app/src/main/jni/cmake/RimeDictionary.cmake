# Keep Japanese completion and T9 dictionary search changes reproducible in fresh submodule checkouts.
find_package(Git REQUIRED)
set(RIME_DICTIONARY_PATCH "${CMAKE_CURRENT_LIST_DIR}/RimeDictionary.patch")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${RIME_DICTIONARY_PATCH}")
execute_process(COMMAND "${GIT_EXECUTABLE}" apply --reverse --check "${RIME_DICTIONARY_PATCH}"
    WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/librime" RESULT_VARIABLE already_applied OUTPUT_QUIET ERROR_QUIET)
if(NOT already_applied EQUAL 0)
    execute_process(COMMAND "${GIT_EXECUTABLE}" apply --check "${RIME_DICTIONARY_PATCH}"
        WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/librime" RESULT_VARIABLE applicable ERROR_VARIABLE patch_error)
    if(NOT applicable EQUAL 0)
        message(FATAL_ERROR "Rime dictionary patch does not match librime: ${patch_error}")
    endif()
    execute_process(COMMAND "${GIT_EXECUTABLE}" apply "${RIME_DICTIONARY_PATCH}"
        WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/librime" RESULT_VARIABLE applied)
    if(NOT applied EQUAL 0)
        message(FATAL_ERROR "Failed to apply Rime dictionary patch")
    endif()
endif()
