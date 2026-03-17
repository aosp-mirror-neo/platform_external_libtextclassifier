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
import java.util.regex.Matcher
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
        val otpMatcherConfig =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag())
        assertEquals(otpMatcherConfig!!.languageTag, ULocale.ENGLISH.toLanguageTag())
    }

    @Test
    fun fromLanguageTag_english_matchEnglishPatterns() {
        val otpMatcherConfig =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag())

        val otpMatcher = otpMatcherConfig!!.otpMatcher.get()!!
        val falsePositiveMatcher = otpMatcherConfig!!.falsePositiveMatcher.get()!!
        val contextWordsMatcher = otpMatcherConfig!!.strongContextWordsMatcher.get()!!

        assertEquals(getAllMatches("123-456", otpMatcher), setOf("123-456"))
        assertEquals(getAllMatches("Your OTP is 1234567", otpMatcher), setOf("1234567"))
        assertEquals(getAllMatches("Your OTP is:12-34-56", otpMatcher), setOf("12-34-56"))
        assertEquals(getAllMatches("Your OTP is [123 456]", otpMatcher), setOf("123 456"))
        assertEquals(getAllMatches("<#>123456 is your code", otpMatcher), setOf("123456"))
        assertEquals(getAllMatches("1234 is your one-time password", otpMatcher), setOf("1234"))

        otpMatcher.reset("12") // Too short (at least 3 digits for english)
        assertFalse(otpMatcher.find())

        // Credit card ending
        assertEquals(
            getAllMatches("ending with 4412", falsePositiveMatcher), setOf("ending with 4412")
        )
        // Phone number
        assertEquals(
            getAllMatches("Call (650) 253-0000 for help", falsePositiveMatcher),
            setOf("(650) 253-0000")
        )
        assertEquals(
            getAllMatches("Reach us at 650 253-0000", falsePositiveMatcher), setOf("650 253-0000")
        )
        assertEquals(
            getAllMatches("Please contact 650-253-0000", falsePositiveMatcher),
            setOf("650-253-0000")
        )
        // Date
        assertEquals(
            getAllMatches("Expiry: 05-02-2026", falsePositiveMatcher), setOf("05-02-2026")
        )
        assertEquals(
            getAllMatches("5-2-2026 or until further notice", falsePositiveMatcher),
            setOf("5-2-2026")
        )

        assertFalse(falsePositiveMatcher.reset("ending with").find())

        assertTrue(contextWordsMatcher.reset("otp").find())
        assertTrue(contextWordsMatcher.reset("OTP").find()) // Case insensitive
        assertTrue(contextWordsMatcher.reset("TAN").find()) // Transaction Authentication Number
        assertTrue(contextWordsMatcher.reset("one-time passcode").find())
        assertTrue(contextWordsMatcher.reset("two factor authentication").find())
        assertTrue(contextWordsMatcher.reset("verify your identity").find())
        assertTrue(contextWordsMatcher.reset("verification code").find())
        assertTrue(contextWordsMatcher.reset("enter your pin").find())

        assertFalse(contextWordsMatcher.reset("hello").find())
        // Potentially voucher codes
        assertFalse(contextWordsMatcher.reset("code 743a").find())
        assertFalse(contextWordsMatcher.reset("code:bzy6").find())
    }

    @Test
    fun fromLanguageTag_korean_matchKoreanPatterns() {
        val otpMatcherConfig =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.KOREAN.toLanguageTag())

        val otpMatcher = otpMatcherConfig!!.otpMatcher.get()!!
        val falsePositiveMatcher = otpMatcherConfig!!.falsePositiveMatcher.get()!!
        val contextWordsMatcher = otpMatcherConfig!!.strongContextWordsMatcher.get()!!

        // Valid OTP
        assertEquals(getAllMatches("[인증번호: 7890]", otpMatcher), setOf("7890"))

        // Too short. Need at least 4 digits for Korean.
        otpMatcher.reset("456")
        assertFalse(otpMatcher.find())

        // Don't detect OTP with dashes for Korean.
        otpMatcher.reset("123-456")
        assertFalse(otpMatcher.find())

        // Date-like patterns
        assertEquals(getAllMatches("- 기간 : 2026.01.01", falsePositiveMatcher), setOf("2026.01.01"))
        assertEquals(getAllMatches("- 기간 : 2026.1.1", falsePositiveMatcher), setOf("2026.1.1"))
        assertEquals(getAllMatches("- 기간 : 2026.1.10", falsePositiveMatcher), setOf("2026.1.10"))
        assertEquals(
            getAllMatches("- 기간 : 2026. 01. 01", falsePositiveMatcher), setOf("2026. 01. 01")
        )
        assertEquals(getAllMatches("- 기간 : 2026. 1. 1", falsePositiveMatcher), setOf("2026. 1. 1"))
        assertEquals(
            getAllMatches("- 기간 : 2026. 1. 10", falsePositiveMatcher), setOf("2026. 1. 10")
        )
        assertEquals(getAllMatches("- 기간 : 2026. 01.", falsePositiveMatcher), setOf("2026. 01"))
        assertEquals(getAllMatches("- 기간 : 2026. 1.", falsePositiveMatcher), setOf("2026. 1"))
        assertEquals(getAllMatches("- 기간 : 2026-01-01", falsePositiveMatcher), setOf("2026-01-01"))
        assertEquals(getAllMatches("- 기간 : 2026-1-1", falsePositiveMatcher), setOf("2026-1-1"))
        assertEquals(getAllMatches("- 기간 : 2026-1-10", falsePositiveMatcher), setOf("2026-1-10"))
        assertEquals(getAllMatches("- 기간 : 2026/01/01", falsePositiveMatcher), setOf("2026/01/01"))
        assertEquals(getAllMatches("- 기간 : 2026/1/1", falsePositiveMatcher), setOf("2026/1/1"))
        assertEquals(getAllMatches("- 기간 : 2026/1/10", falsePositiveMatcher), setOf("2026/1/10"))
        assertEquals(
            getAllMatches("- 기간 : 2026년 1월 10일", falsePositiveMatcher), setOf("2026년 1월 10")
        )

        // Phone number-like patterns
        assertEquals(getAllMatches("고객센터 문의 : 1234-5678", falsePositiveMatcher), setOf("1234-5678"))
        assertEquals(
            getAllMatches("무료수신거부 080.123.4567", falsePositiveMatcher), setOf("080.123.4567")
        )
        assertEquals(getAllMatches("☎ 02-1234-5678", falsePositiveMatcher), setOf("02-1234-5678"))
        assertEquals(getAllMatches("(800) 826-9983", falsePositiveMatcher), setOf("826-9983"))

        // Time-like patterns
        assertFalse(falsePositiveMatcher.reset("12:15").find())

        // Context words
        assertTrue(contextWordsMatcher.reset("비밀번호 인증").find()) // Password authentication
        assertFalse(contextWordsMatcher.reset("안녕하세요").find()) // Hello
        assertFalse(contextWordsMatcher.reset("▶️픽업번호 : 112").find()) // Pickup number
        assertFalse(contextWordsMatcher.reset("주문번호: ORD-K112981").find()) // Order id
        assertFalse(contextWordsMatcher.reset("당신만을 위한 코드: STY2025OFF").find()) // Coupon code

        // Mixed languages
        assertTrue(contextWordsMatcher.reset("[국제발신]649640는귀하의 Shopping OTP입니다.").find())
    }

    @Test
    fun fromLanguageTag_spanish_matchesOtpPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.SPANISH_LANGUAGE_TAG)
        val matcher = config!!.otpMatcher.get()!!

        assertEquals(
            setOf("1234"),
            getAllMatches("1234-Código de verificación", matcher)
        )
        assertEquals(
            setOf("12345678"),
            getAllMatches("G-12345678", matcher)
        )
        assertEquals(
            setOf("123-456"),
            getAllMatches("Código de verificación: 123-456", matcher)
        )
        assertEquals(
            setOf("123 456"),
            getAllMatches("123 456 es tu contraseña temporal", matcher)
        )

        // Too short (at least 4 digits for Spanish)
        matcher.reset("123")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_spanish_matchesFalsePositivePatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.SPANISH_LANGUAGE_TAG)
        val matcher = config!!.falsePositiveMatcher.get()!!

        // Date-like patterns
        assertEquals(
            setOf("2022-05-16"),
            getAllMatches("está entregado el 2022-05-16 en 11:42.", matcher)
        )
        assertEquals(
            setOf("26/05/2025"),
            getAllMatches("la Atencion Telefonica del 26/05/2025", matcher)
        )
        assertEquals(
            setOf("31-03-2025"),
            getAllMatches("31-03-2025 de 11:00 a 12:00", matcher)
        )

        // Phone-like patterns
        assertEquals(
            setOf("55 5447 8000", "800 90 90000"),
            getAllMatches("O comunícate a nuestro CAT 📞 *55 5447 8000* | *800 90 90000*", matcher)
        )

        // Currency-like patterns
        assertEquals(
            setOf("$7100"),
            getAllMatches("Invita a amigos y gana \$7100 m.n.", matcher)
        )
    }

    @Test
    fun fromLanguageTag_spanish_matchesContextWordsPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.SPANISH_LANGUAGE_TAG)
        val matcher = config!!.strongContextWordsMatcher.get()!!

        // Your verification code is
        matcher.reset("Su codigo de verificacion es")
        assertTrue(matcher.find())

        // Your security code
        matcher.reset("Tu código de seguridad")
        assertTrue(matcher.find())

        // For your new access code
        matcher.reset("para tu nueva clave de acceso")
        assertTrue(matcher.find())

        // Access your digital banking from the app or website using your one-time password
        matcher.reset(
            "Accede a tu banca digital desde la app o la web con la contraseña de 1 solo uso"
        )
        assertTrue(matcher.find())

        // Here's a special code just for you
        matcher.reset("Aquí hay un código especial solo para ti")
        assertFalse(matcher.find())

        // Your K-Parcel order with code
        matcher.reset("Tu pedido de K-Parcel con código")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_unknown_returnNull() {
        val otpMatcherConfig = OtpMatcherLanguageConfig.fromLanguageTag("unknown")
        assertNull(otpMatcherConfig)
    }

    private fun getAllMatches(text: String, regex: Matcher): Set<String> {
        val matches = mutableSetOf<String>()
        regex.reset(text)
        while (regex.find()) {
            matches.add(regex.group())
        }
        return matches
    }
}
