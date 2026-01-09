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
        Mockito.doReturn(TextLanguage.Builder().putLocale(ULocale.ENGLISH, 0.9f).build())
            .`when`(tc)
            .detectLanguage(any())
    }

    private fun containsOtp(text: String): Boolean {
        return OtpDetector.containsOtp(
            text,
            this.tc,
        )
    }

    @Test
    fun testOtpDetection() {
        Assert.assertFalse(containsOtp("hello"))
        Assert.assertTrue(containsOtp("Your OTP code is 123456"))
    }

    @Test
    fun testOtpDetection_validPinPatterns() {
        val acceptedPinPatterns =
            listOf(
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
            Assert.assertTrue(containsOtp(otpMessage));
        }
    }

    @Test
    fun testContainsOtpLikePattern_length() {
        val tooShortNumOnly = "12"
        val minLenNumOnly = "123"
        val twoTriplets = "123 456"
        val tooShortTriplets = "12 345"
        val maxLen = "12345678"
        val tooLong = "123456789"

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(minLenNumOnly))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(maxLen))
        Assert.assertFalse(
            "$tooShortNumOnly is too short",
            OtpDetector.containsOtpLikePattern(tooShortNumOnly)
        )
        Assert.assertFalse(
            "$tooLong is too long",
            OtpDetector.containsOtpLikePattern(tooLong)
        )
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(twoTriplets))
        Assert.assertFalse(
            "$tooShortTriplets is too short",
            OtpDetector.containsOtpLikePattern(tooShortTriplets)
        )
    }

    @Test
    fun testContainsOtpLikePattern_rejectsNonRomanAlphabeticalChars() {
        val lowercase = "123ķ4"
        val uppercase = "123Ŀ4"
        val ideographicInMiddle = "12码45"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(lowercase))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(uppercase))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(ideographicInMiddle))
    }

    @Test
    fun testContainsOtpLikePattern_dashes() {
        val oneDash = "1-23456"
        val manyDashes = "1-23-456"
        val consecutiveDashes = "1--2345"
        val oopsAllDashes = "------"

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(oneDash))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(oneDash))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(manyDashes))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(consecutiveDashes))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(oopsAllDashes))
    }

    @Test
    fun testContainsOtpLikePattern_dateExclusion() {
        val date = "01-01-2001"
        val singleDigitDate = "1-1-2001"
        val twoDigitYear = "1-1-01"
        val dateWithOtpAfter = "1-1-01 is the date of your code 3425"
        val dateWithOtpBefore = "your code 54-234-3 was sent on 1-1-01"
        val otpWithDashesButInvalidDate = "34-58-30"
        val otpWithDashesButInvalidYear = "12-1-3089"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(date))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(singleDigitDate))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(twoDigitYear))

        Assert.assertTrue(OtpDetector.containsOtpLikePattern(dateWithOtpAfter))
        Assert.assertTrue(OtpDetector.containsOtpLikePattern(dateWithOtpBefore))
        Assert.assertTrue(
            OtpDetector.containsOtpLikePattern(otpWithDashesButInvalidDate)
        )
        Assert.assertTrue(
            OtpDetector.containsOtpLikePattern(otpWithDashesButInvalidYear)
        )
    }

    @Test
    fun testContainsOtpLikePattern_phoneExclusion() {
        val parens = "(888) 8888888"
        val allSpaces = "888 888 8888"
        val withDash = "(888) 888-8888"
        val allDashes = "888-888-8888"
        val allDashesWithParen = "(888)-888-8888"

        Assert.assertFalse(OtpDetector.containsOtpLikePattern(parens))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(allSpaces))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(withDash))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(allDashes))
        Assert.assertFalse(OtpDetector.containsOtpLikePattern(allDashesWithParen))
    }

    @Test
    fun testContainsOtp_falsePositiveExclusion() {
        // OTP: [888-8888] falsePositives=[] finalOtpCandidate=[1234]
        Assert.assertTrue(containsOtp("Your OTP is 888-8888"))

        // OTP: [1234, 888-8888] falsePositives=[(888) 888-8888] finalOtpCandidate=[1234]
        Assert.assertTrue(containsOtp("1234 is your OTP, call (888) 888-8888 for more info"))

        // OTP: [888-8888] falsePositives=[(888) 888-8888] finalOtpCandidate=[]
        Assert.assertFalse(containsOtp("Your OTP can't be shared at this point, please call (888) 888-8888"))

        // OTP: [1234, 01-01-2001] falsePositives=[01-01-2001] finalOtpCandidate=[1234]
        Assert.assertTrue(containsOtp("Your OTP code is 1234 and this is sent on 01-01-2001"))

        // OTP: [01-01-2001] falsePositives=[01-01-2001] finalOtpCandidate=[]
        Assert.assertFalse(containsOtp("Your OTP code is null and this is sent on 01-01-2001"))

        // OTP: ["ending with 1234"] falsePositives=["ending with 1234"] finalOtpCandidate=[]
        Assert.assertFalse(containsOtp("Your transaction with credit card ending with 1234 is rejected."))
    }

    @Test
    fun testContainsOtp_mustHaveNumber() {
        val noNums = "TEFHXES"
        Assert.assertFalse(containsOtp(noNums))
    }

    @Test
    fun testContainsOtp_invalidStart() {
        val invalidStarts =
            listOf(
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
            Assert.assertFalse(containsOtp(invalidOtp))
        }
    }

    @Test
    fun testContainsOtp_invalidEnds() {
        val invalidEnds =
            listOf(
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
            Assert.assertFalse(containsOtp(invalidOtp))
        }
    }

    @Test
    fun testContainsOtp_validStartAndEnd() {
        val validStartAndEnds =
            listOf(
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
            Assert.assertTrue(containsOtp(validOtp))
        }
    }

    @Test
    fun testContainsOtp_multipleFalsePositives() {
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
        Assert.assertFalse(containsOtp(multipleLongFp))
        Assert.assertFalse(containsOtp(multipleShortFp))
        Assert.assertTrue(containsOtp(multipleLongFpWithOtpBefore))
        Assert.assertTrue(containsOtp(multipleLongFpWithOtpAfter))
        Assert.assertTrue(containsOtp(multipleLongFpWithOtpBetween))
        Assert.assertTrue(containsOtp(multipleShortFpWithOtpBefore))
        Assert.assertTrue(containsOtp(multipleShortFpWithOtpAfter))
        Assert.assertTrue(containsOtp(multipleShortFpWithOtpBetween))
    }

    @Test
    fun testContainsOtp_withCodeKeyword_invalidContext() {
        val invalidCodeKeywordContexts =
            listOf(
                // Code is followed by an alphanumeric token, e.g. voucher/promo code.
                "Use code XMAS25 for a 15% off! Ref: 123456",
                "Use code:XMAS25 for a 15% off! Ref: 123456",
                "Use code: XMAS25 for a 15% off! Ref: 123456",
                "Use code 50OFF for a 50% off! Ref: 123456",
                "Use code: 50OFF for a 50% off! Ref: 123456",
            )
        for (invalidOtp in invalidCodeKeywordContexts) {
            Assert.assertFalse(containsOtp(invalidOtp))
        }
    }

    @Test
    fun testContainsOtp_withCodeKeyword_validContext() {
        val validCodeKeywordContexts =
            listOf(
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
            Assert.assertTrue(containsOtp(validOtp))
        }
    }


    @Test
    fun testContainsOtpCode_nonEnglish() {
        val textWithOtp = "1234 是您的一次性代碼" // 1234 is your one time code
        Assert.assertFalse(containsOtp(textWithOtp))
    }

    @Test
    fun testContainsOtp_englishSpecificRegex() {
        val englishFalsePositive = "This is a false positive 4543"
        val englishContextWords =
            listOf(
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

        Assert.assertFalse(containsOtp(englishFalsePositive))
        for (context in englishContextWords) {
            val englishTruePositive = "$context $englishFalsePositive"
            Assert.assertTrue(containsOtp(englishTruePositive))
        }
        for (context in englishContextWordsCase) {
            val englishTruePositive = "$context $englishFalsePositive"
            Assert.assertTrue(containsOtp(englishTruePositive))
        }
        for (falseContext in englishContextSubstrings) {
            val anotherFalsePositive = "$falseContext $englishFalsePositive"
            Assert.assertFalse(containsOtp(anotherFalsePositive))
        }
        Assert.assertTrue(containsOtp(codeInNextSentence))
        Assert.assertTrue(containsOtp(codeInSentenceAfterNewline))
    }
}
