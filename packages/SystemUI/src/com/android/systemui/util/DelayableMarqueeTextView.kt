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

package com.android.systemui.util

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DelayableMarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : SafeMarqueeTextView(context, attrs, defStyleAttr, defStyleRes) {

    var marqueeDelay: Long = DEFAULT_MARQUEE_DELAY
    private var wantsMarquee = false
    private var marqueeBlocked = true

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var marqueeJob: Job? = null

    init {
        try {
            val typedArray = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.DelayableMarqueeTextView,
                defStyleAttr,
                defStyleRes
            )
            marqueeDelay = typedArray.getInteger(
                R.styleable.DelayableMarqueeTextView_marqueeDelay,
                DEFAULT_MARQUEE_DELAY.toInt()
            ).toLong()
            typedArray.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing DelayableMarqueeTextView: ${e.message}", e)
        }
    }

    override fun startMarquee() {
        if (!isSelected || !isAttachedToWindow) {
            return
        }
        wantsMarquee = true
        if (marqueeBlocked) {
            if (marqueeJob?.isActive != true) {
                marqueeJob = coroutineScope.launch {
                    delay(marqueeDelay)
                    if (wantsMarquee && isAttachedToWindow) {
                        marqueeBlocked = false
                        startMarqueeOnMainThread()
                    }
                }
            }
            return
        }
        super.startMarquee()
    }

    private fun startMarqueeOnMainThread() {
        CoroutineScope(Dispatchers.Main).launch {
            super@DelayableMarqueeTextView.startMarquee()
        }
    }

    override fun stopMarquee() {
        marqueeJob?.cancel()
        wantsMarquee = false
        marqueeBlocked = true
        super.stopMarquee()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        marqueeJob?.cancel()
        marqueeBlocked = true
    }

    companion object {
        private const val TAG = "DelayableMarqueeTextView"
        const val DEFAULT_MARQUEE_DELAY = 2000L
    }
}
