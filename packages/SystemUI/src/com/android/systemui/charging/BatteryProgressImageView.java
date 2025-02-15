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
 * limitations under the License.
 */

package com.android.systemui.charging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.android.systemui.res.R;
import com.android.systemui.util.ArcProgressWidget;

public class BatteryProgressImageView extends ImageView {

    private static final String TAG = "BatteryProgressImageView";
    private Context mContext;
    private Bitmap mCurrentBitmap;
    private int mBatteryLevel = -1;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            mBatteryLevel = (int) ((level / (float) scale) * 100);

            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);

            Log.d(TAG, "Charging: " + isCharging + ", Level: " + mBatteryLevel);
            updateImageView(isCharging);
        }
    };

    public BatteryProgressImageView(Context context) {
        super(context);
        init(context);
    }

    public BatteryProgressImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BatteryProgressImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        startBatteryUpdates();
    }

    private void startBatteryUpdates() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(batteryReceiver, filter);
    }

    private Drawable getScaledDrawable(int drawableResId) {
        Drawable drawable = ContextCompat.getDrawable(mContext, drawableResId);
        if (drawable != null) {
            int sizeInPixels = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 36, mContext.getResources().getDisplayMetrics());
            drawable.setBounds(0, 0, sizeInPixels, sizeInPixels);
        }
        return drawable;
    }

    private void updateImageView(boolean isCharging) {
        recycleBitmap();

        int drawableResId = isCharging ? R.drawable.ic_charging_miui : R.drawable.ic_battery;
        Drawable drawable = getScaledDrawable(drawableResId);

        if (drawable == null) {
            Log.e(TAG, "Drawable resource is null.");
            return;
        }

        mCurrentBitmap = ArcProgressWidget.generateBitmap(
                mContext,
                mBatteryLevel,
                mBatteryLevel + "%",
                40,
                drawable,
                36
        );
        setImageBitmap(mCurrentBitmap);
    }

    private void recycleBitmap() {
        if (mCurrentBitmap != null && !mCurrentBitmap.isRecycled()) {
            mCurrentBitmap.recycle();
            mCurrentBitmap = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopBatteryUpdates();
        recycleBitmap();
    }

    private void stopBatteryUpdates() {
        mContext.unregisterReceiver(batteryReceiver);
    }
}
