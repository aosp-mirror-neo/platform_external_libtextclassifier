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
package com.android.textclassifier.utils;

import android.os.Build;
import android.permission.flags.Flags;
import android.view.textclassifier.TextClassifier;

public class TextClassifierUtils {
    public static final String TYPE_OTP = getOtpType();
    public static final String TYPE_SMS_RETRIEVER_OTP = getSmsRetrieverOtpType();
    public static final String TYPE_SMS_WEB_OTP = getSmsWebOtpType();

    private static String getOtpType() {
        if (!isAtLeastB() || !Flags.textClassifierChoiceApiEnabled()) {
            return "otp";
        }
        return TextClassifier.TYPE_OTP;
    }

    private static String getSmsRetrieverOtpType() {
        if (!isAtLeast25Q4() || !com.android.internal.telephony.flags.Flags.redactOtpSmsApi()) {
            return "sms_retriever_otp";
        }
        return TextClassifier.TYPE_SMS_RETRIEVER_OTP;
    }

    private static String getSmsWebOtpType() {
        if (!android.view.flags.Flags.redactWebOtpSmsApi()) {
            return "sms_web_otp";
        }
        return TextClassifier.TYPE_SMS_WEB_OTP;
    }

    public static boolean isAtLeastB() {
        return Build.VERSION.CODENAME.equals("Baklava")
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA;
    }

    public static boolean isOtpClassificationEnabled() {
        return isAtLeastB() && Flags.textClassifierChoiceApiEnabled()
                && Flags.enableOtpInTextClassifiers();
    }

    public static boolean isAtLeast25Q4() {
        // Since SDK_INT_FULL doesn't exist until 36, we are ensuring v36 first
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1;
    }
}
