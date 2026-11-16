package com.example.chat.ui.settings.conversation

/** Display samples only, not claims about the current account, chat or security implementation. */
internal enum class ConversationInfoSample(val stringId: Int, val testTag: String) {
    Encryption(com.example.chat.R.string.conv_proposal_sample_encryption, "conversation-extra-encryption-preview"),
    SecurityCodeChange(com.example.chat.R.string.conv_proposal_sample_security_code, "conversation-extra-security-code-preview"),
}
