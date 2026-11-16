package com.example.chat.ui.chats.home

/** Session-only scratch text. This is not a saved note or a synced self-chat. */
object HomeScratchpadPolicy {
    const val MAX_CODE_POINTS=4000
    fun bounded(text:String):String {
        val points=text.codePointCount(0,text.length)
        return if(points<=MAX_CODE_POINTS) text else text.substring(0,text.offsetByCodePoints(0,MAX_CODE_POINTS))
    }
}
