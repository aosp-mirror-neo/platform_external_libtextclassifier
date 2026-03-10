package com.android.textclassifier.otpconfig;

final class JapaneseOtpPattern {
    /**
     * Matches and captures 4-8 digits, not followed or preceded by alphanumeric characters,
     * allowing dashes or spaces in the middle of 6-digit OTPs.
     * Examples: "1234", "12345678", "123-456", and "123 456"
     */
    static final String OTP_PATTERN =
            "(?<=\\b|[^\\da-zA-Z])(\\d{4,8}|\\d{3}[-\\s]\\d{3})(?=\\b|[^\\da-zA-Z])";

    /**
     * Matches and captures the following common non-OTP patterns:
     * 1. Date/Phone-like: "123 123 123", "12-12-1234", "123. 1234. 1234"
     * 2. Currency-like: "1234円", "¥1234"
     * 3. URL query string-like: "https://abc.com/q?x=123456"
     */
    static final String FALSE_POSITIVE_PATTERN =
            "(\\d{2,4}(?:[-－/\\.]\\d{2,4}){2,4}|\\d{4,8}[年円]|[¥=]\\d{4,8})";

    /**
     * A list of regular expressions representing words found in an OTP context.
     */
    static final String[] STRONG_CONTEXT_WORDS_PATTERNS = new String[]{
            "(?:認証|確認|検証|セキュリティ|パス|\\d桁の)コード",
            "ワンタイムパスワード",
            "認証番号",
    };

    /**
     * A list of potential keywords in an OTP message.
     * This alone is not a strong indicator. It is paired with a list of supporting context words.
     * A message should match at least one of the keywords from each list to be considered an OTP.
     */
    static final String[] DEPENDENT_TARGET_WORDS_PATTERNS = new String[]{
            "コード", // "Code"
            "パスワード", // "Password"
            "ワンタイム", // "One-time"
    };

    /**
     * A list of supporting context words that are usually present in an OTP message.
     * This list is paired with a list of target words. The co-occurrence of both target and context
     * words yield a strong signal when determining an OTP.
     */
    static final String[] DEPENDENT_CONTEXT_WORDS_PATTERNS = new String[]{
            "共有しないでください", // "Please do not share"
            "認証", // "Authentication"
            "入力してください", // "Please enter"
            "有効期限", // "Validity/expiration date/time"
    };

    private JapaneseOtpPattern() {
    }
}
