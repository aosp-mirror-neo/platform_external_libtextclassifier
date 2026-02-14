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

import static java.lang.String.format;

import android.icu.util.ULocale;
import android.util.ArrayMap;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.textclassifier.otpconfig.OtpMatcherLanguageConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class with helper methods to detecting One-Time Password (OTP) codes in a text.
 *
 * <p>This class is designed to be lightweight with minimal dependencies, allowing it
 * to be easily exported and built as a standalone library.
 */
public class OtpDetector {

  private static final float TC_THRESHOLD = 0.6f;

  /**
   * Checks if a string of text might contain an OTP, based on several regular expressions, and
   * potentially using a textClassifier to eliminate false positives.
   *
   * <p><b>Note:</b> This method is meant to be called in Android V only. Android B+ should make
   * TextClassifier request to determine if the text contains OTP.</p>
   *
   * <p><b>Important:</b> Signature of this method to be kept intact since it is intended for
   * use by external modules via an exported library.
   *
   * @param text The input text to scan for OTP keywords. Must not be null.
   * @param tc TextClassifier instance to be used to find the language of the text.
   * @return {@code true} if an OTP is determined to be in the text, {@code false} otherwise.
   */
  public static boolean containsOtp(
          @NonNull String text,
          @NonNull TextClassifier tc) {
    TextLanguage language = getTextLanguage(text, tc);
    ULocale uLocale = getHighestConfidenceLocale(language);
    if (uLocale == null) {
      return false;
    }
    return containsOtp(text, /* detectedLanguageTags= */ ImmutableList.of(uLocale.toLanguageTag()));
  }

  /**
   * Checks if a string of text might contain an OTP, based on several regular expressions.
   *
   * @param text The text whose content should be checked.
   * @param detectedLanguageTags The language tags of the text.
   * @return {@code true} if an OTP is determined to be in the text, {@code false} otherwise.
   */
  protected static boolean containsOtp(@NonNull String text, @NonNull List<String> detectedLanguageTags) {
    for (String languageTag : detectedLanguageTags) {
      if (containsOtpLikePattern(text, languageTag) && containsOtpContextWord(text, languageTag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the given text contains a pattern resembling an OTP.
   *
   * <p>This method attempts to identify such patterns by matching against a regular expression.
   * Avoids false positives by checking for common patterns that might be mistaken for OTPs, such
   * as phone numbers or dates.</p>
   *
   * @param text The text to be checked.
   * @param languageTag The language tag (e.g., "en", "es", "fr") for which to check.
   * @return {@code true} if the text contains an OTP-like pattern, {@code false} otherwise.
   */
  @VisibleForTesting
  protected static boolean containsOtpLikePattern(@NonNull String text, @NonNull String languageTag) {
    OtpMatcherLanguageConfig otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag(languageTag);
    if (otpMatcherConfig == null) {
      return false;
    }

    Set<String> otpMatches = getAllMatches(text, otpMatcherConfig.getOtpMatcher().get());
    if (otpMatches.isEmpty()) {
      return false;
    }
    Set<String> falsePositives = getAllMatches(text, otpMatcherConfig.getFalsePositiveMatcher().get());

    // This optional, but having this would help with performance
    // Example: "Your OTP code is 1234 and this is sent on 01-01-2001"
    // At this point -> otpMatches: [1234, 01-01-2001] falsePositives=[01-01-2001]
    // It filters "01-01-2001" in advance and continues to next checks with otpMatches: [1234]
    otpMatches.removeAll(falsePositives);

    // Following is to handle text like: "Your OTP can't be shared at this point, please call
    // (888) 888-8888"
    // otpMatches: [888-8888] falsePositives=[(888) 888-8888] final=[]
    for (String otpMatch : otpMatches) {
      boolean currentOtpIsFalsePositive = false;
      for (String falsePositive : falsePositives) {
        if (falsePositive.contains(otpMatch)) {
          currentOtpIsFalsePositive = true;
          break;
        }
      }
      if (!currentOtpIsFalsePositive) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the given text contains a language-specific context word or phrase associated with OTPs.
   * This method uses regular expressions defined for specific languages to identify these words.
   *
   * @param text The text to check.
   * @param languageTag The language tag (e.g., "en", "es", "fr") for which to check.
   * @return {@code true} if the text contains a language-specific OTP word, {@code false} otherwise.
   *         Returns {@code false} if no language-specific regex is defined for the given tag.
   */
  private static boolean containsOtpContextWord(@NonNull String text, @NonNull String languageTag) {
    OtpMatcherLanguageConfig otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag(languageTag);
    if (otpMatcherConfig == null) {
      return false;
    }
    Matcher languageSpecificMatcher = otpMatcherConfig.getContextWordsMatcher().get();
    if (languageSpecificMatcher == null) {
      return false;
    }
    languageSpecificMatcher.reset(text);
    return languageSpecificMatcher.find();
  }

  private static Set<String> getAllMatches(String text, Matcher regex) {
    Set<String> matches = new HashSet<>();
    regex.reset(text);
    while (regex.find()) {
      matches.add(regex.group());
    }
    return matches;
  }

  // Tries to determine the language of the given text.
  private static TextLanguage getTextLanguage(@NonNull String text, @NonNull TextClassifier tc) {
    TextLanguage.Request langRequest = new TextLanguage.Request.Builder(text).build();
    return tc.detectLanguage(langRequest);
  }

  // Will return the language with the highest confidence score that meets the minimum threshold,
  // null otherwise
  @Nullable
  private static ULocale getHighestConfidenceLocale(@NonNull TextLanguage lang) {
    float highestConfidence = 0;
    ULocale highestConfidenceLocale = null;
    for (int i = 0; i < lang.getLocaleHypothesisCount(); i++) {
      ULocale locale = lang.getLocale(i);
      float confidence = lang.getConfidenceScore(locale);
      if (confidence >= TC_THRESHOLD
              && confidence >= highestConfidence) {
        highestConfidence = confidence;
        highestConfidenceLocale = locale;
      }
    }
    return highestConfidenceLocale;
  }

  private OtpDetector() {}
}
