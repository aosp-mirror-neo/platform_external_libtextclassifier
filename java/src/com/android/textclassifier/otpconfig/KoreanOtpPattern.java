package com.android.textclassifier.otpconfig;

final class KoreanOtpPattern {
    /**
     * Matches and captures 4-6 digits, surrounded by non-digit characters or start/end of a line.
     */
    static final String OTP_PATTERN = "(?<=[^\\d]|^)(\\d{4,6})(?=[^\\d]|$)";

    /**
     * Matches and captures dates or phone numbers with dashes or dots in between numbers.
     * Examples: "2023. 7.", "2025. 05. 20", "2027.2.13", "2025-10-20", "2026/02/15",
     * "080.123.4567", "1234-5678", "02-1234-5678"
     */
    static final String FALSE_POSITIVE_PATTERN =
            "(\\d{2,4}(?:[-/]|[\\.년]\\s?)\\d{1,4}(?:(?:[-/]|[\\.월]\\s?)\\d{1,4})?)";

    /**
     * A list of regular expressions representing words found in an OTP context.
     */
    static final String[] CONTEXT_WORDS_PATTERNS = new String[]{
            "(?:인증|비밀|승인)\\s?번호",
            "(?:인증|보안|확인)\\s?코드",
            "otp",
    };

    private KoreanOtpPattern() {
    }
}
