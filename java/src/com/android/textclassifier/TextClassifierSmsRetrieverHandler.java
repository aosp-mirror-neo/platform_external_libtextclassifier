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

import com.android.textclassifier.utils.AppHashHelper;
import com.android.textclassifier.utils.TextClassifierUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Map;

/**
 * Handles messages that conform to the SMS Retriever API format.
 * <p>
 * This class checks if the input {@code text} contains a known 11-character app hash, if so, adds a
 * {@link TextLinks.Link} of type {@link android.view.textclassifier.TextClassifier#TYPE_SMS_RETRIEVER_OTP}.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class TextClassifierSmsRetrieverHandler {
    private static final int HASH_LENGTH = 11;

    /**
     * Analyzes the given text for an SMS Retriever app hash and, if found, adds a
     * corresponding {@link TextLinks.Link} to the builder.
     * <p>
     * This method first invokes the app hashes load, then searches the {@code text} for a known
     * 11-character app hash.
     * <p>
     * If a valid hash is found, a link spanning the entire message is added to the
     * {@code builder} with the entity type {@link TextClassifierUtils#TYPE_SMS_RETRIEVER_OTP}.
     *
     * @param text    The full content of the SMS message to be analyzed.
     * @param builder The {@link TextLinks.Builder} to which a new link will be added if the
     *                message is identified.
     * @param context The {@link Context} used to access system services like
     *                {@code PackageManager} for loading app hashes if they are not already cached.
     */
    public static void addLink(@NonNull String text, @NonNull TextLinks.Builder builder,
            @NonNull Context context) {
        AppHashHelper.ensureLoaded(context);
        if (!containsSmsRetrieverHash(text)) {
            return;
        }
        final Map<String, Float> entityScores = Collections.singletonMap(
                TextClassifierUtils.TYPE_SMS_RETRIEVER_OTP, 1f);
        builder.addLink(0, 0, entityScores, new Bundle());
    }

    @VisibleForTesting
    public static boolean containsSmsRetrieverHash(@NonNull String text) {
        if (text.length() < HASH_LENGTH) {
            return false;
        }

        for (int i = text.length() - HASH_LENGTH; i >= 0; i--) {
            String sub = text.substring(i, i + HASH_LENGTH);
            if (AppHashHelper.hasHash(sub)) {
                return true;
            }
        }

        return false;
    }
}
