package com.example.chat.ui.icons
import org.junit.Assert.assertEquals
import org.junit.Test
class PurposeBridgeIdentityContractTest {
    @Test fun materializedPurposeNamesRoundTripWithoutStockAliases() {
        for (id in ChatyIconId.entries) assertEquals(id, ChatyMaterialIconRoles.resolve("Chaty" + id.name))
        assertEquals(ChatyIconId.errorOutline, ChatyMaterialIconRoles.resolve("Rounded.Error"))
        assertEquals(ChatyIconId.share, ChatyMaterialIconRoles.resolve("AutoMirrored.Rounded.IosShare"))
    }
}
