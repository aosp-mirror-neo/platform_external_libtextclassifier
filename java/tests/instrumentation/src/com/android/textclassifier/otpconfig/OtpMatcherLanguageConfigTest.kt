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
    fun fromLanguageTag_english_matchesOtpPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag())!!
        val matcher = config.otpMatcher.get()!!

        assertEquals(getAllMatches("123-456", matcher), setOf("123-456"))
        assertEquals(getAllMatches("Your OTP is 1234567", matcher), setOf("1234567"))
        assertEquals(getAllMatches("Your OTP is:12-34-56", matcher), setOf("12-34-56"))
        assertEquals(getAllMatches("Your OTP is [123 456]", matcher), setOf("123 456"))
        assertEquals(getAllMatches("<#>123456 is your code", matcher), setOf("123456"))
        assertEquals(getAllMatches("1234 is your one-time password", matcher), setOf("1234"))

        matcher.reset("12") // Too short (at least 3 digits for english)
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_english_matchesFalsePositivePatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag())!!
        val matcher = config.falsePositiveMatcher.get()!!

        // Credit card ending
        assertEquals(getAllMatches("ending with 4412", matcher), setOf("ending with 4412"))
        // Phone number
        assertEquals(
            getAllMatches("Call (650) 253-0000 for help", matcher), setOf("(650) 253-0000")
        )
        assertEquals(getAllMatches("Reach us at 650 253-0000", matcher), setOf("650 253-0000"))
        assertEquals(getAllMatches("Please contact 650-253-0000", matcher), setOf("650-253-0000"))
        // Date
        assertEquals(getAllMatches("Expiry: 05-02-2026", matcher), setOf("05-02-2026"))
        assertEquals(getAllMatches("5-2-2026 or until further notice", matcher), setOf("5-2-2026"))

        assertFalse(matcher.reset("ending with").find())
    }

    @Test
    fun fromLanguageTag_english_matchesContextWordsPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.ENGLISH.toLanguageTag())!!
        val matcher = config.strongContextWordsMatcher.get()!!

        assertTrue(matcher.reset("otp").find())
        assertTrue(matcher.reset("OTP").find()) // Case-insensitive
        assertTrue(matcher.reset("TAN").find()) // Transaction Authentication Number
        assertTrue(matcher.reset("one-time passcode").find())
        assertTrue(matcher.reset("two factor authentication").find())
        assertTrue(matcher.reset("verify your identity").find())
        assertTrue(matcher.reset("verification code").find())
        assertTrue(matcher.reset("enter your pin").find())

        assertFalse(matcher.reset("hello").find())
        // Potentially voucher codes
        assertFalse(matcher.reset("code 743a").find())
        assertFalse(matcher.reset("code:bzy6").find())
    }

    @Test
    fun fromLanguageTag_korean_matchesOtpPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.KOREAN.toLanguageTag())!!
        val matcher = config.otpMatcher.get()!!

        // Valid OTP
        assertEquals(getAllMatches("[인증번호: 7890]", matcher), setOf("7890"))

        // Too short. Need at least 4 digits for Korean.
        matcher.reset("456")
        assertFalse(matcher.find())

        // Don't detect OTP with dashes for Korean.
        matcher.reset("123-456")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_korean_matchesFalsePositivePatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.KOREAN.toLanguageTag())!!
        val matcher = config.falsePositiveMatcher.get()!!

        // Date-like patterns
        assertEquals(getAllMatches("- 기간 : 2026.01.01", matcher), setOf("2026.01.01"))
        assertEquals(getAllMatches("- 기간 : 2026.1.1", matcher), setOf("2026.1.1"))
        assertEquals(getAllMatches("- 기간 : 2026.1.10", matcher), setOf("2026.1.10"))
        assertEquals(getAllMatches("- 기간 : 2026. 01. 01", matcher), setOf("2026. 01. 01"))
        assertEquals(getAllMatches("- 기간 : 2026. 1. 1", matcher), setOf("2026. 1. 1"))
        assertEquals(getAllMatches("- 기간 : 2026. 1. 10", matcher), setOf("2026. 1. 10"))
        assertEquals(getAllMatches("- 기간 : 2026. 01.", matcher), setOf("2026. 01"))
        assertEquals(getAllMatches("- 기간 : 2026. 1.", matcher), setOf("2026. 1"))
        assertEquals(getAllMatches("- 기간 : 2026-01-01", matcher), setOf("2026-01-01"))
        assertEquals(getAllMatches("- 기간 : 2026-1-1", matcher), setOf("2026-1-1"))
        assertEquals(getAllMatches("- 기간 : 2026-1-10", matcher), setOf("2026-1-10"))
        assertEquals(getAllMatches("- 기간 : 2026/01/01", matcher), setOf("2026/01/01"))
        assertEquals(getAllMatches("- 기간 : 2026/1/1", matcher), setOf("2026/1/1"))
        assertEquals(getAllMatches("- 기간 : 2026/1/10", matcher), setOf("2026/1/10"))
        assertEquals(getAllMatches("- 기간 : 2026년 1월 10일", matcher), setOf("2026년 1월 10"))

        // Phone number-like patterns
        assertEquals(getAllMatches("고객센터 문의 : 1234-5678", matcher), setOf("1234-5678"))
        assertEquals(getAllMatches("무료수신거부 080.123.4567", matcher), setOf("080.123.4567"))
        assertEquals(getAllMatches("☎ 02-1234-5678", matcher), setOf("02-1234-5678"))
        assertEquals(getAllMatches("(800) 826-9983", matcher), setOf("826-9983"))

        // Time-like patterns
        assertFalse(matcher.reset("12:15").find())
    }

    @Test
    fun fromLanguageTag_korean_matchesContextWordsPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(ULocale.KOREAN.toLanguageTag())!!
        val matcher = config.strongContextWordsMatcher.get()!!

        // Context words
        assertTrue(matcher.reset("비밀번호 인증").find()) // Password authentication
        assertFalse(matcher.reset("안녕하세요").find()) // Hello
        assertFalse(matcher.reset("▶️픽업번호 : 112").find()) // Pickup number
        assertFalse(matcher.reset("주문번호: ORD-K112981").find()) // Order id
        assertFalse(matcher.reset("당신만을 위한 코드: STY2025OFF").find()) // Coupon code

        // Mixed languages
        assertTrue(matcher.reset("[국제발신]649640는귀하의 Shopping OTP입니다.").find())
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
    fun fromLanguageTag_bahasa_matchesOtpPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.BAHASA_LANGUAGE_TAG)!!
        val matcher = config.otpMatcher.get()!!

        assertEquals(
            setOf("1234"),
            getAllMatches("Kode OTP kamu adalah [1234].", matcher)
        )
        assertEquals(
            setOf("566677"),
            getAllMatches("G-566677 adalah kode verifikasi Google Anda.", matcher)
        )
        assertEquals(
            setOf("123-456"),
            getAllMatches("Kode WhatsApp Anda 123-456", matcher)
        )

        // Too short (at least 4 digits for Bahasa)
        matcher.reset("123")
        assertFalse(matcher.find())

        // Too long
        matcher.reset("Merchant PAN        9360001430024541374")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_bahasa_matchesFalsePositivePatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.BAHASA_LANGUAGE_TAG)!!
        val matcher = config.falsePositiveMatcher.get()!!

        // Date-like patterns
        assertEquals(
            setOf("06-06-2025"),
            getAllMatches("berlaku 5 menit hingga 06-06-2025 15:42:00", matcher)
        )
        assertEquals(
            setOf("2025-05-28"),
            getAllMatches("Berlaku Sampai 2025-05-28 08:32", matcher)
        )
        assertEquals(
            setOf("08/05/2025"),
            getAllMatches("dan berlaku hingga 08/05/2025 08:51.", matcher)
        )

        // Phone-like patterns
        assertEquals(
            setOf("021 5086 0698"),
            getAllMatches("Kami akan menghubungi kamu di nomor 021 5086 0698", matcher),
        )
    }

    @Test
    fun fromLanguageTag_bahasa_matchesContextWordsPatterns() {
        val config =
            OtpMatcherLanguageConfig.fromLanguageTag(OtpMatcherLanguageConfig.BAHASA_LANGUAGE_TAG)!!
        val matcher = config.strongContextWordsMatcher.get()!!

        // Your transaction authentication code is ####
        matcher.reset("Kode Otentikasi transaksi Anda 607500")
        assertTrue(matcher.find())

        // #### is your one-time password
        matcher.reset("037920 adalah One Time Password")
        assertTrue(matcher.find())

        // Do not give out the secret code
        matcher.reset("JANGAN BERIKAN KODE RAHASIA")
        assertTrue(matcher.find())

        // Use this PIN to reset your password: ####.
        matcher.reset("Gunakan PIN ini untuk mengatur ulang sandi Anda: 8852.")
        assertTrue(matcher.find())

        // Transaction code ###-#####.
        matcher.reset("Kode transaksi 5555-0014216094.")
        assertFalse(matcher.find())

        // Was this offer/information helpful? If not, text STOP 360Kredi to 8000.
        matcher.reset("Penawaran/info ini bermanfaat? Jika tidak, Kirim STOP 360Kredi ke 8000")
        assertFalse(matcher.find())

        // Order ### has arrived at your address on DD/MM/YYYY.
        matcher.reset("Pesanan #250625F3M5CKYP telah tiba di alamatmu pada 30/06/2025.")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_japanese_matchesOtpPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.JAPANESE.toLanguageTag())!!
        val matcher = config.otpMatcher.get()!!

        assertEquals(
            setOf("5023"),
            getAllMatches("認証コード：JK-5023", matcher)
        )
        assertEquals(
            setOf("915210"),
            getAllMatches("<#> 認証番号「915210」", matcher)
        )
        assertEquals(
            setOf("444-777"),
            getAllMatches("認証コードは444-777です。", matcher)
        )
        assertEquals(
            setOf("522 987"),
            getAllMatches("<#> 522 987があなた", matcher)
        )

        // Too short (at least 4 digits for Japanese)
        matcher.reset("認証コード：YONY656")
        assertFalse(matcher.find())

        // Too long
        matcher.reset("■ログインID：08075363215/パスワード：ご指定のパスワード")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_japanese_matchesFalsePositivePatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.JAPANESE.toLanguageTag())!!
        val matcher = config.falsePositiveMatcher.get()!!

        // Date-like patterns
        assertEquals(
            setOf("2024/06/06"),
            getAllMatches("プリフラ予約確定\nうえだ様\n2024/06/06(木) 18:00", matcher)
        )
        assertEquals(
            setOf("2025-01-30"),
            getAllMatches("- 変更日時: 2025-01-30 17:45", matcher)
        )
        assertEquals(
            setOf("2022.09.27"),
            getAllMatches("受取期限2022.09.27", matcher)
        )
        assertEquals(
            setOf("2025年"),
            getAllMatches("ご請求年月：2025年06月", matcher)
        )

        // Phone-like patterns
        assertEquals(
            setOf("0120-123-411"),
            getAllMatches("電話番号：0120-123-411", matcher)
        )

        // Currency-like patterns
        assertEquals(
            setOf("6000円"),
            getAllMatches("本日開始！最大6000円分ギフト券を条件達成で全員に進呈！", matcher)
        )

        // Query string-like patterns
        assertEquals(
            setOf("=20220128"),
            getAllMatches("utm_source=qlv&aam_medium=sms& utm campaign=20220128", matcher)
        )
    }

    @Test
    fun fromLanguageTag_japanese_matchesContextWordsPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.JAPANESE.toLanguageTag())!!
        val matcher = config.strongContextWordsMatcher.get()!!

        // One-time password
        matcher.reset("ワンタイムパスワード：7714")
        assertTrue(matcher.find())

        // Verification code
        matcher.reset("確認コード: 941749")
        assertTrue(matcher.find())

        // Verification number
        matcher.reset("ドットマネーの認証番号は 546577 です。30分間有効です。")
        assertTrue(matcher.find())

        // Coupon code
        matcher.reset("クーポンコード: AB5")
        assertFalse(matcher.find())

        // Amount to send
        matcher.reset("送金額2650円")
        assertFalse(matcher.find())

        // Confirmation number
        matcher.reset("確認番号：1610")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_german_matchesOtpPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.GERMAN.toLanguageTag())!!
        val matcher = config.otpMatcher.get()!!

        assertEquals(
            setOf("723413"),
            getAllMatches(
                "Die einmalige PIN ist 723413 für die Transaktion über 109.20 CHF bei SBB CFF FFS",
                matcher
            )
        )
        assertEquals(
            setOf("3161"),
            getAllMatches("Dein FREENOW Verifizierungscode lautet 3161.", matcher)
        )
        assertEquals(
            setOf("831-995"),
            getAllMatches("<#> Dein WhatsApp Business Code: 831-995", matcher)
        )
        assertEquals(
            setOf("116 389"),
            getAllMatches("116 389 ist dein Instagram-Code.", matcher)
        )

        // Too short (at least 4 digits for German)
        matcher.reset("175 ist Ihr Verifizierungscode für Doctolib.")
        assertFalse(matcher.find())

        // Too long
        matcher.reset("Abmelden: Sende OUT an 015735999356")
        assertFalse(matcher.find())
    }

    @Test
    fun fromLanguageTag_german_matchesFalsePositivePatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.GERMAN.toLanguageTag())!!
        val matcher = config.falsePositiveMatcher.get()!!

        // Date-like patterns
        assertEquals(
            setOf("11/11/2024"),
            getAllMatches("Flughafen Frankfurt P2/P3 Terminal 1 Parking, 11/11/2024.", matcher)
        )
        assertEquals(
            setOf("10.1.2025"),
            getAllMatches("Vergleich aktualisiert (10.1.2025)", matcher)
        )

        // Phone-like patterns
        assertEquals(
            setOf("0800 330 1341"),
            getAllMatches("Hier informieren oder 0800 330 1341. Ihre Telekom", matcher)
        )
        assertEquals(
            setOf("069 5 8000 8000"),
            getAllMatches("Bei Fragen:\n069 5 8000 8000 (24h Beratung)", matcher)
        )

        // Query string-like patterns
        assertEquals(
            setOf("=837163"),
            getAllMatches("online.de/verify-paymorrow?code=837163&token=Nh", matcher)
        )

        // Currency-like patterns
        assertEquals(
            setOf("2500 EUR"),
            getAllMatches("Wir haben Ihrem Konto 2500 EUR", matcher)
        )
        assertEquals(
            setOf("1000€"),
            getAllMatches("Willkommenspaket ist HIER und wartet noch. Bis zu 1000€ Bonus", matcher)
        )
    }

    @Test
    fun fromLanguageTag_german_matchesContextWordsPatterns() {
        val config = OtpMatcherLanguageConfig.fromLanguageTag(ULocale.GERMAN.toLanguageTag())!!
        val matcher = config.strongContextWordsMatcher.get()!!

        // Your phone verification code
        matcher.reset("Dein Telefonverifizierungscode")
        assertTrue(matcher.find())

        // Your SMS password: ######
        matcher.reset("Ihr SMS-Kennwort:\n74476")
        assertTrue(matcher.find())

        // #### is your confirmation code. The confirmation code expires in ## minutes.
        matcher.reset("8111 ist Ihr Bestätigungscode. Der Bestätigungscode läuft in 10 Minuten ab.")
        assertTrue(matcher.find())

        // Your discount code
        matcher.reset("Ihr Rabattcode lautet: 48ZD53")
        assertFalse(matcher.find())

        // Parking expired
        matcher.reset("Der Parkvorgang für NMSRJ3 ist abgelaufen")
        assertFalse(matcher.find())

        // Your promo code for your next order is
        matcher.reset("Ihr Promo-Code für Ihre nächste Bestellung lautet: SOL20DE.")
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
