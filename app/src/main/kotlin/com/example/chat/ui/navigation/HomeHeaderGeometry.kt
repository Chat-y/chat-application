package com.example.chat.ui.navigation

/** Pure reservation/scroll math. Every extent is measured for the current configuration. */
internal object HomeHeaderGeometry {
    fun collapsed(previous: Float, deltaY: Float, identityExtent: Float): Float =
        (previous - deltaY).coerceIn(0f, identityExtent.coerceAtLeast(0f))
    fun actions(normal: Int, selection: Int, root: Int): Int = maxOf(normal, selection, root)
    fun actions(normal: Float, selection: Float, root: Float): Float = maxOf(normal, selection, root)
    fun height(identity: Int, collapsed: Float, banner: Int, actions: Int, search: Int): Int =
        identity - collapsed.coerceIn(0f, identity.toFloat()).toInt() + banner + actions + search
}
