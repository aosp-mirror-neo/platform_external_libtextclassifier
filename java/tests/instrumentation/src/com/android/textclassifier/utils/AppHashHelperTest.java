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

import static android.os.Build.VERSION_CODES.BAKLAVA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
@SdkSuppress(minSdkVersion = BAKLAVA)
@RequiresFlagsEnabled(com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS_API)
public class AppHashHelperTest {
    private static final String PACKAGE_NAME = "com.example.app";
    private static final String SIGNATURE_STRING = "mysignature";
    private static final String EXPECTED_HASH = "TH60ej87DlP";

    @Mock
    private Signature mMockSignature;
    @Mock
    private Signature mMockSignature2;
    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    private File mTempFile;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws IOException {
        when(mMockSignature.toCharsString()).thenReturn(SIGNATURE_STRING);
        when(mMockSignature2.toCharsString()).thenReturn("another_signature");
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        mTempFile = File.createTempFile("app_hashes", "txt");
        when(mMockContext.openFileOutput(eq(AppHashHelper.APP_HASHES_FILENAME), anyInt()))
                .thenReturn(new FileOutputStream(mTempFile));
        when(mMockContext.openFileInput(eq(AppHashHelper.APP_HASHES_FILENAME)))
                .thenThrow(new FileNotFoundException());

        AppHashHelper.clearAllHashes();
        AppHashHelper.resetLoadFromPackageManager();
    }

    @After
    public void tearDown() {
        if (mTempFile != null) {
            mTempFile.delete();
        }
    }

    @Test
    public void getAppHash_singleSigner_success() {
        SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_JAR,
                Collections.singletonList(mMockSignature), null, null);

        String actualHash = AppHashHelper.getAppHash(signingInfo, PACKAGE_NAME);

        assertEquals("The generated hash should match the expected value for a single signer.",
                EXPECTED_HASH, actualHash);
    }

    @Test
    public void getAppHash_multipleSigners_success() {
        SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_JAR,
                Arrays.asList(mMockSignature, mMockSignature2), null, null);

        String actualHash = AppHashHelper.getAppHash(signingInfo, PACKAGE_NAME);

        assertEquals("The generated hash should match the expected value for multiple signers.",
                EXPECTED_HASH, actualHash);
    }

    @Test
    public void getAppHash_nullSigningInfo_returnsNull() {
        String actualHash = AppHashHelper.getAppHash(null, PACKAGE_NAME);

        assertNull("getAppHash should return null when SigningInfo is null.", actualHash);
    }

    @Test
    public void getAppHash_nullSignatures_returnsNull() {
        SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_JAR, null, null, null);

        String actualHash = AppHashHelper.getAppHash(signingInfo, PACKAGE_NAME);

        assertNull("getAppHash should return null when the signature array is null.", actualHash);
    }

    @Test
    public void getAppHash_emptySignatures_returnsNull() {
        SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_JAR,
                Collections.emptyList(), null, null);

        String actualHash = AppHashHelper.getAppHash(signingInfo, PACKAGE_NAME);

        assertNull("getAppHash should return null when the signature array is empty.", actualHash);
    }

    @Test
    public void load_populatesCacheAndSkipsInvalidPackagesFromPackageManager() throws IOException {
        PackageInfo validPackage = createMockPackageInfo("com.example.app", true);
        validPackage.signingInfo = new SigningInfo(SigningInfo.VERSION_JAR,
                Collections.singletonList(mMockSignature), null, null);

        PackageInfo packageWithoutCode = createMockPackageInfo("com.nocode.app", false);

        PackageInfo packageWithNullHash = createMockPackageInfo("com.nullhash.app", true);
        packageWithNullHash.signingInfo = new SigningInfo(SigningInfo.VERSION_JAR,
                Collections.emptyList(), null, null);

        when(mMockPackageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(Arrays.asList(validPackage, packageWithoutCode, packageWithNullHash));

        AppHashHelper.ensureLoaded(mMockContext);

        SystemUtil.eventually(() -> assertEquals("Load is expected to be completed", true,
                AppHashHelper.isLoadedFromPackageManager()));

        assertEquals("Only one hash should be in the set.", 1,
                AppHashHelper.getAppHashes().size());
        assertTrue("The hash for the valid package should be added.",
                AppHashHelper.hasHash(EXPECTED_HASH));

        List<String> lines = Files.readAllLines(mTempFile.toPath(), StandardCharsets.UTF_8);
        assertEquals("The file should contain exactly one hash.", 1, lines.size());
        assertEquals("The hash in the file should match the expected value.", EXPECTED_HASH,
                lines.get(0));
        verify(mMockContext).openFileOutput(eq(AppHashHelper.APP_HASHES_FILENAME),
                eq(Context.MODE_PRIVATE));
    }

    @Test
    public void load_isOnlyExecutedOnce() {
        when(mMockPackageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(Collections.emptyList());

        AppHashHelper.ensureLoaded(mMockContext); // First call, should execute
        AppHashHelper.ensureLoaded(mMockContext); // Second call, should be skipped

        verify(mMockPackageManager, times(1))
                .getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
    }

    @Test
    public void load_loadsFromDisk() throws Exception {
        String storedHashes = "hash1\nhash2\n";
        Files.write(mTempFile.toPath(), storedHashes.getBytes(StandardCharsets.UTF_8));
        when(mMockContext.openFileInput(eq(AppHashHelper.APP_HASHES_FILENAME))).thenReturn(
                new FileInputStream(mTempFile));

        // Prevent PackageManager from returning packages to ensure we're testing the disk cache.
        // The PackageManager's results would otherwise overwrite the on-disk data.
        when(mMockPackageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(Collections.emptyList());

        AppHashHelper.ensureLoaded(mMockContext);

        SystemUtil.eventually(() ->
                assertEquals("Should contain 2 hashes loaded from disk", 2,
                        AppHashHelper.getAppHashes().size())
        );
        assertTrue("Cache should contain hash1 from disk", AppHashHelper.hasHash("hash1"));
        assertTrue("Cache should contain hash2 from disk", AppHashHelper.hasHash("hash2"));
        verify(mMockPackageManager).getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
    }

    private PackageInfo createMockPackageInfo(String packageName, boolean hasCode) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.signingInfo = new SigningInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        if (hasCode) {
            packageInfo.applicationInfo.flags = ApplicationInfo.FLAG_HAS_CODE;
        } else {
            packageInfo.applicationInfo.flags = 0;
        }
        return packageInfo;
    }
}
