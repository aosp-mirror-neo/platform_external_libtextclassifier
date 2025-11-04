/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.textclassifier;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.textclassifier.TextLinks;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.textclassifier.utils.TextClassifierUtils;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies whether a message is a WebOTP message.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class TextClassifierSmsWebOtpHandler {
    private static final String WEBOTP_REGEX = "(?:^|\\n)@([a-zA-Z0-9.-]+) #\\d";
    private static final Pattern WEBOTP_PATTERN = Pattern.compile(WEBOTP_REGEX);
    // Reduces the number of instantiated Matcher objects by reusing the same matcher within the
    // same thread to avoid unnecessary GCs.
    private static final ThreadLocal<Matcher> WEBOTP_MATCHER_CACHE =
            ThreadLocal.withInitial(() -> WEBOTP_PATTERN.matcher(""));
    // Reduces the number of instantiated empty bundles.
    private static final Bundle EMPTY_EXTRA_BUNDLE = new Bundle();

    public static void addLink(@NonNull String text, @NonNull TextLinks.Builder builder,
            @NonNull Context context) {
        Matcher matcher = WEBOTP_MATCHER_CACHE.get().reset(text);
        if (matcher.find()) {
            final Map<String, Float> entityScores = Collections.singletonMap(
                    TextClassifierUtils.TYPE_SMS_WEB_OTP, 1f);
            builder.addLink(0, 0, entityScores, EMPTY_EXTRA_BUNDLE);
        }
    }
}
