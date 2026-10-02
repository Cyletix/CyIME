#pragma once
// Host-only Android logging adapter. No decoding or candidate behavior lives here.
#include <stdarg.h>
#include <stdio.h>
enum { ANDROID_LOG_DEBUG = 3, ANDROID_LOG_INFO = 4, ANDROID_LOG_ERROR = 6 };
static inline int __android_log_print(int, const char* tag, const char* format, ...) {
  fprintf(stderr, "%s: ", tag);
  va_list arguments;
  va_start(arguments, format);
  int result = vfprintf(stderr, format, arguments);
  va_end(arguments);
  fputc('\n', stderr);
  return result;
}
