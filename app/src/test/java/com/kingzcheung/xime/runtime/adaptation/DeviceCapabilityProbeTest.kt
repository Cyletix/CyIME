package com.kingzcheung.xime.runtime.adaptation

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceCapabilityProbeTest {
    @get:Rule val files = TemporaryFolder()
    private fun frequency(path: String, value: String) {
        File(files.root, "$path/cpuinfo_max_freq").apply { requireNotNull(parentFile).mkdirs(); writeText(value) }
    }

    @Test fun policyMaximumIncludesBigCoresAndIgnoresCurrentFrequency() {
        frequency("cpufreq/policy0", "1800000\n")
        frequency("cpufreq/policy4", "2800000\n")
        frequency("cpu0/cpufreq", "1800000")
        File(files.root, "cpufreq/policy4/scaling_cur_freq").writeText("300000")
        assertEquals(2_800_000L, DeviceCapabilityProbe.readMaxCpuKHz(files.root))
    }

    @Test fun kernelsWithoutPoliciesCanUseCompletePerCoreInformation() {
        frequency("cpu0/cpufreq", " 1800000 ")
        frequency("cpu1/cpufreq", "2200000")
        assertEquals(2_200_000L, DeviceCapabilityProbe.readMaxCpuKHz(files.root))
    }

    @Test fun missingOrPartialOrInvalidFilesRemainUnknown() {
        assertNull(DeviceCapabilityProbe.readMaxCpuKHz(files.root))
        frequency("cpufreq/policy0", "1800000")
        File(files.root, "cpufreq/policy4").mkdirs()
        assertNull(DeviceCapabilityProbe.readMaxCpuKHz(files.root))
        for (value in listOf("", "unavailable", "0", "-1", "9999999999999999999999")) {
            frequency("cpufreq/policy4", value)
            assertNull(DeviceCapabilityProbe.readMaxCpuKHz(files.root))
        }
    }
}
