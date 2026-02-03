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

import android.content.Context;
import android.content.pm.verify.domain.DomainOwner;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.view.textclassifier.TextLinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.textclassifier.utils.TextClassifierUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies whether a message is a WebOTP message.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class TextClassifierSmsWebOtpHandler {
    private static final String WEBOTP_REGEX = "(?:^|\\n)@([a-zA-Z0-9.-]+) #\\d";
    private static final Pattern WEBOTP_PATTERN = Pattern.compile(WEBOTP_REGEX);
    // Reduces the number of instantiated Matcher objects by reusing the same matcher within the
    // same thread to avoid unnecessary GCs.
    private static final ThreadLocal<Matcher> WEBOTP_MATCHER_CACHE =
            ThreadLocal.withInitial(() -> WEBOTP_PATTERN.matcher(""));
    private static final ArrayList<String> NO_OTP_TRUSTED_PACKAGES = new ArrayList<>();

    public static void addLink(@NonNull TextLinks.Request request,
            @NonNull TextLinks.Builder builder, @NonNull Context context) {
        final String text = request.getText().toString();
        Matcher matcher = WEBOTP_MATCHER_CACHE.get().reset(text);
        if (matcher.find()) {
            final Map<String, Float> entityScores = Collections.singletonMap(
                    TextClassifierUtils.TYPE_SMS_WEB_OTP, 1f);
            String domain = matcher.group(1);
            ArrayList<String> otpTrustedPackages = getOtpTrustedPackages(context,
                    request.getCallingPackageName(), domain);
            Bundle webOtpExtra = new Bundle();
            if (!otpTrustedPackages.isEmpty()) {
                ExtrasUtils.putOtpTrustedPackagesExtra(webOtpExtra, otpTrustedPackages);
            }
            builder.addLink(0, 0, entityScores, webOtpExtra);
        }
    }

    // Returns a list of trusted packages who can view or receive a Web OTP containing the
    // specified `domain`.
    // Returns an empty list if no trusted packages should view the Web OTP.
    private static ArrayList<String> getOtpTrustedPackages(@NonNull Context context,
            @Nullable String callingPackage, @NonNull String domain) {
        Set<String> otpTrustedPackages = new ArraySet<String>();
        try {
            UserManager userManager = context.getSystemService(UserManager.class);
            Set<UserHandle> visibleUsers = userManager.getVisibleUsers();
            for (UserHandle user : visibleUsers) {
                Context userContext = context.createContextAsUser(user, 0);
                DomainVerificationManager domainVerificationManager =
                        userContext.getSystemService(DomainVerificationManager.class);
                SortedSet<DomainOwner> verifiedOwners =
                        domainVerificationManager.getVerifiedOwnersForDomain(domain);
                for (DomainOwner owner : verifiedOwners) {
                    if (TextClassifierUtils.hasPackageVisibility(context, callingPackage,
                            owner.getPackageName())) {
                        otpTrustedPackages.add(owner.getPackageName());
                    }
                }
            }
        } catch(Exception e) {
            // Fail gracefully if any exception is encountered.
            return NO_OTP_TRUSTED_PACKAGES;
        }
        return new ArrayList(otpTrustedPackages);
    }
}
