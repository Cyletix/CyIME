"""Experimental bounded, causal stationary-noise suppression for offline replay.

Not production code and not a trained denoiser. Uses only past/current 32 ms
frames, with <=32 ms overlap/add latency; no oracle noise-only recording or VAD.
"""
from collections import deque

import numpy as np


def suppress_stationary_noise(pcm, sample_rate=16000):
    if sample_rate != 16000:
        raise ValueError('This experiment is defined for 16 kHz PCM')
    size, hop = 512, 128
    # Power smoothing suppresses periodogram variance before taking a rolling
    # minimum. A one-second history does not assume silence at session start.
    history_frames = sample_rate // hop
    window = np.sqrt(np.hanning(size + 1)[:-1])
    padded = np.pad(np.asarray(pcm, dtype=np.float64), (size, size))
    output = np.zeros_like(padded)
    weights = np.zeros_like(padded)
    history = deque(maxlen=history_frames)
    smooth_power = np.zeros(size // 2 + 1)
    previous_gain = np.ones(size // 2 + 1)
    gains = []
    for offset in range(0, len(padded) - size + 1, hop):
        spectrum = np.fft.rfft(padded[offset:offset + size] * window)
        power = np.abs(spectrum) ** 2
        smooth_power = .8 * smooth_power + .2 * power
        history.append(smooth_power.copy())
        if len(history) < history_frames:
            gain = np.ones_like(power)
        else:
            # Bias allowance is deliberately small: under-reduction is preferable
            # to deleting weak speech. Never attenuate any bin by over 12 dB.
            noise = 1.2 * np.min(np.asarray(history), axis=0)
            snr_gain = np.maximum(.25, 1 - noise / np.maximum(smooth_power, 1e-12))
            # Neighboring bins and causal time smoothing reduce isolated musical
            # artifacts. Silence stays exactly zero throughout overlap/add.
            target = np.convolve(np.pad(snr_gain, (1, 1), mode='edge'), [.2, .6, .2], mode='valid')
            gain = .6 * previous_gain + .4 * target
        previous_gain = gain
        gains.append(float(np.mean(gain)))
        frame = np.fft.irfft(spectrum * gain, n=size) * window
        output[offset:offset + size] += frame
        weights[offset:offset + size] += window ** 2
    valid = weights > 1e-12
    output[valid] /= weights[valid]
    return output[size:size + len(pcm)].astype(np.float32), dict(
        method='bounded-causal-minimum-spectrum-wiener', fft_samples=size,
        hop_samples=hop, history_seconds=history_frames * hop / sample_rate,
        maximum_algorithmic_latency_ms=size / sample_rate * 1000,
        minimum_amplitude_gain=.25, gain_mean_range=[min(gains), max(gains)],
        uses_oracle_noise=False, uses_additional_model=False)
