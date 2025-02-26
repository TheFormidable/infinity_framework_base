/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.systemui.privacy

import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.DeviceConfig
import android.provider.Settings
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.util.asIndenting
import com.android.systemui.util.annotations.WeaklyReferencedCallback
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.withIncreasedIndent
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class PrivacyConfig @Inject constructor(
    @Main private val uiExecutor: DelayableExecutor,
    private val secureSettings: SecureSettings,
    @Main handler: Handler,
    dumpManager: DumpManager
) : Dumpable {

    @VisibleForTesting
    internal companion object {
        const val TAG = "PrivacyConfig"
    }

    private val callbacks = mutableListOf<Callback>()

    var micCameraAvailable = isMicCameraEnabled()
        private set
    var locationAvailable = isLocationEnabled()
        private set
    var mediaProjectionAvailable = isMediaProjectionEnabled()
        private set

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            micCameraAvailable = isMicCameraEnabled()
            locationAvailable = isLocationEnabled()
            mediaProjectionAvailable = isMediaProjectionEnabled()
            callbacks.forEach { it.onFlagMicCameraChanged(micCameraAvailable) }
            callbacks.forEach { it.onFlagLocationChanged(locationAvailable) }
            callbacks.forEach { it.onFlagMediaProjectionChanged(mediaProjectionAvailable) }
        }
    }

    init {
        dumpManager.registerDumpable(TAG, this)
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.ENABLE_LOCATION_PRIVACY_INDICATOR,
            settingsObserver, UserHandle.USER_CURRENT
        )
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.ENABLE_CAMERA_PRIVACY_INDICATOR,
            settingsObserver, UserHandle.USER_CURRENT
        )
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.ENABLE_PROJECTION_PRIVACY_INDICATOR,
            settingsObserver, UserHandle.USER_CURRENT
        )
    }

    private fun isMicCameraEnabled(): Boolean {
        return  secureSettings.getIntForUser(
            Settings.Secure.ENABLE_CAMERA_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT) == 1
    }

    private fun isLocationEnabled(): Boolean {
        return secureSettings.getIntForUser(
            Settings.Secure.ENABLE_LOCATION_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT) == 1
    }

    private fun isMediaProjectionEnabled(): Boolean {
        return secureSettings.getIntForUser(
            Settings.Secure.ENABLE_PROJECTION_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT) == 1
    }

    fun addCallback(callback: Callback) {
        uiExecutor.execute {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: Callback) {
        uiExecutor.execute {
            callbacks.remove(callback)
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        val ipw = pw.asIndenting()
        ipw.println("PrivacyConfig state:")
        ipw.withIncreasedIndent {
            ipw.println("micCameraAvailable: $micCameraAvailable")
            ipw.println("locationAvailable: $locationAvailable")
            ipw.println("mediaProjectionAvailable: $mediaProjectionAvailable")
            ipw.println("Callbacks:")
            ipw.withIncreasedIndent {
                callbacks.forEach { ipw.println(it) }
            }
        }
        ipw.flush()
    }

    @WeaklyReferencedCallback
    interface Callback {
        fun onFlagMicCameraChanged(flag: Boolean) {}

        fun onFlagLocationChanged(flag: Boolean) {}

        fun onFlagMediaProjectionChanged(flag: Boolean) {}
    }
}
