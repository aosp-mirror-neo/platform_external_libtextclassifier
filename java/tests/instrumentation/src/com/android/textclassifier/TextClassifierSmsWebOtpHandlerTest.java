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
import android.view.textclassifier.TextLinks;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.textclassifier.utils.TextClassifierUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = BAKLAVA)
@RequiresFlagsEnabled({
    com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS_API,
    android.view.flags.Flags.FLAG_REDACT_WEB_OTP_SMS_API})
public class TextClassifierSmsWebOtpHandlerTest {
    private Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void testClassifyWebOtp() {
        String message = "Hello there!\n\n@domain.test #123456";
        TextLinks.Request request = new TextLinks.Request.Builder(message).build();
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsWebOtpHandler.addLink(request, builder, context);
        TextLinks textLinks = builder.build();

        Assert.assertNotNull("TextLinks object should not be null", textLinks);
        Assert.assertNotNull("Links object should not be null", textLinks.getLinks());
        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("Exactly one link should be added", 1, links.size());

        TextLinks.TextLink link = links.get(0);
        Assert.assertEquals("Exactly one entity should be added", 1, link.getEntityCount());
        assertEquals(TextClassifierUtils.TYPE_SMS_WEB_OTP, link.getEntity(0));

        assertEquals(null,
                link.getExtras().getStringArrayList(
                        TextClassifierUtils.EXTRA_OTP_TRUSTED_PACKAGES));
    }

    @Test
    public void testClassifyWebOtp_nonWebOtp() {
        String message = "Hello there!";
        TextLinks.Request request = new TextLinks.Request.Builder(message).build();
        TextLinks.Builder builder = new TextLinks.Builder(message);

        TextClassifierSmsWebOtpHandler.addLink(request, builder, context);
        TextLinks textLinks = builder.build();

        List<TextLinks.TextLink> links = textLinks.getLinks().stream().toList();
        Assert.assertEquals("Should be empty", 0, links.size());
    }
}
