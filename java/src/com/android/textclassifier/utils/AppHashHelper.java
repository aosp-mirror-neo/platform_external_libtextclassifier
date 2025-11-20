/**
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Trace;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class AppHashHelper {
    private static final String TAG = AppHashHelper.class.getSimpleName();

    @VisibleForTesting
    public static final Map<String, String> sAppHashCache = new ConcurrentHashMap<>();

    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits = 12 Base64s
    private static final int NUM_BASE64_CHARS = 11; // truncate 12 into 11 Base64 chars
    @VisibleForTesting
    public static final String HASH_DELIMITER = ",";

    @VisibleForTesting
    public static final String APP_HASHES_FILENAME = "app_hashes.txt";

    public static final Object sLock = new Object();
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static volatile Future<?> sLoadingFuture;
    private static final long LOAD_TIMEOUT_MS = 500;
    private static volatile boolean sIsLoadedFromPackageManager = false;

    /**
     * Returns the package name for a given hash.
     *
     * @param hash The hash to look up.
     * @return The package name, or {@code null} if the hash is not found.
     */
    @Nullable
    public static String getPackageNameForHash(String hash) {
        synchronized (sLock) {
            return sAppHashCache.get(hash);
        }
    }

    @VisibleForTesting
    public static void resetLoadFromPackageManager() {
        sLoadingFuture = null;
        sIsLoadedFromPackageManager = false;
    }

    @VisibleForTesting
    public static void markAsLoadedFromPackageManagerForTest() {
        sIsLoadedFromPackageManager = true;
    }

    @VisibleForTesting
    public static boolean isLoadedFromPackageManager() {
        return sIsLoadedFromPackageManager;
    }

    /**
     * Triggers the app hash loading process if it is not already complete, and waits for the
     * load to finish, subject to a timeout.
     * <p>
     * This method is the primary entry point for initializing the hash cache. It is intended to be
     * called at two key moments:
     * <ul>
     *   <li>Proactively, such as in response to a {@code BOOT_COMPLETED} event, to warm up the
     *       cache in the background.</li>
     *   <li>On-demand, just before the hashes are required for the first time when
     *       processing an incoming SMS_RETRIEVER_OTP classification request.</li>
     * </ul>
     * The method first loads hashes from the on-disk cache for a fast startup, and then, as part
     * of the same background task, performs a full refresh from the {@link PackageManager} to
     * ensure the cache is up-to-date.
     * <p>
     * This method is thread-safe and idempotent. If multiple threads call it concurrently, only one
     * background loading task is ever launched. All callers will wait for that single task to
     * complete, but will time out if it takes longer than {@link #LOAD_TIMEOUT_MS}. If the wait
     * times out, the background task continues to run, and subsequent calls will wait on the same,
     * still-running task.
     *
     * @param context The application context, used to access the {@link PackageManager} and for
     *                storage.
     */
    public static void ensureLoaded(Context context) {
        if (sIsLoadedFromPackageManager) {
            return;
        }

        Future<?> loadingTask = sLoadingFuture;

        if (loadingTask == null) {
            synchronized (sLock) {
                loadingTask = sLoadingFuture;
                if (loadingTask == null) {
                    loadingTask = sExecutor.submit(() -> {
                        loadAppHashesFromDisk(context);
                        refreshFromPackageManager(context);
                    });
                    sLoadingFuture = loadingTask;
                }
            }
        }

        // All threads wait on the same Future object, outside the synchronized block.
        try {
            loadingTask.get(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.w(TAG, "Loading app hashes timed out after " + LOAD_TIMEOUT_MS + " ms");
        } catch (ExecutionException | InterruptedException e) {
            // The task failed. Log the error and clear the future to allow a retry.
            Log.e(TAG, "Failed to load app hashes", e);
            synchronized (sLock) {
                if (sLoadingFuture == loadingTask) {
                    sLoadingFuture = null;
                }
            }
        }
    }

    private static void refreshFromPackageManager(Context context) {
        try {
            Trace.beginSection("refreshFromPackageManager");
            PackageManager packageManager = context.getPackageManager();
            List<PackageInfo> packages = packageManager.getInstalledPackages(
                    PackageManager.GET_SIGNING_CERTIFICATES |
                    PackageManager.MATCH_ANY_USER);
            Map<String, String> hashToPackageMap = new HashMap<>();
            for (PackageInfo packageInfo : packages) {
                // Skip packages that don't contain any executable code.
                if (packageInfo.applicationInfo != null && (packageInfo.applicationInfo.flags
                        & android.content.pm.ApplicationInfo.FLAG_HAS_CODE) == 0) {
                    continue;
                }
                String appHash = getAppHash(packageInfo.signingInfo, packageInfo.packageName);
                if (appHash != null) {
                    hashToPackageMap.put(appHash, packageInfo.packageName);
                }
            }
            // Early termination is for test purpose only, in real world the hashes will never be
            // empty.
            if (hashToPackageMap.isEmpty()) {
                return;
            }
            synchronized (sLock) {
                sAppHashCache.clear();
                sAppHashCache.putAll(hashToPackageMap);
            }
            saveAppHashesToDisk(context);
            sIsLoadedFromPackageManager = true;
            Log.i(TAG, "App hash codes refreshed, total found=" + sAppHashCache.size());
        } finally {
            Trace.endSection();
        }
    }

    private static void loadAppHashesFromDisk(Context context) {
        try {
            Trace.beginSection("loadAppHashesFromDisk");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    context.openFileInput(APP_HASHES_FILENAME), CHARSET_UTF_8))) {
                Map<String, String> hashToPackageMap = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(HASH_DELIMITER, 2);
                    if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                        hashToPackageMap.put(parts[0], parts[1]);
                    }
                }
                if (hashToPackageMap.isEmpty()) {
                    return;
                }
                synchronized (sLock) {
                    sAppHashCache.putAll(hashToPackageMap);
                }
                Log.i(TAG, "Loaded " + hashToPackageMap.size() + " app hashes from disk");
            } catch (FileNotFoundException e) {
                // This is expected if the file doesn't exist yet.
            } catch (IOException e) {
                Log.e(TAG, "Error reading app hashes from disk", e);
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Generates and caches the SMS Retriever hash for a single package, typically after an
     * installation or update.
     * <p>
     * This method is designed to be called from a {@code BroadcastReceiver} handling package
     * change intents. It generates the corresponding app hash, and adds it to the in-memory cache
     * and persists the updated set to disk.
     *
     * @param context The context used to access the {@link PackageManager} and for storage.
     * @param packageName The package name of the app for which to generate the hash.
     */
    public static void addAppHashOnPackageAdd(Context context, @NonNull String packageName) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        try {
            packageInfo =
                    packageManager.getPackageInfo(packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES |
                                    PackageManager.MATCH_ANY_USER);
        } catch (PackageManager.NameNotFoundException ignored) {
            return;
        }
        // Skip packages that don't contain any executable code.
        if (packageInfo.applicationInfo != null && (packageInfo.applicationInfo.flags
                & android.content.pm.ApplicationInfo.FLAG_HAS_CODE) == 0) {
            return;
        }
        String appHash = getAppHash(packageInfo.signingInfo, packageName);
        if (appHash == null) {
            Log.w(TAG, "App hash is null for packageName=" + packageName);
            return;
        }
        if (sAppHashCache.containsKey(appHash)) {
            return;
        }
        sAppHashCache.put(appHash, packageName);
        sExecutor.execute(() -> saveAppHashesToDisk(context));
    }

    private static void saveAppHashesToDisk(Context context) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                context.openFileOutput(APP_HASHES_FILENAME, Context.MODE_PRIVATE),
                CHARSET_UTF_8))) {
            for (Map.Entry<String, String> entry : sAppHashCache.entrySet()) {
                writer.write(entry.getKey() + HASH_DELIMITER + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing app hashes to disk", e);
        }
    }

    /**
     * Generates the app-specific hash code used by the SMS Retriever API.
     * <p>
     * This is created by hashing the combination of the app's package name and its
     * first signing certificate using SHA-256. The resulting hash is then Base64-encoded
     * and truncated to an 11-character string.
     *
     * @param signingInfo    The {@link SigningInfo} for the package, containing its signing
     *                       certificates.
     * @param packageName    The package name of the app for which to generate the hash.
     * @return The 11-character app-specific hash string, or {@code null} if the package
     *         could not be found or has no signing information.
     */
    @VisibleForTesting
    public static String getAppHash(SigningInfo signingInfo, String packageName) {
        if (signingInfo == null) {
            return null;
        }

        Signature[] signatures;
        if (signingInfo.hasMultipleSigners()) {
            signatures = signingInfo.getApkContentsSigners();
        } else {
            signatures = signingInfo.getSigningCertificateHistory();
        }

        if (signatures == null || signatures.length == 0) {
            Log.e(TAG, "Signatures is null or empty for packageName=" + packageName);
            return null;
        }

        String appInfo = packageName + " " + signatures[0].toCharsString();
        return hash(appInfo);
    }

    private static String hash(String appInfo) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(HASH_TYPE);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Could not find " + HASH_TYPE
                    + " algorithm, which is required for generating app hash.", e);
            throw new RuntimeException(e);
        }

        messageDigest.update(appInfo.getBytes(CHARSET_UTF_8));
        byte[] hashSignature = messageDigest.digest();

        // truncated into NUM_HASHED_BYTES
        hashSignature = Arrays.copyOf(hashSignature, NUM_HASHED_BYTES);
        // encode into Base64
        String base64Hash = Base64.encodeToString(hashSignature,
                Base64.NO_PADDING | Base64.NO_WRAP);
        base64Hash = base64Hash.substring(0, NUM_BASE64_CHARS);

        return base64Hash;
    }
}
