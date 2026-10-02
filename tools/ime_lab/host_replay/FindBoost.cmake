# The production prefix already defines the pinned Boost 1.89 targets. Avoid
# accidentally substituting the host distribution's older Boost headers/library.
if(NOT TARGET Boost::regex)
  message(FATAL_ERROR "The production vendored Boost::regex target is missing")
endif()
set(Boost_FOUND TRUE)
set(Boost_VERSION 1.89.0)
set(Boost_INCLUDE_DIRS "")
set(Boost_LIBRARIES Boost::regex)
