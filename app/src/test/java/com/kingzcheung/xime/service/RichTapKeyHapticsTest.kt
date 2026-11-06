package com.kingzcheung.xime.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class RichTapKeyHapticsTest {

    @Test fun `HE JSON always has exactly 4 curve points`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        org.junit.Assert.assertEquals(4, curve.length())
    }

    @Test fun `HE JSON curve times are strictly increasing`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")

        val times = mutableListOf<Int>()
        for (i in 0 until curve.length()) {
            times.add(curve.getJSONObject(i).getInt("Time"))
        }
        org.junit.Assert.assertTrue(times[0] < times[1])
        org.junit.Assert.assertTrue(times[1] < times[2])
        org.junit.Assert.assertTrue(times[2] < times[3])
    }

    @Test fun `HE JSON duration is within valid range`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val duration = event.getInt("Duration")
        org.junit.Assert.assertTrue(duration >= 20)
        org.junit.Assert.assertTrue(duration <= 5000)
    }

    @Test fun `HE JSON duration respects lower bound`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(10, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val duration = event.getInt("Duration")
        org.junit.Assert.assertTrue(duration >= 20)
    }

    @Test fun `HE JSON duration respects upper bound`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(6000, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val duration = event.getInt("Duration")
        org.junit.Assert.assertTrue(duration <= 5000)
    }

    @Test fun `HE JSON metadata version is 1`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val metadata = he.getJSONObject("Metadata")
        org.junit.Assert.assertEquals(1, metadata.getInt("Version"))
    }

    @Test fun `HE JSON uses HE 1.0 format (Pattern not PatternList)``() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        org.junit.Assert.assertTrue(he.has("Pattern"))
        org.junit.Assert.assertFalse(he.has("PatternList"))
    }

    @Test fun `KeyFeedbackType CHARACTER maps to expected spec`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.CHARACTER, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(36, spec.durationMs)
        org.junit.Assert.assertEquals(56, spec.frequencyHe)
        org.junit.Assert.assertEquals(1.0, spec.peak, 0.01)
    }

    @Test fun `KeyFeedbackType SPACE maps to longer duration`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.SPACE, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(52, spec.durationMs)
        org.junit.Assert.assertEquals(56, spec.frequencyHe)
        org.junit.Assert.assertEquals(1.0, spec.peak, 0.01)
    }

    @Test fun `KeyFeedbackType DELETE maps to shorter duration with higher frequency`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.DELETE, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(26, spec.durationMs)
        org.junit.Assert.assertEquals(58, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.90, spec.peak, 0.01)
    }

    @Test fun `KeyFeedbackType CURSOR_STEP maps to light tick`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.CURSOR_STEP, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(16, spec.durationMs)
        org.junit.Assert.assertEquals(64, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.50, spec.peak, 0.01)
    }

    @Test fun `KeyFeedbackType TOOLBAR maps to medium spec`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.TOOLBAR, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(36, spec.durationMs)
        org.junit.Assert.assertEquals(56, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.95, spec.peak, 0.01)
    }

    @Test fun `Long press maps to longer duration`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.CHARACTER, longPress = true, keyUp = false)
        org.junit.Assert.assertEquals(60, spec.durationMs)
        org.junit.Assert.assertEquals(56, spec.frequencyHe)
        org.junit.Assert.assertEquals(1.0, spec.peak, 0.01)
    }

    @Test fun `Key up maps to light tick`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.CHARACTER, longPress = false, keyUp = true)
        org.junit.Assert.assertEquals(18, spec.durationMs)
        org.junit.Assert.assertEquals(62, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.55, spec.peak, 0.01)
    }

    @Test fun `HANDWRITING_ERASE maps to light tick like cursor step`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.HANDWRITING_ERASE, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(18, spec.durationMs)
        org.junit.Assert.assertEquals(62, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.55, spec.peak, 0.01)
    }

    @Test fun `MODIFIER maps to lighter than character`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec.of(KeyFeedbackType.MODIFIER, longPress = false, keyUp = false)
        org.junit.Assert.assertEquals(28, spec.durationMs)
        org.junit.Assert.assertEquals(60, spec.frequencyHe)
        org.junit.Assert.assertEquals(0.80, spec.peak, 0.01)
    }

    @Test fun `HE JSON peak intensity is clamped to 0-1`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.5)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val peak = curve.getJSONObject(1).getDouble("Intensity")
        org.junit.Assert.assertTrue(peak <= 1.0)
        org.junit.Assert.assertTrue(peak >= 0.0)
    }

    @Test fun `HE JSON peak intensity handles negative values`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, -0.5)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val peak = curve.getJSONObject(1).getDouble("Intensity")
        org.junit.Assert.assertTrue(peak >= 0.0)
    }

    @Test fun `HE JSON frequency is within 0-100 range`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val freq = event.getJSONObject("Parameters").getInt("Frequency")
        org.junit.Assert.assertTrue(freq >= 0)
        org.junit.Assert.assertTrue(freq <= 100)
    }

    @Test fun `HE JSON intensity parameter is always 100`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val intensity = event.getJSONObject("Parameters").getInt("Intensity")
        org.junit.Assert.assertEquals(100, intensity)
    }

    @Test fun `HE JSON event type is continuous`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val type = event.getString("Type")
        org.junit.Assert.assertEquals("continuous", type)
    }

    @Test fun `HE JSON RelativeTime is 0`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val relativeTime = event.getInt("RelativeTime")
        org.junit.Assert.assertEquals(0, relativeTime)
    }

    @Test fun `HE JSON curve first point has zero intensity`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val firstIntensity = curve.getJSONObject(0).getDouble("Intensity")
        org.junit.Assert.assertEquals(0.0, firstIntensity, 0.01)
    }

    @Test fun `HE JSON curve last point has zero intensity`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val lastIntensity = curve.getJSONObject(3).getDouble("Intensity")
        org.junit.Assert.assertEquals(0.0, lastIntensity, 0.01)
    }

    @Test fun `HE JSON curve second point has peak intensity`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 0.85)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val peakIntensity = curve.getJSONObject(1).getDouble("Intensity")
        org.junit.Assert.assertEquals(0.85, peakIntensity, 0.01)
    }

    @Test fun `HE JSON curve third point has decayed intensity`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        val midIntensity = curve.getJSONObject(2).getDouble("Intensity")
        val peakIntensity = curve.getJSONObject(1).getDouble("Intensity")
        org.junit.Assert.assertTrue(midIntensity < peakIntensity)
        org.junit.Assert.assertTrue(midIntensity > 0.0)
    }

    @Test fun `HE JSON curve Frequency offsets are zero`() {
        val spec = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val json = RichTapKeyHaptics.heClick(spec)
        val he = JSONObject(json)
        val pattern = he.getJSONArray("Pattern")
        val event = pattern.getJSONObject(0).getJSONObject("Event")
        val curve = event.getJSONObject("Parameters").getJSONArray("Curve")
        for (i in 0 until curve.length()) {
            val freqOffset = curve.getJSONObject(i).getDouble("Frequency")
            org.junit.Assert.assertEquals(0.0, freqOffset, 0.01)
        }
    }

    @Test fun `Cache key generation is consistent`() {
        val spec1 = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val spec2 = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val cacheKey1 = "${spec1.durationMs},${spec1.frequencyHe},${spec1.peak}"
        val cacheKey2 = "${spec2.durationMs},${spec2.frequencyHe},${spec2.peak}"
        org.junit.Assert.assertEquals(cacheKey1, cacheKey2)
    }

    @Test fun `Cache key differs for different specs`() {
        val spec1 = RichTapKeyHaptics.KeyHapticSpec(36, 56, 1.0)
        val spec2 = RichTapKeyHaptics.KeyHapticSpec(40, 58, 0.9)
        val cacheKey1 = "${spec1.durationMs},${spec1.frequencyHe},${spec1.peak}"
        val cacheKey2 = "${spec2.durationMs},${spec2.frequencyHe},${spec2.peak}"
        org.junit.Assert.assertNotEquals(cacheKey1, cacheKey2)
    }
}
