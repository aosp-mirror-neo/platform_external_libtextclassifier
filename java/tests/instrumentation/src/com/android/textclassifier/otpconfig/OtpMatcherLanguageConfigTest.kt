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
package com.android.textclassifier.otpconfig


import android.icu.util.ULocale
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OtpMatcherLanguageConfigTest {

    @Test
    fun fromLanguageTag_english_returnEnglishConfig() {
        val otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag());
        assertEquals(otpMatcherConfig!!.languageTag, ULocale.ENGLISH.toLanguageTag())
    }

    @Test
    fun fromLanguageTag_english_matchEnglishPatterns() {
        val otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag());

        val otpMatcher = otpMatcherConfig!!.otpMatcher.get()!!
        val falsePositiveMatcher = otpMatcherConfig!!.falsePositiveMatcher.get()!!
        val contextWordsMatcher = otpMatcherConfig!!.contextWordsMatcher.get()!!

        otpMatcher.reset("123-456")
        assertTrue(otpMatcher.find())

        otpMatcher.reset("12") // Too short (at least 3 digits for english)
        assertFalse(otpMatcher.find())

        falsePositiveMatcher.reset("ending with 4412") // Credit card ending
        assertTrue(falsePositiveMatcher.find())

        falsePositiveMatcher.reset("ending with")
        assertFalse(falsePositiveMatcher.find())

        contextWordsMatcher.reset("otp")
        assertTrue(contextWordsMatcher.find())

        contextWordsMatcher.reset("hello")
        assertFalse(contextWordsMatcher.find())
    }

    @Test
    fun fromLanguageTag_unknown_returnNull() {
        val otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag("unknown");
        assertNull(otpMatcherConfig)
    }
}
