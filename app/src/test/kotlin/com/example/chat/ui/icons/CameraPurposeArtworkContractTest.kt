package com.example.chat.ui.icons

import org.junit.Assert.*
import org.junit.Test

class CameraPurposeArtworkContractTest {
    @Test fun allCameraPurposesHaveUniqueNonemptyOriginalArtwork() {
        assertEquals(130, ChatyCameraPurposeArtwork.ids.size)
        assertEquals(130, ChatyCameraPurposeArtwork.shapes.values.toSet().size)
        assertEquals(ChatyCameraPurposeArtwork.ids, ChatyCameraPurposeArtwork.drawables.keys)
        for (id in ChatyCameraPurposeArtwork.ids) {
            assertTrue(ChatyCameraPurposeArtwork.shapes.getValue(id).isNotEmpty())
            assertEquals(id, ChatyMaterialIconRoles.resolve(ChatyGlyphVectors.of(id).name))
            for (theme in ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack)) {
                assertNotNull("$theme/$id", ChatySupplementalIcons.drawable(theme, id))
            }
        }
    }
}
