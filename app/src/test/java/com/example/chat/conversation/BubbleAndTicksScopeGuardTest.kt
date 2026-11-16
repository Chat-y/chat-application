package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Authored regression sources only; not executed. */
class BubbleAndTicksScopeGuardTest {
    private fun theme() = ThemePresets.paper.let { BubblePreviewTheme(it,Color(it.surfaceColor),Color(it.cardColor)) }
    @Test fun staleModelCannotReadOrWriteTheNextAccount() {
        var generation=1L;var reads=0;var writes=0
        val model=BubbleAndTicksViewModel({reads++;ConversationPreferences()},{writes++},{},{},theme(),scopeIsCurrent={generation==1L})
        val previousReads=reads;generation=2L
        model.edit {it.copy(rightBubbleColor=22L)}
        model.setTheme(theme())
        assertEquals(previousReads,reads);assertEquals(0,writes)
    }
    @Test fun retainedListenerCannotPublishNextAccountPreferences() {
        var current=true;var latest=ConversationPreferences(rightBubbleColor=11L);var listener:(()->Unit)?=null
        val model=BubbleAndTicksViewModel({latest},{},{listener=it},{},theme(),scopeIsCurrent={current})
        current=false;latest=latest.copy(rightBubbleColor=22L);listener!!.invoke()
        assertEquals(11L,model.uiState.value.preferences.rightBubbleColor)
    }
    @Test fun deferredTransactionRechecksGenerationWhenApplied() {
        var current=true;var pending:((ConversationPreferences)->ConversationPreferences)?=null
        val model=BubbleAndTicksViewModel({ConversationPreferences()},{},{},{},theme(),transaction={pending=it},scopeIsCurrent={current})
        model.edit {it.copy(rightBubbleColor=11L)}
        current=false;val nextAccount=ConversationPreferences(rightBubbleColor=22L)
        assertSame(nextAccount,pending!!.invoke(nextAccount))
    }
    @Test fun currentAccountEditsStillUseTheFreshSnapshot() {
        var current=true;var latest=ConversationPreferences()
        val model=BubbleAndTicksViewModel({latest},{latest=it},{},{},theme(),scopeIsCurrent={current})
        latest=latest.copy(hyperlinksColor=99L);model.edit {it.copy(rightBubbleColor=11L)}
        assertEquals(99L,latest.hyperlinksColor);assertEquals(11L,latest.rightBubbleColor)
    }
}
