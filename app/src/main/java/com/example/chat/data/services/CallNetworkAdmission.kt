package com.example.chat.data.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Fresh passive OS snapshot. Validated internet is necessary here, never a quality guarantee. */
object CallNetworkAdmission {
    fun online(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)
}
