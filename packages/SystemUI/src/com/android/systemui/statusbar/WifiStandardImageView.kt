/*
 * Copyright (C) 2023 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.systemui.statusbar

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import com.android.systemui.res.R
import java.lang.ref.WeakReference
import android.provider.Settings.Secure.SHOW_WIFI_STANDARD_ICON

class WifiStandardImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStandardEnabled = false
    private var isRegistered = false
    private var contentObserver: ContentObserver? = null

    init {
        setupContentObserver()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        showWifiStandard()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterNetworkCallback()
        unregisterContentObserver()
    }

    private fun setupContentObserver() {
        val showWifiStandardIconUri = Settings.Secure.getUriFor(Settings.Secure.SHOW_WIFI_STANDARD_ICON)
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            private val weakContext = WeakReference(context)

            override fun onChange(selfChange: Boolean) {
                weakContext.get()?.let { ctx ->
                    wifiStandardEnabled = Settings.Secure.getIntForUser(
                        ctx.contentResolver,
                        Settings.Secure.SHOW_WIFI_STANDARD_ICON,
                        0,
                        UserHandle.USER_CURRENT
                    ) == 1

                    if (wifiStandardEnabled) {
                        showWifiStandard()
                    } else {
                        unregisterNetworkCallback()
                        post { visibility = GONE }
                    }
                }
            }
        }
        contentObserver?.let {
            context.contentResolver.registerContentObserver(
                showWifiStandardIconUri, false, it, UserHandle.USER_CURRENT
            )
            it.onChange(true)
        }
    }

    private fun unregisterContentObserver() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    private fun showWifiStandard() {
        if (!wifiStandardEnabled || networkCallback != null) return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateWifiStandard(network)
            }

            override fun onUnavailable() {
                updateWifiStandard(null)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    updateWifiStandard(network)
                }
            }
        }
        registerNetworkCallback()
    }

    private fun updateWifiStandard(network: Network?) {
        val wifiStandard = network?.let { getWifiStandard(it) } ?: -1
        updateIcon(wifiStandard)
    }

    private fun getWifiStandard(network: Network): Int {
        return connectivityManager.getNetworkCapabilities(network)
            ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?.let { wifiManager.connectionInfo.wifiStandard } ?: -1
    }

    private fun updateIcon(wifiStandard: Int) {
        val drawableId = getDrawableForWifiStandard(wifiStandard)
        if (!wifiStandardEnabled || drawableId == 0) {
            post { visibility = GONE }
        } else {
            post {
                setImageResource(drawableId)
                visibility = VISIBLE
            }
        }
    }

    private fun getDrawableForWifiStandard(wifiStandard: Int): Int {
        return when (wifiStandard) {
            4 -> R.drawable.ic_wifi_standard_4
            5 -> R.drawable.ic_wifi_standard_5
            6 -> R.drawable.ic_wifi_standard_6
            7 -> R.drawable.ic_wifi_standard_7
            else -> 0
        }
    }

    private fun registerNetworkCallback() {
        if (isRegistered || networkCallback == null) return
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        isRegistered = true
    }

    private fun unregisterNetworkCallback() {
        if (!isRegistered || networkCallback == null) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback!!)
        } catch (e: IllegalArgumentException) {
            Log.e("WifiStandardImageView", "NetworkCallback unregistration failed", e)
        } finally {
            networkCallback = null
            isRegistered = false
        }
        post { visibility = GONE }
    }
}
