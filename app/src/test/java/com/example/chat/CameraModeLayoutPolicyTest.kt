package com.example.chat

import com.example.chat.ui.camera.cameraModeSlotWidthDp
import com.example.chat.ui.camera.cameraControlsTight
import com.example.chat.ui.camera.cameraFlipActionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only, NOT RUN. */
class CameraModeLayoutPolicyTest {
    @Test fun defaultTight() { assertEquals(76f, cameraModeSlotWidthDp(true, 1f), 0f) }
    @Test fun defaultRegular() { assertEquals(88f, cameraModeSlotWidthDp(false, 1f), 0f) }
    @Test fun doubleTight() { assertEquals(152f, cameraModeSlotWidthDp(true, 2f), 0f) }
    @Test fun doubleRegular() { assertEquals(176f, cameraModeSlotWidthDp(false, 2f), 0f) }
    @Test fun smallerTextDoesNotShrinkTargets() { assertEquals(76f, cameraModeSlotWidthDp(true, .8f), 0f) }
    @Test fun intermediateScale() { assertEquals(132f, cameraModeSlotWidthDp(false, 1.5f), 0f) }
    // CAM-R1-058 breakpoint cases, source only NOT RUN.
    @Test fun narrowShortSideIsTight() { assertEquals(true, cameraControlsTight(339, 840)) }
    @Test fun exactBoundariesAreRegular() { assertEquals(false, cameraControlsTight(340, 680)) }
    @Test fun shortPortraitIsTight() { assertEquals(true, cameraControlsTight(400, 679)) }
    @Test fun normalPortraitIsRegular() { assertEquals(false, cameraControlsTight(400, 840)) }
    @Test fun shortLandscapeIsTight() { assertEquals(true, cameraControlsTight(840, 360)) }
    @Test fun normalTabletIsRegular() { assertEquals(false, cameraControlsTight(840, 1024)) }
    @Test fun backCameraOffersFront() { assertEquals("Switch to front camera", cameraFlipActionLabel(false)) }
    @Test fun frontCameraOffersBack() { assertEquals("Switch to back camera", cameraFlipActionLabel(true)) }
}
