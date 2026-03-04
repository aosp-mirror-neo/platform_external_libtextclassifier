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
package com.android.textclassifier

import android.content.Context
import android.icu.util.ULocale
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLanguage
import androidx.collection.LruCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.textclassifier.common.ModelFile
import com.android.textclassifier.common.TextClassifierSettings
import com.android.textclassifier.testing.FakeContextBuilder
import com.android.textclassifier.testing.TestingDeviceConfig
import com.android.textclassifier.utils.TextClassifierUtils
import com.google.android.textclassifier.AnnotatorModel
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class OtpDetectorTest {
    private val englishLanguageTag = ULocale.ENGLISH.toLanguageTag()
    private val englishLanguageTagList = listOf(englishLanguageTag)

    private val koreanLanguageTag = ULocale.KOREAN.toLanguageTag()
    private val koreanLanguageTagList = listOf(koreanLanguageTag)

    private lateinit var context: Context
    private lateinit var deviceConfig: TestingDeviceConfig
    private lateinit var settings: TextClassifierSettings
    private lateinit var annotatorModelCache: LruCache<ModelFile, AnnotatorModel>
    private lateinit var tc: TextClassifier

    @Before
    fun setup() {
        Assume.assumeTrue(TextClassifierUtils.isOtpClassificationEnabled())

        MockitoAnnotations.initMocks(this)
        this.context =
            FakeContextBuilder()
                .setAllIntentComponent(FakeContextBuilder.DEFAULT_COMPONENT)
                .setAppLabel(FakeContextBuilder.DEFAULT_COMPONENT.packageName, "Test app")
                .build()
        this.deviceConfig = TestingDeviceConfig()
        this.settings = TextClassifierSettings(deviceConfig, /* isWear= */ false)
        this.annotatorModelCache = LruCache(2)
        this.tc = Mockito.mock(TextClassifier::class.java)
    }

    @Test
    fun containsOtp_providedTextClassifier_correctResult() {
        Mockito.doReturn(TextLanguage.Builder().putLocale(ULocale.ENGLISH, 0.9f).build())
            .`when`(tc)
            .detectLanguage(any())
        Assert.assertFalse(OtpDetector.containsOtp("hello", this.tc))
        Assert.assertTrue(OtpDetector.containsOtp("Your OTP code is 123456", this.tc))
    }

    @Test
    fun containsOtp_providedTextClassifier_correctResultForLanguageAgnosticOtpSignature() {
        // Intentionally returning a language tag with low confidence.
        Mockito.doReturn(TextLanguage.Builder().putLocale(ULocale.ENGLISH, 0.3f).build())
            .`when`(tc)
            .detectLanguage(any())
        Assert.assertFalse(OtpDetector.containsOtp("Your OTP code is 123456", this.tc))
        Assert.assertTrue(OtpDetector.containsOtp("<#> Your OTP code is 123456", this.tc))
    }

    @Test
    fun containsOtp_english_validPinPatterns() {
        val acceptedPinPatterns = listOf(
            // 3 to 8-digit numeric pin.
            "Your OTP is 123",
            "Your OTP is 1234",
            "Your OTP is 12345",
            "Your OTP is 123456",
            "Your OTP is 1234567",
            "Your OTP is 12345678",

            // Numeric pin preceded by a letter tag.
            "Your OTP is G-1234",
            "Your OTP is G-123456",

            // Numeric pin preceded by 3 to 5-letter tag.
            "Your OTP is ABC-1234",
            "Your OTP is ABC-123456",
            "Your OTP is abcde-123456",

            // 6-digit numeric pin with space in the middle.
            "Your OTP is 123 456",

            // 6 digits with dash in between.
            "Your OTP is 1-2-3-4-5-6",
            "Your OTP is 123-456",
            "Your OTP is 12-3456",
            "Your OTP is 12345-6",
        )

        for (otpMessage in acceptedPinPatterns) {
            Assert.assertTrue(OtpDetector.containsOtp(otpMessage, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtpLikePattern_english_length() {
        val tooShortNumOnly = "12"
        val minLenNumOnly = "123"
        val twoTriplets = "123 456"
        val tooShortTriplets = "12 345"
        val maxLen = "12345678"
        val tooLong = "123456789"

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(minLenNumOnly, englishLanguageTag))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(maxLen, englishLanguageTag))
        Assert.assertFalse(
            "$tooShortNumOnly is too short",
            OtpDetector.containsOtpLikePattern(tooShortNumOnly, englishLanguageTag)
        )
        Assert.assertFalse(
            "$tooLong is too long",
            OtpDetector.containsOtpLikePattern(tooLong, englishLanguageTag)
        )
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(twoTriplets, englishLanguageTag))
        Assert.assertFalse(
            "$tooShortTriplets is too short",
            OtpDetector.containsOtpLikePattern(tooShortTriplets, englishLanguageTag)
        )
    }

    @Test
    fun containsOtpLikePattern_english_rejectsNonRomanAlphabeticalChars() {
        val lowercase = "123ķ4"
        val uppercase = "123Ŀ4"
        val ideographicInMiddle = "12码45"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(lowercase, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(uppercase, englishLanguageTag))
        Assert.assertFalse(
            OtpDetector.containsOtpLikePattern(ideographicInMiddle, englishLanguageTag)
        )
    }

    @Test
    fun containsOtpLikePattern_english_dashes() {
        val oneDash = "1-23456"
        val manyDashes = "1-23-456"
        val consecutiveDashes = "1--2345"
        val oopsAllDashes = "------"

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(oneDash, englishLanguageTag))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(oneDash, englishLanguageTag))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(manyDashes, englishLanguageTag))
        Assert.assertFalse(
            OtpDetector.containsOtpLikePattern(consecutiveDashes, englishLanguageTag)
        )
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(oopsAllDashes, englishLanguageTag))
    }

    @Test
    fun containsOtpLikePattern_english_dateExclusion() {
        val date = "01-01-2001"
        val singleDigitDate = "1-1-2001"
        val twoDigitYear = "1-1-01"
        val dateWithOtpAfter = "1-1-01 is the date of your code 3425"
        val dateWithOtpBefore = "your code 54-234-3 was sent on 1-1-01"
        val otpWithDashesButInvalidDate = "34-58-30"
        val otpWithDashesButInvalidYear = "12-1-3089"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(date, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(singleDigitDate, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(twoDigitYear, englishLanguageTag))

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(dateWithOtpAfter, englishLanguageTag))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(dateWithOtpBefore, englishLanguageTag))
        Assert.assertTrue(
            OtpDetector.containsOtpLikePattern(otpWithDashesButInvalidDate, englishLanguageTag)
        )
        Assert.assertTrue(
            OtpDetector.containsOtpLikePattern(otpWithDashesButInvalidYear, englishLanguageTag)
        )
    }

    @Test
    fun containsOtpLikePattern_english_phoneExclusion() {
        val parens = "(888) 8888888"
        val allSpaces = "888 888 8888"
        val withDash = "(888) 888-8888"
        val allDashes = "888-888-8888"
        val allDashesWithParen = "(888)-888-8888"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(parens, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(allSpaces, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(withDash, englishLanguageTag))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(allDashes, englishLanguageTag))
        Assert.assertFalse(
            OtpDetector.containsOtpLikePattern(allDashesWithParen, englishLanguageTag)
        )
    }

    @Test
    fun containsOtpLikePattern_english_falsePositiveExclusion() {
        // OTP: [888-8888] falsePositives=[] finalOtpCandidate=[1234]
        Assert.assertTrue(OtpDetector.containsOtp("Your OTP is 888-8888", englishLanguageTagList))

        // OTP: [1234, 888-8888] falsePositives=[(888) 888-8888] finalOtpCandidate=[1234]
        Assert.assertTrue(
            OtpDetector.containsOtp(
                "1234 is your OTP, call (888) 888-8888 for more info", englishLanguageTagList
            )
        )

        // OTP: [888-8888] falsePositives=[(888) 888-8888] finalOtpCandidate=[]
        Assert.assertFalse(
            OtpDetector.containsOtp(
                "Your OTP can't be shared at this point, please call (888) 888-8888",
                englishLanguageTagList
            )
        )

        // OTP: [1234, 01-01-2001] falsePositives=[01-01-2001] finalOtpCandidate=[1234]
        Assert.assertTrue(
            OtpDetector.containsOtp(
                "Your OTP code is 1234 and this is sent on 01-01-2001", englishLanguageTagList
            )
        )

        // OTP: [01-01-2001] falsePositives=[01-01-2001] finalOtpCandidate=[]
        Assert.assertFalse(
            OtpDetector.containsOtp(
                "Your OTP code is null and this is sent on 01-01-2001", englishLanguageTagList
            )
        )

        // OTP: ["ending with 1234"] falsePositives=["ending with 1234"] finalOtpCandidate=[]
        Assert.assertFalse(
            OtpDetector.containsOtp(
                "Your transaction with credit card ending with 1234 is rejected.",
                englishLanguageTagList
            )
        )
    }

    @Test
    fun containsOtp_english_mustHaveNumber() {
        val noNums = "TEFHXES"
        Assert.assertFalse(OtpDetector.containsOtp(noNums, englishLanguageTagList))
    }

    @Test
    fun containsOtp_english_invalidStart() {
        val invalidStarts = listOf(
            // No space at the start.
            "Your OTP is123456",
            "Your OTP ABC123456",

            // Starting with a space that is preceded by a number.
            "Your OTP is 4 123456",
            "Your OTP is 123 123456",

            // Starting with a colon that is preceded by a number.
            "Your OTP is 4:123456",
            "Your OTP is 123:123456",

            // Starting with a dash that is preceded by a number.
            "Your OTP is 4-12345678",
            "Your OTP is 123-12345678",

            // Starting with an unsupported symbol.
            "Your OTP is $123456",
            "Your OTP is %123456",
            "Your OTP is ^123456",
        )
        for (invalidOtp in invalidStarts) {
            Assert.assertFalse(OtpDetector.containsOtp(invalidOtp, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtp_english_invalidEnds() {
        val invalidEnds = listOf(
            // No space at the end.
            "Your OTP is 123456for real",
            "Your OTP is 123456ABC",

            // Ending with a space that is followed by a number.
            "Your OTP is 123456 4",
            "Your OTP is 123456 123456",

            // Ending with a dash.
            "Your OTP is 123456-",

            // Ending with an underscore.
            "Your OTP is 123456_",

            // Ending with an unsupported symbol.
            "Your OTP is 123456$",
            "Your OTP is 123456%",
            "Your OTP is 123456^",
        )
        for (invalidOtp in invalidEnds) {
            Assert.assertFalse(OtpDetector.containsOtp(invalidOtp, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtp_english_validStartAndEnd() {
        val validStartAndEnds = listOf(
            // Starting with colon preceded by a letter.
            "Your OTP is:123456",

            // Starting with a newline character.
            "Your OTP is \n123456",
            "Your OTP is\n123456",
            "Your OTP is\n\n123456",

            // Starting/ending with quote.
            "Your OTP is '123456'",
            "Your OTP is '123456",
            "Your OTP is 123456'",

            // Starting/ending with double quote.
            "Your OTP is \"123456\"",
            "Your OTP is \"123456",
            "Your OTP is 123456\"",

            // Starting/ending with square bracket.
            "Your OTP is [123456]",
            "Your OTP is [123456",
            "Your OTP is 123456]",

            // Starting/ending with ideographic character.
            "Your OTP is码123456",
            "Your OTP is 码123456",
            "Your OTP is码345821码",
            "Your OTP is 码345821码",
            "Your OTP is 码 345821",
            "Your OTP is 345821码",

            // Ending with period.
            "Your OTP is 123456.",

            // Starting/ending with parens.
            "Your OTP is (123456)",
            "Your OTP is (123456",
            "Your OTP is 123456)",

            // Starting with a dash preceeded by a letter.
            "Your OTP is XYZ-123456",
            "Your OTP is G-123456",

            // Starting with a closed square bracket.
            "[Company]123456 is your OTP",
        )
        for (validOtp in validStartAndEnds) {
            Assert.assertTrue(OtpDetector.containsOtp(validOtp, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtp_english_multipleFalsePositives() {
        val otp = "code 1543 code"
        val longFp = "888-777-6666"
        val shortFp = "34ess"
        val multipleLongFp = "$longFp something something $longFp"
        val multipleLongFpWithOtpBefore = "$otp $multipleLongFp"
        val multipleLongFpWithOtpAfter = "$multipleLongFp $otp"
        val multipleLongFpWithOtpBetween = "$longFp $otp $longFp"
        val multipleShortFp = "$shortFp something something $shortFp"
        val multipleShortFpWithOtpBefore = "$otp $multipleShortFp"
        val multipleShortFpWithOtpAfter = "$otp $multipleShortFp"
        val multipleShortFpWithOtpBetween = "$shortFp $otp $shortFp"
        Assert.assertFalse(OtpDetector.containsOtp(multipleLongFp, englishLanguageTagList))
        Assert.assertFalse(OtpDetector.containsOtp(multipleShortFp, englishLanguageTagList))
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleLongFpWithOtpBefore, englishLanguageTagList)
        )
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleLongFpWithOtpAfter, englishLanguageTagList)
        )
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleLongFpWithOtpBetween, englishLanguageTagList)
        )
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleShortFpWithOtpBefore, englishLanguageTagList)
        )
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleShortFpWithOtpAfter, englishLanguageTagList)
        )
        Assert.assertTrue(
            OtpDetector.containsOtp(multipleShortFpWithOtpBetween, englishLanguageTagList)
        )
    }

    @Test
    fun containsOtp_english_withCodeKeyword_invalidContext() {
        val invalidCodeKeywordContexts = listOf(
            // Code is followed by an alphanumeric token, e.g. voucher/promo code.
            "Use code XMAS25 for a 15% off! Ref: 123456",
            "Use code:XMAS25 for a 15% off! Ref: 123456",
            "Use code: XMAS25 for a 15% off! Ref: 123456",
            "Use code 50OFF for a 50% off! Ref: 123456",
            "Use code: 50OFF for a 50% off! Ref: 123456",
        )
        for (invalidOtp in invalidCodeKeywordContexts) {
            Assert.assertFalse(OtpDetector.containsOtp(invalidOtp, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtp_english_withCodeKeyword_validContext() {
        val validCodeKeywordContexts = listOf(
            // Code that is not followed by an alphanumeric token.
            "Use code 123456",
            "Use code: 123456",
            "Use code:123456",
            "123456 is your code for the app",
            "123456 is your code ABCDEF",
            "123456 is your code:ABCDEF",
            "123456 is your code: ABCDEF",
            "123456 is your code",
            "123456 is your code:",
        )
        for (validOtp in validCodeKeywordContexts) {
            Assert.assertTrue(OtpDetector.containsOtp(validOtp, englishLanguageTagList))
        }
    }

    @Test
    fun containsOtp_english_englishSpecificRegex() {
        val englishFalsePositive = "This is a false positive 4543"
        val englishContextWords = listOf(
            "login",
            "log in",
            "2fa",
            "authenticate",
            "auth",
            "authentication",
            "tan",
            "password",
            "passcode",
            "two factor",
            "two-factor",
            "2factor",
            "2 factor",
            "pin",
            "one time",
        )
        val englishContextWordsCase = listOf("LOGIN", "logIn", "LoGiN")
        // Strings with a context word somewhere in the substring
        val englishContextSubstrings = listOf("pins", "gaping", "backspin")
        val codeInNextSentence = "context word: code. This sentence has the actual value of 434343"
        val codeInSentenceAfterNewline = "your code is \n 34343"

        Assert.assertFalse(OtpDetector.containsOtp(englishFalsePositive, englishLanguageTagList))
        for (context in englishContextWords) {
            val englishTruePositive = "$context $englishFalsePositive"
            Assert.assertTrue(OtpDetector.containsOtp(englishTruePositive, englishLanguageTagList))
        }
        for (context in englishContextWordsCase) {
            val englishTruePositive = "$context $englishFalsePositive"
            Assert.assertTrue(OtpDetector.containsOtp(englishTruePositive, englishLanguageTagList))
        }
        for (falseContext in englishContextSubstrings) {
            val anotherFalsePositive = "$falseContext $englishFalsePositive"
            Assert.assertFalse(
                OtpDetector.containsOtp(anotherFalsePositive, englishLanguageTagList)
            )
        }
        Assert.assertTrue(OtpDetector.containsOtp(codeInNextSentence, englishLanguageTagList))
        Assert.assertTrue(
            OtpDetector.containsOtp(codeInSentenceAfterNewline, englishLanguageTagList)
        )
    }

    @Test
    fun containsOtp_english_nonEnglishFalse() {
        val textWithOtp = "1234 是您的一次性代碼" // 1234 is your one time code
        Assert.assertFalse(OtpDetector.containsOtp(textWithOtp, englishLanguageTagList))
    }

    @Test
    fun containsOtp_korean_detectsOtpWithValidContextWord() {
        val messagesWithOtp = listOf(
            "[Web발신] [원스토어] 인증번호는 [411090]입니다.",
            "본인확인 인증번호는 [171611]입니다",
            "[국외발신]\nNetflix 인증 코드: 321654",
            "[국외발신]\n111444은(는) 확인 코드이며 보내온 곳은 Payoneer",
            "[SCI평가정보] 본인인증번호는 664067입니다. 정확히 입력해주세요.",
            "[국제발신]649640는귀하의 Shopping OTP입니다.",
        )
        for (message in messagesWithOtp) {
            Assert.assertTrue(OtpDetector.containsOtp(message, koreanLanguageTagList))
        }
    }

    @Test
    fun containsOtp_korean_doesNotDetectNonOtp() {
        val messagesWithoutOtp = listOf(
            "고객센터 1899-0522", // Phone number
            "유효기간 : 2025-06-10 ~ 2025-08-09", // Date
            "완료일자 : 2022년 12월 30일", // Date
            "- 결제일시 : 2024. 12. 18. 16:26", // Date
        )
        for (message in messagesWithoutOtp) {
            Assert.assertFalse(OtpDetector.containsOtp(message, koreanLanguageTagList))
        }

        // Avoid common false positives
        val validContextWord = "승인 번호"
        for (message in messagesWithoutOtp) {
            Assert.assertFalse(
                OtpDetector.containsOtp("$validContextWord $message", koreanLanguageTagList)
            )
            Assert.assertFalse(
                OtpDetector.containsOtp("$message $validContextWord", koreanLanguageTagList)
            )
        }
    }

    @Test
    fun containsOtp_detectsLanguageAgnosticOtpSignature() {
        val validLanguageAgnosticOtp = listOf(
            "<#> Your MyApp code is 123456",
            "[#] รหัสผ่านของคุณคือ 888555", // Your password is 888555
        )
        for (validOtp in validLanguageAgnosticOtp) {
            // When an unsupported language tag is provided.
            Assert.assertTrue(OtpDetector.containsOtp(validOtp, listOf("some-unsupported-lang")))
            // When no language tag is provided.
            Assert.assertTrue(OtpDetector.containsOtp(validOtp, listOf()))
        }

        val invalidLanguageAgnosticOtp = listOf(
            // When <#> is not at the very beginning of the text message.
            // Translation: [Web transmission] Please enter KR Securities authentication number [789012].
            "[Web발신] <#>KR증권 인증번호 [789012]을 입력해주세요.",
            "[Web발신]\n<#>KR증권 인증번호 [789012]을 입력해주세요.",
        )
        for (invalidOtp in invalidLanguageAgnosticOtp) {
            Assert.assertFalse(OtpDetector.containsOtp(invalidOtp, listOf("some-unsupported-lang")))
            Assert.assertFalse(OtpDetector.containsOtp(invalidOtp, listOf()))
        }
    }
}
