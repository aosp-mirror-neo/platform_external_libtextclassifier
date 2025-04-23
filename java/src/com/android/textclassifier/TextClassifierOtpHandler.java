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
import android.view.textclassifier.TextLanguage;
import android.view.textclassifier.TextLinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.utils.TextClassifierUtils;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Handler class for TextClassifier for One-Time Password (OTP) requests. Adds to
 * to {@link android.view.textclassifier.TextLinks} if the input text contains an OTP
 */
class TextClassifierOtpHandler {
  private static final String TAG = TextClassifierOtpHandler.class.getSimpleName();

  /**
   * Checks if the text might contain an OTP, if so, adds a link to the builder with type as OTP
   *
   * @param text    The text whose content should be checked for OTP
   * @param tcImpl  Instance of the TextClassifierImpl to detect the language of input text
   * @param builder TextLinks builder object to whom the OTP link to be added
   */
  public static void addOtpLink(@NonNull String text, @NonNull TextClassifierImpl tcImpl,
          @NonNull TextLinks.Builder builder) {
    if (!containsOtp(text, tcImpl)) {
      return;
    }
    final Map<String, Float> entityScores = Collections.singletonMap(TextClassifierUtils.TYPE_OTP,
            1f);
    builder.addLink(0, 0, entityScores, new Bundle());
  }

  /**
   * Checks if a string of text might contain an OTP, based on several regular expressions, and
   * potentially using a textClassifier to eliminate false positives
   *
   * @param text   The text whose content should be checked.
   * @param tcImpl TextClassifierImpl to be used to find the language of the text.
   * @return {@code true} if an OTP is determined to be in the text, {@code false} otherwise.
   */
  @VisibleForTesting
  protected static boolean containsOtp(
          @NonNull String text,
          @NonNull TextClassifierImpl tcImpl) {
    if (!OtpDetector.containsOtpLikePattern(text)) {
      return false;
    }

    TextLanguage language = getTextLanguage(text, tcImpl);
    if (language == null) {
      return false;
    }
    return OtpDetector.containsOtpWithLanguage(text, language);
  }

  @Nullable
  private static TextLanguage getTextLanguage(String text, @NonNull TextClassifierImpl tcImpl) {
    TextLanguage.Request langRequest = new TextLanguage.Request.Builder(text).build();
    try {
      return tcImpl.detectLanguage(null, null, langRequest);
    } catch (IOException e) {
      TcLog.e(TAG, "Except detecting language", e);
      return null;
    }
  }

  private TextClassifierOtpHandler() {}
}
