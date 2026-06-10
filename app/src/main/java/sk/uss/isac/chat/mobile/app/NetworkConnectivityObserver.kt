package sk.uss.isac.chat.mobile.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkConnectivityObserver {
    val isConnected: StateFlow<Boolean>
}

object AlwaysConnectedNetworkConnectivityObserver : NetworkConnectivityObserver {
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

class AndroidNetworkConnectivityObserver(
    context: Context
) : NetworkConnectivityObserver {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val _isConnected = MutableStateFlow(readConnectivitySnapshot())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            _isConnected.value = readConnectivitySnapshot()
        }

        override fun onUnavailable() {
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isConnected.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    init {
        connectivityManager?.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback
        )
    }

    private fun readConnectivitySnapshot(): Boolean {
        val capabilities = connectivityManager
            ?.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
