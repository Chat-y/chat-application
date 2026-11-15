package com.example.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.ChatyTheme

/**
 * Shared fixtures for the Bandi-approved login/register test lanes (Nov 15).
 * Instrumentation-only rendering checks against isolated screens hosted in
 * ChatFixtureActivity: no backend, no account, no messages leave the device.
 */
internal typealias AuthComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ChatFixtureActivity>, ChatFixtureActivity>

internal fun AuthComposeRule.setThemedContent(
    theme: ThemeConfig,
    fontScale: Float = 1f,
    localeTag: String = "en",
    content: @Composable () -> Unit,
) {
    activityRule.scenario.onActivity { activity ->
        activity.setContent {
            val density = LocalDensity.current
            val original=androidx.compose.ui.platform.LocalContext.current
            val configuration=android.content.res.Configuration(original.resources.configuration).apply {setLocale(java.util.Locale.forLanguageTag(localeTag))}
            val localized=original.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), androidx.compose.ui.platform.LocalContext provides localized, androidx.compose.ui.platform.LocalConfiguration provides configuration, androidx.compose.ui.platform.LocalLayoutDirection provides if(localeTag in listOf("ar","he")) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                ChatyTheme(theme) { content() }
            }
        }
    }
    waitForIdle()
}

internal val AuthComposeRule.rootRect: Rect
    get() = onRootRect()

private fun AuthComposeRule.onRootRect(): Rect =
    onAllNodes(isRootNode())[0].fetchSemanticsNode().boundsInRoot

private fun isRootNode() = SemanticsMatcher("root") { it.isRoot }

private val SemanticsNode.isRoot: Boolean get() = parent == null

internal data class InteractiveNodeInfo(
    val label: String,
    val bounds: Rect,
    val actions: List<String>,
)

/** Visible, merged-tree interactive nodes (click/select/toggle/set-text). */
internal fun AuthComposeRule.interactiveNodes(): List<InteractiveNodeInfo> {
    val root = rootRect
    val out = ArrayList<InteractiveNodeInfo>()
    fun visit(node: SemanticsNode) {
        val actions = ArrayList<String>()
        val config = node.config
        if (config.contains(SemanticsActions.OnClick)) actions.add("OnClick")
        if (config.contains(SemanticsActions.SetText)) actions.add("SetText")
        if (config.contains(SemanticsProperties.Selected)) actions.add("Select")
        if (config.contains(SemanticsProperties.ToggleableState)) actions.add("Toggle")
        val bounds = node.boundsInRoot
        val visible = bounds.overlaps(root) && bounds.width > 0f && bounds.height > 0f
        if (actions.isNotEmpty() && visible) {
            val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            out.add(InteractiveNodeInfo(desc ?: text ?: "<unlabeled>", bounds, actions))
        }
        node.children.forEach(::visit)
    }
    // Merged tree is what TalkBack walks; collect from every root (dialogs too).
    onAllNodes(isRootNode(), useUnmergedTree = false).fetchSemanticsNodes().forEach(::visit)
    // A parent and child can share identical bounds after merging; dedupe.
    return out.distinctBy { Triple(it.label, it.bounds, it.actions) }
}

/** WCAG-style minimum 48dp target audit over the interactive nodes. */
internal fun AuthComposeRule.assertMinTouchTargets48dp(screenName: String) {
    val minPx = with(density) { 48.dp.toPx() } - 1f // 1px anti-alias tolerance
    val violations = interactiveNodes()
        .filter { it.bounds.width < minPx || it.bounds.height < minPx }
        .map { info ->
            "%s %.0fx%.0f dp".format(
                info.label,
                info.bounds.width / density.density,
                info.bounds.height / density.density,
            )
        }
    org.junit.Assert.assertTrue("$screenName: interactive targets under 48dp: $violations", violations.isEmpty())
}

/** Voice Access: every clickable control must expose a speakable label. */
internal fun AuthComposeRule.assertAllClickablesLabeled(screenName: String) {
    val unlabeled = interactiveNodes()
        .filter { "OnClick" in it.actions }
        .filter { it.label == "<unlabeled>" || it.label.isBlank() }
        .map { it.bounds.toString() }
    org.junit.Assert.assertTrue("$screenName: clickable nodes with no label at $unlabeled", unlabeled.isEmpty())
}

internal fun hasHeading(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

internal fun liveRegionPolite(): SemanticsMatcher =
    SemanticsMatcher("liveRegion Polite") {
        it.config.getOrNull(SemanticsProperties.LiveRegion) ==
            androidx.compose.ui.semantics.LiveRegionMode.Polite
    }

internal fun hasNoSetText(): SemanticsMatcher =
    SemanticsMatcher("read-only (no SetText action)") { !it.config.contains(SemanticsActions.SetText) }
