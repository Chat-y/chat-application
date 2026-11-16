package com.example.chat.ui.chats.detail

internal const val CONVERSATION_PER_CHAT_COLORS = "conv_per_chat_colors_v1"
internal data class ConversationPerChatColors(
    val enabled: Boolean = false,val header: Long? = null,val link: Long? = null,val name: Long? = null,
) {
    fun effective(): ConversationPerChatColors = if (enabled) this else ConversationPerChatColors()
    fun applyTo(base: BubbleColorOverrides): BubbleColorOverrides = effective().let { own -> base.copy(
        hyperlinksColor = own.link ?: base.hyperlinksColor,
        groupParticipantNameColor = own.name ?: base.groupParticipantNameColor,
        varyCustomParticipantNames = if (own.name != null) false else base.varyCustomParticipantNames,
    ) }
    fun toMap(): Map<String,Any?> = mapOf("enabled" to enabled,"header" to header,"link" to link,"name" to name)
    companion object {
        fun fromMap(values: Map<String,Any?>,accountChatKey: String?): ConversationPerChatColors {
            if (accountChatKey.isNullOrBlank()) return ConversationPerChatColors()
            val all = values[CONVERSATION_PER_CHAT_COLORS] as? Map<*,*> ?: return ConversationPerChatColors()
            val own = all[accountChatKey] as? Map<*,*> ?: return ConversationPerChatColors()
            fun color(key: String): Long? {
                val v=(own[key] as? Number)?.toDouble() ?: return null
                if (!v.isFinite() || v % 1.0 != 0.0 || v < Int.MIN_VALUE.toDouble() || v > UInt.MAX_VALUE.toDouble()) return null
                return com.example.chat.ui.kit.opaqueConversationArgb(v.toLong())
            }
            return ConversationPerChatColors(own["enabled"] as? Boolean ?: false,color("header"),color("link"),color("name"))
        }
    }
}
/** Preserve unrelated chats/unknown future fields. Caller guards current account before persisting. */
internal fun updatedConversationPerChatColors(values: Map<String,Any?>,accountChatKey: String,state: ConversationPerChatColors): Map<String,Any?> {
    val root=(values[CONVERSATION_PER_CHAT_COLORS] as? Map<*,*>)?.entries?.mapNotNull { (key,value)->(key as? String)?.let { it to value } }?.toMap() ?: emptyMap()
    val own=(root[accountChatKey] as? Map<*,*>)?.entries?.mapNotNull { (key,value)->(key as? String)?.let { it to value } }?.toMap() ?: emptyMap()
    return root + (accountChatKey to (own + state.toMap()))
}
