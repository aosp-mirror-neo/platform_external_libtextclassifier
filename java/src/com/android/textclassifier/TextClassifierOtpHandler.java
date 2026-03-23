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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handler class for TextClassifier for One-Time Password (OTP) requests. Adds to
 * {@link android.view.textclassifier.TextLinks} if the input text contains an OTP.
 */
class TextClassifierOtpHandler {
    private static final String TAG = TextClassifierOtpHandler.class.getSimpleName();

    /**
     * Checks if the text might contain an OTP, if so, adds a link to the builder with type as OTP.
     *
     * @param text                 The text whose content should be checked for OTP.
     * @param detectedLanguageTags The language tags of the text.
     * @param builder              TextLinks builder object to whom the OTP link to be added.
     */
    public static void addOtpLink(@NonNull String text,
            @NonNull TextLinks.Builder builder,
            @NonNull List<String> detectedLanguageTags) {
        if (!OtpDetector.containsOtp(text, detectedLanguageTags)) {
            return;
        }
        final Map<String, Float> entityScores =
                Collections.singletonMap(TextClassifierUtils.TYPE_OTP, 1f);
        builder.addLink(0, 0, entityScores, new Bundle());
    }

    private TextClassifierOtpHandler() {
    }
}
