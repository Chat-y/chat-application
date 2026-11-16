package com.example.chat.ui.settings.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** SET-R4-005: pins SettingsSearchLogic.filter (empty query, case folding, keyword leg). */
class SettingsSearchLogicTest {
    private data class Item(val title: String, val category: String, val description: String, val keywords: List<String> = emptyList())

    private val items = listOf(
        Item("Chaty App Lock", "Security", "Biometric and PIN", listOf("lock", "fingerprint")),
        Item("Chaty Themes", "Themes", "Dark and light presets", listOf("colour")),
    )

    private fun run(q: String) = SettingsSearchLogic.filter(q, items, { it.title }, { it.category }, { it.description }, { it.keywords })

    @Test fun emptyQueryReturnsEverything() = assertEquals(items, run("   "))
    @Test fun titleMatchIsCaseInsensitive() = assertEquals(listOf(items[0]), run("APP LOCK"))
    @Test fun categoryMatches() = assertEquals(listOf(items[1]), run("themes"))
    @Test fun descriptionMatches() = assertEquals(listOf(items[0]), run("biometric"))
    @Test fun keywordMatches() = assertEquals(listOf(items[1]), run("COLOUR"))
    @Test fun noMatchReturnsEmpty() = assertEquals(emptyList<Item>(), run("zzz"))
}
