/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settingslib.net;

import android.content.Context;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utils class for data usage
 */
public class DataUsageUtils {
    private static final String TAG = "DataUsageUtils";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final WeakHashMap<Integer, SubscriptionInfo> subscriptionCache = new WeakHashMap<>();

    /**
     * Return mobile NetworkTemplate based on {@code subId}.
     */
    @NonNull
    public static NetworkTemplate getMobileTemplate(@NonNull Context context, int subId) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        int mobileDefaultSubId = telephonyManager.getSubscriptionId();

        SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subInfoList = subscriptionManager.getAvailableSubscriptionInfoList();

        if (subInfoList == null || subInfoList.isEmpty()) {
            Log.i(TAG, "Subscription info list is empty for subId: " + subId);
            return getMobileTemplateForSubId(telephonyManager, mobileDefaultSubId);
        }

        if (subscriptionCache.containsKey(subId)) {
            return getNormalizedMobileTemplate(telephonyManager, subId);
        }

        for (SubscriptionInfo subInfo : subInfoList) {
            if (subInfo != null && subInfo.getSubscriptionId() == subId) {
                subscriptionCache.put(subId, subInfo);
                return getNormalizedMobileTemplate(telephonyManager, subId);
            }
        }

        Log.i(TAG, "Subscription is not active for subId: " + subId);
        return getMobileTemplateForSubId(telephonyManager, mobileDefaultSubId);
    }

    private static NetworkTemplate getNormalizedMobileTemplate(
            @NonNull TelephonyManager telephonyManager, int subId) {
        NetworkTemplate mobileTemplate = getMobileTemplateForSubId(telephonyManager, subId);
        String[] mergedSubscriberIds = telephonyManager.createForSubscriptionId(subId).getMergedImsisFromGroup();

        if (mergedSubscriberIds == null || mergedSubscriberIds.length == 0) {
            Log.i(TAG, "MergedSubscriberIds is null for subId: " + subId);
            return mobileTemplate;
        }

        return normalizeMobileTemplate(mobileTemplate, Set.of(mergedSubscriberIds));
    }

    private static NetworkTemplate normalizeMobileTemplate(
            @NonNull NetworkTemplate template, @NonNull Set<String> mergedSet) {
        if (template.getSubscriberIds().isEmpty()) {
            return template;
        }

        String subscriberId = template.getSubscriberIds().iterator().next();
        if (mergedSet.contains(subscriberId)) {
            return new NetworkTemplate.Builder(template.getMatchRule())
                    .setSubscriberIds(mergedSet)
                    .setWifiNetworkKeys(template.getWifiNetworkKeys())
                    .setMeteredness(NetworkStats.METERED_YES)
                    .build();
        }
        return template;
    }

    private static NetworkTemplate getMobileTemplateForSubId(
            @NonNull TelephonyManager telephonyManager, int subId) {
        String subscriberId = telephonyManager.getSubscriberId(subId);
        if (subscriberId != null) {
            return new NetworkTemplate.Builder(NetworkTemplate.MATCH_CARRIER)
                    .setSubscriberIds(Set.of(subscriberId))
                    .setMeteredness(NetworkStats.METERED_YES)
                    .build();
        } else {
            return new NetworkTemplate.Builder(NetworkTemplate.MATCH_MOBILE)
                    .setMeteredness(NetworkStats.METERED_YES)
                    .build();
        }
    }

    /**
     * Returns today's passed time in milliseconds.
     */
    public static long getTodayMillis() {
        LocalTime now = LocalTime.now();
        return now.toSecondOfDay() * 1000L;
    }

    /**
     * Fetches subscription info in the background to avoid UI thread blocking.
     */
    public static void fetchSubscriptionInfoAsync(@NonNull Context context, int subId, @NonNull Callback callback) {
        executor.execute(() -> {
            try {
                SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
                List<SubscriptionInfo> subInfoList = subscriptionManager.getAvailableSubscriptionInfoList();

                if (subInfoList != null) {
                    for (SubscriptionInfo subInfo : subInfoList) {
                        if (subInfo != null && subInfo.getSubscriptionId() == subId) {
                            subscriptionCache.put(subId, subInfo);
                            callback.onResult(subInfo);
                            return;
                        }
                    }
                }
                callback.onResult(null);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching subscription info", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Callback interface for async subscription fetching.
     */
    public interface Callback {
        void onResult(@Nullable SubscriptionInfo subscriptionInfo);
        void onError(@NonNull Exception e);
    }
}
