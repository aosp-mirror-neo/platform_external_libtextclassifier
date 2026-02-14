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

import android.icu.util.ULocale
import android.os.Build.VERSION_CODES.BAKLAVA
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.textclassifier.TextLinks;
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.textclassifier.utils.TextClassifierUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = BAKLAVA)
@RequiresFlagsEnabled(
    Flags.FLAG_TEXT_CLASSIFIER_CHOICE_API_ENABLED, Flags.FLAG_ENABLE_OTP_IN_TEXT_CLASSIFIERS
)
class TextClassifierOtpHandlerTest {
    private val englishLanguageTag = listOf(ULocale.ENGLISH.toLanguageTag())

    @Test
    fun addOtpLink_isOtpMessage_linkAdded() {
        val message = "Your OTP code is 123456"
        val builder = TextLinks.Builder(message)

        TextClassifierOtpHandler.addOtpLink(message, builder, englishLanguageTag)
        val textLinks = builder.build()

        assertNotNull("TextLinks object should not be null", textLinks)
        assertNotNull("Links object should not be null", textLinks.links)

        val links = textLinks.links.toList()
        assertEquals("Exactly one link should be added", 1, links.size)

        val link = links[0]
        assertEquals("Exactly one entity should be added", 1, link.entityCount)
        assertEquals(TextClassifierUtils.TYPE_OTP, link.getEntity(0))
    }

    @Test
    fun addOtpLink_isNotOtpMessage_linkNotAdded() {
        val message = "Hello, it's me you're looking for."
        val builder = TextLinks.Builder(message)

        TextClassifierOtpHandler.addOtpLink(message, builder, englishLanguageTag)
        val textLinks = builder.build()

        assertNotNull("TextLinks object should not be null", textLinks)
        assertNotNull("Links object should not be null", textLinks.links)

        val links = textLinks.links.toList()
        assertEquals("No link should be added", 0, links.size)
    }
}
