package com.example.chat.ui.chats.detail
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test
class DeliveryIconGeometryPolicyTest {
    @Test fun referenceSizeKeepsExistingDimensions() { assertEquals(1f,deliveryGeometryScale(15f,15f),0f) }
    @Test fun largerCanvasScalesExactlyOnce() { assertEquals(2f,deliveryGeometryScale(30f,30f),0f);assertEquals(3f,deliveryGeometryScale(45f,45f),0f) }
    @Test fun nonSquareCanvasUsesSmallerDimension() { assertEquals(2f,deliveryGeometryScale(45f,30f),0f) }
    @Test fun invalidDimensionsNeverProduceNegativeOrInfiniteWidths() { assertEquals(0f,deliveryGeometryScale(-1f,15f),0f);assertEquals(0f,deliveryGeometryScale(Float.NaN,15f),0f) }
    @Test fun batVerticesStayInsideNormalizedBounds() { assertEquals(12,deliveryBatmanVertices().size);assertTrue(deliveryBatmanVertices().all {it.first in 0f..1f && it.second in 0f..1f}) }
    @Test fun rightWingMirrorsExistingLeftWing() { val v=deliveryBatmanVertices();(1..5).forEach {i->assertEquals(1f-v[i].first,v[12-i].first,.00001f);assertEquals(v[i].second,v[12-i].second,0f)} }
    @Test fun insetUsesReadableExistingTokenForEveryShippedAccent() { ThemePresets.all.forEach {t->val fill=t.accentColor;val inset=deliveryFilledForeground(fill);assertTrue(inset in listOf(ThemePresets.monochromeLight.accentColor,ThemePresets.monochromeDark.accentColor));assertTrue(t.id,ColorPickerLogic.contrastRatio(inset,fill)>=4.5)} }
    @Test fun lightAndDarkFillsDoNotAlwaysGetWhite() { val dark=ThemePresets.monochromeLight.accentColor;val light=ThemePresets.monochromeDark.accentColor;assertEquals(dark,deliveryFilledForeground(light));assertEquals(light,deliveryFilledForeground(dark)) }
}
