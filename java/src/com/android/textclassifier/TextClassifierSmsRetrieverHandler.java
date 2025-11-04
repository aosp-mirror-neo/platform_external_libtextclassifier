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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.textclassifier.TextLinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.textclassifier.utils.AppHashHelper;
import com.android.textclassifier.utils.TextClassifierUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Map;

/**
 * Handles messages that conform to the SMS Retriever API format.
 * <p>
 * This class checks if the input {@code text} contains a known 11-character app hash, if so, adds
 * a {@link TextLinks.Link} of type
 * {@link android.view.textclassifier.TextClassifier#TYPE_SMS_RETRIEVER_OTP}.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class TextClassifierSmsRetrieverHandler {
    private static final int HASH_LENGTH = 11;

    @VisibleForTesting
    protected static boolean bypassPackageVisibilityForTesting = false;

    /**
     * Analyzes the given text for an SMS Retriever app hash and, if found, adds a
     * corresponding {@link TextLinks.Link} to the builder.
     * <p>
     * This method first invokes the app hashes load, then searches the text from {@code request}
     * for a known
     * 11-character app hash.
     * <p>
     * If a valid hash is found, a link spanning the entire message is added to the
     * {@code builder} with the entity type {@link TextClassifierUtils#TYPE_SMS_RETRIEVER_OTP}.
     * The extras bundle of the link will contain the package name of the app to which hash has
     * been matched, with the key {@link ExtrasUtils#EXTRA_APP_HASH_PACKAGE_NAME}.
     *
     * @param request The {@link TextLinks.Request} request object containing the text to be
     *                analyzed.
     * @param builder The {@link TextLinks.Builder} to which a new link will be added if the
     *                message is identified.
     * @param context The {@link Context} used to access system services like
     *                {@code PackageManager} for loading app hashes if they are not already cached.
     */
    public static void addLink(@NonNull TextLinks.Request request,
            @NonNull TextLinks.Builder builder,
            @NonNull Context context) {
        AppHashHelper.ensureLoaded(context);
        String packageName = findSmsRetrieverPackageName(context, request);
        if (packageName == null) {
            return;
        }
        final Map<String, Float> entityScores = Collections.singletonMap(
                TextClassifierUtils.TYPE_SMS_RETRIEVER_OTP, 1f);
        Bundle extras = new Bundle();
        ExtrasUtils.putAppHashMatchedPackageNameExtra(extras, packageName);
        builder.addLink(0, 0, entityScores, extras);
    }

    @VisibleForTesting
    @Nullable
    protected static String findSmsRetrieverPackageName(@NonNull Context context,
            @NonNull TextLinks.Request request) {
        String text = request.getText().toString();
        if (text.length() < HASH_LENGTH) {
            return null;
        }
        for (int i = text.length() - HASH_LENGTH; i >= 0; i--) {
            String sub = text.substring(i, i + HASH_LENGTH);
            String packageName = AppHashHelper.getPackageNameForHash(sub);
            if (packageName != null && hasPackageVisibility(context,
                    request.getCallingPackageName(), packageName)) {
                return packageName;
            }
        }
        return null;
    }

    /**
     * Verifies if the calling package is allowed to see the target package.
     *
     * Performance: Highly efficient due to caching. The first execution may incur a one-time
     * latency but all subsequent calls are instantaneous. Additionally, this is only invoked as a
     * final validation step once package has been successfully identified.
     */
    private static boolean hasPackageVisibility(Context context, String callingPackageName,
            String targetPackageName) {
        if (bypassPackageVisibilityForTesting) {
            return true;
        }
        if (callingPackageName == null) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        boolean has_query_all_packages = packageManager.checkPermission(
                android.Manifest.permission.QUERY_ALL_PACKAGES, callingPackageName)
                == PackageManager.PERMISSION_GRANTED;
        boolean has_interact_across_users = packageManager.checkPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS, callingPackageName)
                == PackageManager.PERMISSION_GRANTED;

        if (has_query_all_packages && has_interact_across_users) {
            return true;
        } else {
            try {
                return packageManager.canPackageQuery(callingPackageName, targetPackageName);
            } catch (PackageManager.NameNotFoundException ignored) {
                return false;
            }
        }
    }
}
