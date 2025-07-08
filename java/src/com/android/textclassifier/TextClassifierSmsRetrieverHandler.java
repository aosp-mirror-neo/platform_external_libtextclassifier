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

import android.os.Bundle;
import android.view.textclassifier.TextLinks;

import androidx.annotation.NonNull;

import com.android.textclassifier.utils.TextClassifierUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles messages that conform to the SMS Retriever API format.
 * <p>
 * This class checks if the input {@code text} contains a known 11-character app hash, if so, adds a
 * {@link TextLinks.Link} of type {@link android.view.textclassifier.TextClassifier#TYPE_SMS_RETRIEVER_OTP}.
 */
public class TextClassifierSmsRetrieverHandler {
    private static final Set<String> SMS_RETRIEVER_APP_HASHES = ConcurrentHashMap.newKeySet();
    private static final int HASH_LENGTH = 11;

    /**
     * Adds a app hash to the set of known SMS Retriever hashes.
     * <p>
     * This method is called by the ExtServices module to populate the list of hashes that
     * TextClassifier will use to identify SMS Retriever messages.
     *
     * @param hash The app hash to add.
     */
    public static void addHash(String hash) {
        SMS_RETRIEVER_APP_HASHES.add(hash);
    }

    /**
     * Returns the number of app hashes currently cached.
     *
     * @return The total count of known app hashes.
     */
    public static int getAppHashCount() {
        return SMS_RETRIEVER_APP_HASHES.size();
    }

    /**
     * Clears all cached app hashes. For testing purposes only.
     */
    @VisibleForTesting
    public static void clearAllHashes() {
        SMS_RETRIEVER_APP_HASHES.clear();
    }

    /**
     * Checks if a specific hash is present in the cache. For testing purposes only.
     *
     * @param hash The hash to check for.
     * @return {@code true} if the hash is present, {@code false} otherwise.
     */
    @VisibleForTesting
    public static boolean hasHash(String hash) {
        return SMS_RETRIEVER_APP_HASHES.contains(hash);
    }

    /**
     * Analyzes the given text and adds a link to the builder if it is identified as an
     * SMS Retriever message.
     * <p>
     * This method checks if the input {@code text} contains a known 11-character app hash.
     * If a valid hash is found, a link spanning the entire message is added to the
     * {@code builder} with the entity type {@link TextClassifierUtils#TYPE_SMS_RETRIEVER_OTP}.
     *
     * @param text    The full content of the SMS message to be analyzed.
     * @param builder The {@link TextLinks.Builder} to which a new link will be added if the
     *                message is identified.
     */
    public static void addLink(@NonNull String text, @NonNull TextLinks.Builder builder) {
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
            if (SMS_RETRIEVER_APP_HASHES.contains(sub)) {
                return true;
            }
        }

        return false;
    }
}
