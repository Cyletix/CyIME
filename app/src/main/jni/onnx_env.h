#pragma once
#include <onnxruntime_c_api.h>
#include <stdint.h>

const OrtApi* OnnxGetApi();
OrtEnv* OnnxGetSharedEnv();
void OnnxReleaseSharedEnv();

// Try to enable NNAPI execution provider via dlsym.
// Returns true if NNAPI was enabled, false if not available (CPU fallback).
bool OnnxTryEnableNnapi(OrtSessionOptions* options);

// Append the CPU execution provider as the explicit fallback.
// onnxruntime partitions the graph so nodes NNAPI doesn't support run on CPU.
// Returns true if the CPU EP was appended.
bool OnnxTryEnableCpuFallback(OrtSessionOptions* options);

// Worker threads block between runs instead of spinning. Same thread count and math,
// so results are unchanged; only idle CPU (battery) differs.
void OnnxDisableSpinning(OrtSessionOptions* options);
