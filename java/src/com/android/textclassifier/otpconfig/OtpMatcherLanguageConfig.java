package com.android.textclassifier.otpconfig;

import android.icu.util.ULocale;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OtpMatcherLanguageConfig {

    // ULocale supports many languages, but only includes constants for a few. Others must be
    // defined manually.
    static final String SPANISH_LANGUAGE_TAG = "es";
    static final String BAHASA_LANGUAGE_TAG = "id";

    private static final int PATTERN_FLAGS =
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;
    private static final Map<String, OtpMatcherLanguageConfig> CONFIG_MAP = new HashMap<>();

    private static final OtpMatcherLanguageConfig ENGLISH = new OtpMatcherLanguageConfig(
            ULocale.ENGLISH.toLanguageTag(),
            EnglishOtpPattern.OTP_PATTERN,
            EnglishOtpPattern.FALSE_POSITIVE_PATTERN,
            EnglishOtpPattern.CONTEXT_WORDS_PATTERNS,
            /* dependentTargetWordsPatterns= */ null,
            /* dependentContextWordsPatterns= */ null,
            /* isSpaceDelimitedLanguage= */ true
    );

    private static final OtpMatcherLanguageConfig KOREAN = new OtpMatcherLanguageConfig(
            ULocale.KOREAN.toLanguageTag(),
            KoreanOtpPattern.OTP_PATTERN,
            KoreanOtpPattern.FALSE_POSITIVE_PATTERN,
            KoreanOtpPattern.CONTEXT_WORDS_PATTERNS,
            /* dependentTargetWordsPatterns= */ null,
            /* dependentContextWordsPatterns= */ null,
            /* isSpaceDelimitedLanguage= */ false
    );

    private static final OtpMatcherLanguageConfig SPANISH = new OtpMatcherLanguageConfig(
            SPANISH_LANGUAGE_TAG,
            SpanishOtpPattern.OTP_PATTERN,
            SpanishOtpPattern.FALSE_POSITIVE_PATTERN,
            SpanishOtpPattern.STRONG_CONTEXT_WORDS_PATTERNS,
            SpanishOtpPattern.DEPENDENT_TARGET_WORDS_PATTERNS,
            SpanishOtpPattern.DEPENDENT_CONTEXT_WORDS_PATTERNS,
            /* isSpaceDelimitedLanguage= */ true
    );

    private static final OtpMatcherLanguageConfig BAHASA = new OtpMatcherLanguageConfig(
            BAHASA_LANGUAGE_TAG,
            BahasaOtpPattern.OTP_PATTERN,
            BahasaOtpPattern.FALSE_POSITIVE_PATTERN,
            BahasaOtpPattern.CONTEXT_WORDS_PATTERNS,
            /* dependentTargetWordsPatterns= */ null,
            /* dependentContextWordsPatterns= */ null,
            /* isSpaceDelimitedLanguage= */ true
    );

    private static final OtpMatcherLanguageConfig JAPANESE = new OtpMatcherLanguageConfig(
            ULocale.JAPANESE.toLanguageTag(),
            JapaneseOtpPattern.OTP_PATTERN,
            JapaneseOtpPattern.FALSE_POSITIVE_PATTERN,
            JapaneseOtpPattern.STRONG_CONTEXT_WORDS_PATTERNS,
            JapaneseOtpPattern.DEPENDENT_TARGET_WORDS_PATTERNS,
            JapaneseOtpPattern.DEPENDENT_CONTEXT_WORDS_PATTERNS,
            /* isSpaceDelimitedLanguage= */ false
    );

    static {
        CONFIG_MAP.put(ENGLISH.languageTag, ENGLISH);
        CONFIG_MAP.put(KOREAN.languageTag, KOREAN);
        CONFIG_MAP.put(SPANISH.languageTag, SPANISH);
        CONFIG_MAP.put(BAHASA.languageTag, BAHASA);
        CONFIG_MAP.put(JAPANESE.languageTag, JAPANESE);
    }

    private final String languageTag;
    private final ThreadLocal<Matcher> otpMatcher;
    private final ThreadLocal<Matcher> falsePositiveMatcher;
    private final ThreadLocal<Matcher> strongContextWordsMatcher;
    @Nullable
    private final ThreadLocal<Matcher> dependentTargetWordsMatcher;
    @Nullable
    private final ThreadLocal<Matcher> dependentContextWordsMatcher;

    private OtpMatcherLanguageConfig(
            String languageTag,
            String otpPatternStr,
            String falsePositivePatternStr,
            String[] strongContextWordsPatterns,
            @Nullable String[] dependentTargetWordsPatterns,
            @Nullable String[] dependentContextWordsPatterns,
            boolean isSpaceDelimitedLanguage) {
        this.languageTag = languageTag;

        this.otpMatcher = createMatcher(otpPatternStr);
        this.falsePositiveMatcher = createMatcher(falsePositivePatternStr);
        this.strongContextWordsMatcher = createDictionaryMatcher(
                strongContextWordsPatterns, isSpaceDelimitedLanguage);
        this.dependentTargetWordsMatcher =
                dependentTargetWordsPatterns != null ? createDictionaryMatcher(
                        dependentTargetWordsPatterns, isSpaceDelimitedLanguage) : null;
        this.dependentContextWordsMatcher =
                dependentContextWordsPatterns != null ? createDictionaryMatcher(
                        dependentContextWordsPatterns, isSpaceDelimitedLanguage) : null;
    }

    /**
     * Call this method in the constructor to compile the Pattern once, but creates a Matcher per
     * thread.
     */
    private static ThreadLocal<Matcher> createMatcher(String patternStr) {
        Pattern pattern = Pattern.compile(patternStr, PATTERN_FLAGS);
        return ThreadLocal.withInitial(() -> pattern.matcher(""));
    }

    /**
     * Creates a regular expression to match any of a series of individual words regexes.
     */
    private static ThreadLocal<Matcher> createDictionaryMatcher(
            String[] words, boolean isSpaceDelimitedLanguage) {
        StringBuilder regex = new StringBuilder("(");
        for (int i = 0; i < words.length; i++) {
            if (isSpaceDelimitedLanguage) {
                String boundedWord = "\\b" + words[i] + "\\b";
                regex.append(boundedWord);
            } else {
                regex.append(words[i]);
            }
            if (i != words.length - 1) {
                regex.append("|");
            }
        }
        regex.append(")");
        return createMatcher(regex.toString());
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public ThreadLocal<Matcher> getOtpMatcher() {
        return otpMatcher;
    }

    public ThreadLocal<Matcher> getFalsePositiveMatcher() {
        return falsePositiveMatcher;
    }

    public ThreadLocal<Matcher> getStrongContextWordsMatcher() {
        return strongContextWordsMatcher;
    }

    @Nullable
    public ThreadLocal<Matcher> getDependentTargetWordsMatcher() {
        return dependentTargetWordsMatcher;
    }

    @Nullable
    public ThreadLocal<Matcher> getDependentContextWordsMatcher() {
        return dependentContextWordsMatcher;
    }

    @Nullable
    public static OtpMatcherLanguageConfig fromLanguageTag(String languageTag) {
        if (languageTag == null) {
            return null;
        }
        return CONFIG_MAP.getOrDefault(languageTag, null);
    }
}
