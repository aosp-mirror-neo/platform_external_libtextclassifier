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

import static android.os.Build.VERSION_CODES.BAKLAVA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.textclassifier.TextLinks;

import androidx.test.filters.SdkSuppress;

import com.android.textclassifier.testing.FakeContextBuilder;
import com.android.textclassifier.utils.AppHashHelper;
import com.android.textclassifier.utils.TextClassifierUtils;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
@SdkSuppress(minSdkVersion=BAKLAVA)
public class TextClassifierSmsRetrieverHandlerTest {
    private static final String VALID_APP_HASH_1 = "VALIDHASH_1"; // 11 characters
    private static final String VALID_APP_HASH_2 = "VALIDHASH_2"; // 11 characters
    private static final String INVALID_APP_HASH = "INVALIDHASH"; // 11 characters
    private static final String MESSAGE_PREFIX = "Your OTP is: ";

    private Context mContext;

    @Before
    public void setUp() {
        Assume.assumeTrue(TextClassifierUtils.isAtLeastB());
        Assume.assumeTrue(com.android.internal.telephony.flags.Flags.redactOtpSmsApi());
        AppHashHelper.clearAllHashes();
        AppHashHelper.markAsLoadedFromPackageManagerForTest();
        mContext = new FakeContextBuilder().build();
    }

    @Test
    public void addLink_validHash_addsLink() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String message = MESSAGE_PREFIX + VALID_APP_HASH_1;
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsRetrieverHandler.addLink(message, builder, mContext);
        TextLinks textLinks = builder.build();

        Assert.assertNotNull("TextLinks object should not be null", textLinks);
        Assert.assertNotNull("Links object should not be null", textLinks.getLinks());
        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("Exactly one link should be added", 1, links.size());

        TextLinks.TextLink link = links.get(0);
        Assert.assertEquals("Exactly one entity should be added", 1, link.getEntityCount());
        assertEquals(TextClassifierUtils.TYPE_SMS_RETRIEVER_OTP, link.getEntity(0));
    }

    @Test
    public void addLink_invalidHash_doesNotAddLink() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String message = MESSAGE_PREFIX + INVALID_APP_HASH;
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsRetrieverHandler.addLink(message, builder, mContext);
        TextLinks textLinks = builder.build();

        Assert.assertNotNull("TextLinks object should not be null", textLinks);
        Assert.assertNotNull("Links object should not be null", textLinks.getLinks());
        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("No link should be added", 0, links.size());
    }

    @Test
    public void containsSmsRetrieverHash_validHashAtEnd_returnsTrue() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = "Your code is 123456" + VALID_APP_HASH_1;

        assertTrue(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_validHashInMiddle_returnsTrue() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = "Some text " + VALID_APP_HASH_1 + " more text";

        assertTrue(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_validHashAtBeginning_returnsTrue() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = VALID_APP_HASH_1 + " Your code is 123456";

        assertTrue(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_multipleValidHashes_returnsTrue() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        AppHashHelper.addHash(VALID_APP_HASH_2);
        String text = "First hash: " + VALID_APP_HASH_1 + " Second hash: " + VALID_APP_HASH_2;

        assertTrue(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_noMatchingHash_returnsFalse() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = "This message does not contain a valid hash.";

        assertFalse(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_textShorterThanHashLength_returnsFalse() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = "Short"; // Length < HASH_LENGTH

        assertFalse(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_textExactlyHashLength_matching_returnsTrue() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = VALID_APP_HASH_1; // Exactly HASH_LENGTH

        assertTrue(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_textExactlyHashLength_nonMatching_returnsFalse() {
        AppHashHelper.addHash(VALID_APP_HASH_1);
        String text = INVALID_APP_HASH; // Exactly HASH_LENGTH, but not in set

        assertFalse(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }

    @Test
    public void containsSmsRetrieverHash_emptyHashCache_returnsFalse() {
        String text = MESSAGE_PREFIX + VALID_APP_HASH_1;

        assertFalse(TextClassifierSmsRetrieverHandler.containsSmsRetrieverHash(text));
    }
}
