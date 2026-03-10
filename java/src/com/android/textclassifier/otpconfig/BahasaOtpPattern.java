package com.android.textclassifier.otpconfig;

final class BahasaOtpPattern {
    /**
     * Matches and captures 4-8 digits, allowing dashes or spaces in the middle of 6-digit OTPs.
     * Examples: "1234", "12345678", "123-456", and "123 456"
     */
    static final String OTP_PATTERN = "\\b(\\d{4,8}|\\d{3}[-\\s]\\d{3})\\b";

    /**
     * Matches and captures dates or phone numbers with dashes or slashes in between.
     * Examples: "12-12-1234", "12/12/1234", "123 1234 1234"
     */
    static final String FALSE_POSITIVE_PATTERN = "(\\d{2,4}[-/\\s]\\d{2,4}[-/\\s]\\d{2,4})";

    /**
     * A list of regular expressions representing words found in an OTP context.
     */
    static final String[] CONTEXT_WORDS_PATTERNS = new String[]{
            "otp",
            "one\\stime\\spassword",
            "verifikasi\\skode",
            "verification\\scode",
            "gunakan\\s(?:pin|kode)",
            "pin\\s(?:untuk|utk)\\slogin",
            "kode\\s(?:atur\\sulang|otentikasi|otorisasi|verifikasi|untuk|utk|rahasia|konfirmasi"
                    + "|token|sekali\\spakai|reset|keamanan)",
            "(?:jangan|jgn)\\s(?:mem|meng)?(?:info|bagi|beri)(?:kan)?\\skode",
    };

    private BahasaOtpPattern() {
    }
}
