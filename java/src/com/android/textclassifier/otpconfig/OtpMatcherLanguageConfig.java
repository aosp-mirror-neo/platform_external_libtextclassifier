package com.android.textclassifier.otpconfig;

import android.icu.util.ULocale;
import androidx.annotation.Nullable;
import com.android.textclassifier.otpconfig.EnglishOtpPattern;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OtpMatcherLanguageConfig {

    private static final int PATTERN_FLAGS = Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;
    private static final Map<String, OtpMatcherLanguageConfig> CONFIG_MAP = new HashMap<>();

    private static final OtpMatcherLanguageConfig ENGLISH = new OtpMatcherLanguageConfig(
            ULocale.ENGLISH.toLanguageTag(),
            EnglishOtpPattern.OTP_PATTERN,
            EnglishOtpPattern.FALSE_POSITIVE_PATTERN,
            EnglishOtpPattern.CONTEXT_WORDS_PATTERNS
    );

    static {
        CONFIG_MAP.put(ENGLISH.languageTag, ENGLISH);
    }

    private final String languageTag;
    private final ThreadLocal<Matcher> otpMatcher;
    private final ThreadLocal<Matcher> falsePositiveMatcher;
    private final ThreadLocal<Matcher> contextWordsMatcher;

    private OtpMatcherLanguageConfig(
            String languageTag,
            String otpPatternStr,
            String falsePositivePatternStr,
            String[] contextWordsPatterns) {
        this.languageTag = languageTag;

        this.otpMatcher = createMatcher(otpPatternStr);
        this.falsePositiveMatcher = createMatcher(falsePositivePatternStr);
        this.contextWordsMatcher = createDictionaryMatcher(contextWordsPatterns);
    }

    /**
     * Call this method in the constructor to compile the Pattern once, but creates a Matcher per thread.
     */
    private static ThreadLocal<Matcher> createMatcher(String patternStr) {
        Pattern pattern = Pattern.compile(patternStr, PATTERN_FLAGS);
        return ThreadLocal.withInitial(() -> pattern.matcher(""));
    }

    /**
     * Creates a regular expression to match any of a series of individual words regexes.
     */
    private static ThreadLocal<Matcher> createDictionaryMatcher(String[] words) {
        StringBuilder regex = new StringBuilder("(");
        for (int i = 0; i < words.length; i++) {
            String boundedWord = "\\b" + words[i] + "\\b";
            regex.append(boundedWord);
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

    public ThreadLocal<Matcher> getContextWordsMatcher() {
        return contextWordsMatcher;
    }

    @Nullable
    public static OtpMatcherLanguageConfig fromLanguageTag(String languageTag) {
        if (languageTag == null) {
            return null;
        }
        return CONFIG_MAP.getOrDefault(languageTag, null);
    }
}