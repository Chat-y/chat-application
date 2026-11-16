package com.example.chat

import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN after the no-build instruction. */
class EditorAdjustmentPolicyTest {
    @Test fun defaultsAreIdentityAndPreserveAlpha() {
        val identity = floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)
        assertArrayEquals(identity, buildEditorColorValues(0f, 1f), 0.00001f)
    }
    @Test fun zeroSaturationHasEqualLuminanceRows() {
        val matrix = buildEditorColorValues(0f, 1f, saturation = 0f)
        assertArrayEquals(matrix.copyOfRange(0,5), matrix.copyOfRange(5,10), 0.00001f)
        assertArrayEquals(matrix.copyOfRange(0,5), matrix.copyOfRange(10,15), 0.00001f)
    }
    @Test fun warmthAndTintChangeColorChannelsButNeverAlpha() {
        val matrix = buildEditorColorValues(0f, 1f, warmth = .5f, tint = .5f)
        assertTrue(matrix[0] > 1f); assertTrue(matrix[12] < 1f); assertTrue(matrix[6] > 1f)
        assertArrayEquals(floatArrayOf(0f,0f,0f,1f,0f), matrix.copyOfRange(15,20), 0f)
    }
    @Test fun fadeIntroducesWhiteOffsetAndAllInputsStayFinite() {
        val matrix = buildEditorColorValues(0f,1f,fade=.5f)
        assertEquals(.5f,matrix[0],.00001f); assertEquals(127.5f,matrix[4],.00001f)
        val invalid = buildEditorColorValues(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN,Float.NaN,Float.NaN,Float.NaN)
        assertTrue(invalid.all { it.isFinite() })
        assertArrayEquals(buildEditorColorValues(0f,1f),invalid,.00001f)
    }
}
