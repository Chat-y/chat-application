package com.example.chat

import com.example.chat.ui.media.storageCategoryShare
import org.junit.Assert.*
import org.junit.Test

/** Source-only test cases, NOT RUN. */
class StorageCategoryShareTest {
    @Test fun quarter() { assertEquals(0.25, storageCategoryShare(25, 100)!!, 0.0001) }
    @Test fun emptyMeasuredTotal() { assertEquals(0.0, storageCategoryShare(0, 0)!!, 0.0) }
    @Test fun positiveBytesWithoutTotalAreUnknown() { assertNull(storageCategoryShare(1, 0)) }
    @Test fun negativeMeasurementsAreUnknown() { assertNull(storageCategoryShare(-1, 2)); assertNull(storageCategoryShare(1, -2)) }
    @Test fun staleCategoryAboveTotalDoesNotClaimOver100Percent() { assertEquals(1.0, storageCategoryShare(120, 100)!!, 0.0) }
    @Test fun largeValuesDoNotOverflow() { assertEquals(1.0, storageCategoryShare(Long.MAX_VALUE, Long.MAX_VALUE)!!, 0.0) }
}
