package com.example.chat.ui.kit

import org.junit.Assert.*
import org.junit.Test

/** System duration-scale decision source cases. No device setting mutations. Unrun. */
class SystemAnimationPolicyTest {
    @Test fun zeroSkipsMotion() { assertFalse(systemAnimationsAllowed(0f)) }
    @Test fun ordinaryScaleAllowsMotion() { assertTrue(systemAnimationsAllowed(1f)) }
    @Test fun slowScaleStillAllowsMotion() { assertTrue(systemAnimationsAllowed(10f)) }
    @Test fun negativeScaleSkipsMotion() { assertFalse(systemAnimationsAllowed(-1f)) }
    @Test fun invalidScaleSkipsMotion() { assertFalse(systemAnimationsAllowed(Float.NaN)); assertFalse(systemAnimationsAllowed(Float.POSITIVE_INFINITY)) }
}
