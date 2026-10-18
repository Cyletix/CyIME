package com.kingzcheung.xime.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrScanGeometryTest {
    @Test fun sameAspectRatioExcludesTextAboveAndBelowTheGuide() {
        assertEquals(OcrCrop(50, 180, 900, 140), OcrScanGeometry.crop(1000, 500, 1000, 500))
    }
    @Test fun tallSensorAndWideKeyboardUseVisibleCenterCrop() {
        assertEquals(OcrCrop(54, 884, 972, 152), OcrScanGeometry.crop(1080, 1920, 1000, 500))
    }
    @Test fun wideSensorAndNarrowFloatingKeyboardUseVisibleCenterCrop() {
        assertEquals(OcrCrop(717, 389, 486, 302), OcrScanGeometry.crop(1920, 1080, 250, 500))
    }
    @Test fun smallFramesStillHaveANonEmptyBoundedCrop() {
        assertEquals(OcrCrop(0, 0, 1, 1), OcrScanGeometry.crop(1, 1, 1, 1))
    }
}
