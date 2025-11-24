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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.textclassifier.TextLinks;

import androidx.test.filters.SdkSuppress;

import com.android.textclassifier.testing.FakeContextBuilder;
import com.android.textclassifier.utils.AppHashHelper;
import com.android.textclassifier.utils.TextClassifierUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
@SdkSuppress(minSdkVersion = BAKLAVA)
@RequiresFlagsEnabled(com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS_API)
public class TextClassifierSmsRetrieverHandlerTest {
    private static final String VALID_APP_HASH_1 = "VALIDHASH_1"; // 11 characters
    private static final String VALID_APP_HASH_2 = "VALIDHASH_2"; // 11 characters
    private static final String INVALID_APP_HASH = "INVALIDHASH"; // 11 characters
    private static final String TEST_APP_1 = "com.test.app.one";
    private static final String TEST_APP_2 = "com.test.app.two";
    private static final String MESSAGE_PREFIX = "Your OTP is: ";

    private Context mContext;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        AppHashHelper.sAppHashCache.clear();
        AppHashHelper.markAsLoadedFromPackageManagerForTest();
        mContext = new FakeContextBuilder().build();
        TextClassifierSmsRetrieverHandler.bypassPackageVisibilityForTesting = true;
    }

    @After
    public void tearDown() {
        TextClassifierSmsRetrieverHandler.bypassPackageVisibilityForTesting = false;
    }

    @Test
    public void addLink_validHash_addsLink() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String message = MESSAGE_PREFIX + VALID_APP_HASH_1;
        TextLinks.Request request = new TextLinks.Request.Builder(message).build();
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsRetrieverHandler.addLink(request, builder, mContext);
        TextLinks textLinks = builder.build();

        Assert.assertNotNull("TextLinks object should not be null", textLinks);
        Assert.assertNotNull("Links object should not be null", textLinks.getLinks());
        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("Exactly one link should be added", 1, links.size());

        TextLinks.TextLink link = links.get(0);
        Assert.assertEquals("Exactly one entity should be added", 1, link.getEntityCount());
        assertEquals(TextClassifierUtils.TYPE_SMS_RETRIEVER_OTP, link.getEntity(0));

        assertEquals(TEST_APP_1,
                link.getExtras().getString(ExtrasUtils.EXTRA_SMS_RETRIEVER_HASH_MATCHED_PACKAGE));
    }

    @Test
    public void addLink_invalidHash_doesNotAddLink() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String message = MESSAGE_PREFIX + INVALID_APP_HASH;
        TextLinks.Request request = new TextLinks.Request.Builder(message).build();
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsRetrieverHandler.addLink(request, builder, mContext);
        TextLinks textLinks = builder.build();

        Assert.assertNotNull("TextLinks object should not be null", textLinks);
        Assert.assertNotNull("Links object should not be null", textLinks.getLinks());
        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("No link should be added", 0, links.size());
    }

    @Test
    public void containsSmsRetrieverHash_validHashAtEnd_returnsTrue() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = "Your code is 123456" + VALID_APP_HASH_1;
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNotNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_validHashInMiddle_returnsTrue() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = "Some text " + VALID_APP_HASH_1 + " more text";
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNotNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_validHashAtBeginning_returnsTrue() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = VALID_APP_HASH_1 + " Your code is 123456";
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNotNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_multipleValidHashes_returnsTrue() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_2, TEST_APP_2);
        String text = "First hash: " + VALID_APP_HASH_1 + " Second hash: " + VALID_APP_HASH_2;
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNotNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_noMatchingHash_returnsFalse() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = "This message does not contain a valid hash.";
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_textShorterThanHashLength_returnsFalse() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = "Short"; // Length < HASH_LENGTH
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_textExactlyHashLength_matching_returnsTrue() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = VALID_APP_HASH_1; // Exactly HASH_LENGTH
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNotNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_textExactlyHashLength_nonMatching_returnsFalse() {
        AppHashHelper.sAppHashCache.put(VALID_APP_HASH_1, TEST_APP_1);
        String text = INVALID_APP_HASH; // Exactly HASH_LENGTH, but not in set
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }

    @Test
    public void containsSmsRetrieverHash_emptyHashCache_returnsFalse() {
        String text = MESSAGE_PREFIX + VALID_APP_HASH_1;
        TextLinks.Request request = new TextLinks.Request.Builder(text).build();

        assertNull(
                TextClassifierSmsRetrieverHandler.findSmsRetrieverPackageName(mContext, request));
    }
}
