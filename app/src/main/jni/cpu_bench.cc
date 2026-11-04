// CyIME CPU 测速：一段固定计算，量一遍要多少纳秒。只在设置页、推荐配置页里、键盘没显示时跑（见
// CpuBenchmark.kt），按键回调里从不调用。
//
// 一遍 = 整数部分 + 浮点部分，各自模仿输入法里真正吃 CPU 的两类活：
// - 整数：在 256 KB 的随机链表上跳，每步做一次散列。像 librime 查词典、组词时的随机访存和分支。
// - 浮点：64x64 矩阵乘若干次。像 ONNX 语音、联想模型里的矩阵运算（-O2 下 clang 自动用 NEON/SSE）。
// 数据在计时前准备好，计时只算计算本身。改了这里的任何计算量，CpuBenchmark.VERSION 必须加一，旧结果作废。
#include <jni.h>

#include <sched.h>

#include <chrono>
#include <cstdint>
#include <thread>
#include <vector>

namespace {

constexpr uint32_t kChainNodes = 64 * 1024;  // 64K 个 uint32 = 256 KB
constexpr uint32_t kChainSteps = 2'000'000;
constexpr int kMatrix = 64;
constexpr int kMatrixRounds = 400;

uint32_t XorShift(uint32_t& state) {
    state ^= state << 13;
    state ^= state >> 17;
    state ^= state << 5;
    return state;
}

struct Work {
    std::vector<uint32_t> chain;
    std::vector<float> a, b, c;
    uint64_t cpus = 0;  // 跑的时候待过哪几个核（位图，前 64 核）

    Work() : chain(kChainNodes), a(kMatrix * kMatrix), b(kMatrix * kMatrix), c(kMatrix * kMatrix) {
        // 固定种子打乱成一个大环（Sattolo），每个节点恰好走到一次，跳的顺序和设备无关。
        for (uint32_t i = 0; i < kChainNodes; ++i) chain[i] = i;
        uint32_t seed = 0x9E3779B9u;
        for (uint32_t i = kChainNodes - 1; i > 0; --i) {
            uint32_t j = XorShift(seed) % i;
            std::swap(chain[i], chain[j]);
        }
        for (int i = 0; i < kMatrix * kMatrix; ++i) {
            a[i] = static_cast<float>((i * 7) % 13) / 13.0f - 0.5f;
            b[i] = static_cast<float>((i * 5) % 11) / 11.0f - 0.5f;
        }
    }

    void Mark() {
        int cpu = sched_getcpu();
        if (cpu >= 0 && cpu < 64) cpus |= uint64_t{1} << cpu;
    }

    uint64_t Run() {
        Mark();
        uint32_t node = 0, hash = 2166136261u;
        for (uint32_t step = 0; step < kChainSteps; ++step) {
            node = chain[node];
            hash = (hash ^ node) * 16777619u;
            if (hash & 1u) hash += node >> 3;
        }
        Mark();
        for (int round = 0; round < kMatrixRounds; ++round) {
            for (int i = 0; i < kMatrix * kMatrix; ++i) c[i] = 0.0f;
            for (int i = 0; i < kMatrix; ++i) {
                for (int k = 0; k < kMatrix; ++k) {
                    const float aik = a[i * kMatrix + k];
                    const float* brow = &b[k * kMatrix];
                    float* crow = &c[i * kMatrix];
                    for (int j = 0; j < kMatrix; ++j) crow[j] += aik * brow[j];
                }
            }
            // 结果喂回 a 的一个元素，编译器不能把多轮合成一轮。
            a[round % (kMatrix * kMatrix)] = c[(round * 31) % (kMatrix * kMatrix)] * 0.5f;
        }
        Mark();
        uint32_t bits = 0;
        static_assert(sizeof(float) == sizeof(uint32_t));
        __builtin_memcpy(&bits, &c[kMatrix + 1], sizeof bits);
        return (uint64_t{hash} << 32) ^ bits;
    }
};

volatile uint64_t g_sink;

}  // namespace

// threads = 1 或 2：同时跑几份（各用自己的数据）。返回从开始到全部跑完的纳秒数；
// out[0] = 待过的核的位图（几份合起来）。Work 的准备不计时。
extern "C" JNIEXPORT jlong JNICALL
Java_com_kingzcheung_xime_runtime_adaptation_CpuBenchmark_nativeRun(JNIEnv* env, jclass, jint threads, jlongArray out) {
    if (threads < 1 || threads > 2) return -1;
    std::vector<Work> works(threads);
    uint64_t sinks[2] = {0, 0};
    auto start = std::chrono::steady_clock::now();
    if (threads == 1) {
        sinks[0] = works[0].Run();
    } else {
        std::thread second([&] { sinks[1] = works[1].Run(); });
        sinks[0] = works[0].Run();
        second.join();
    }
    auto elapsed = std::chrono::steady_clock::now() - start;
    g_sink = sinks[0] ^ sinks[1];
    uint64_t cpus = 0;
    for (auto& work : works) cpus |= work.cpus;
    if (out != nullptr && env->GetArrayLength(out) > 0) {
        jlong mask = static_cast<jlong>(cpus);
        env->SetLongArrayRegion(out, 0, 1, &mask);
    }
    return static_cast<jlong>(std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count());
}
