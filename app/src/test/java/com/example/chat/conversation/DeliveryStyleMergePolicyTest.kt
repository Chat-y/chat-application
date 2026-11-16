package com.example.chat.conversation

import com.example.chat.core.theme.*
import org.junit.Assert.*
import org.junit.Test

/** Source declarations only. Not executed in this patch-only pass. */
class DeliveryStyleMergePolicyTest {
    @Test fun all95OldIdsMapToKeptStyle() {
        assertEquals(95, DeliveryIconStyle.entries.size)
        DeliveryIconStyle.entries.forEach { assertTrue(canonicalDeliveryStyle(it) in uniqueDeliveryStyles) }
    }
    @Test fun keptChoicesAreUniqueAndCanonical() {
        assertEquals(19, uniqueDeliveryStyles.size)
        assertEquals(19, uniqueDeliveryStyles.toSet().size)
        uniqueDeliveryStyles.forEach { assertEquals(it, canonicalDeliveryStyle(it)) }
    }
    @Test fun labelsAreUnique() = assertEquals(19, uniqueDeliveryStyles.map(::deliveryStyleLabel).toSet().size)
    @Test fun priorTinyCheckVariantsMergeToStock() {
        listOf(DeliveryIconStyle.rcIos11, DeliveryIconStyle.bbmV2, DeliveryIconStyle.bwTicks).forEach {
            assertEquals(DeliveryIconStyle.stock, canonicalDeliveryStyle(it))
        }
    }
    @Test fun broadBadgeVariantsMergeTogether() {
        listOf(DeliveryIconStyle.cirCheck, DeliveryIconStyle.gabIflo, DeliveryIconStyle.gabFace,
            DeliveryIconStyle.gabCircle, DeliveryIconStyle.circlePrint).forEach {
            assertEquals(DeliveryIconStyle.sticker, canonicalDeliveryStyle(it))
        }
    }
}
