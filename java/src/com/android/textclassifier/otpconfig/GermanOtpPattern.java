package com.android.textclassifier.otpconfig;

final class GermanOtpPattern {
    /**
     * Matches and captures 4-8 digits, not followed or preceded by alphanumeric characters,
     * allowing dashes or spaces in the middle of 6-digit OTPs.
     * Examples: "1234", "12345678", "123-456", and "123 456"
     */
    static final String OTP_PATTERN = "\\b(\\d{4,8}|\\d{3}[-\\s]\\d{3})\\b";

    /**
     * Matches and captures the following common non-OTP patterns:
     * 1. Date/Phone/Time-like: "123 123 123", "12-12-1234", "123.1234.1234"
     * 2. URL query string-like: "https://abc.com/q?x=123456"
     * 3. Currency-like: €2500, 1000 EUR
     */
    static final String FALSE_POSITIVE_PATTERN =
            "(\\d{2,4}(?:[-/\\.\\s]\\d{1,4}){2,5}|[=€]\\d{4,8}|\\d{4,8}\\s?(?:eur|€))";

    /**
     * A list of regular expressions representing words found in an OTP context.
     */
    static final String[] STRONG_CONTEXT_WORDS_PATTERNS = new String[]{
            "(?:aktivierung|angeforderter|authentifikation|authentifi?zierung|best[aä]tigung"
                    + "|registrierung|sicherheit|telefonverifizierung|verifizierung)"
                    + "(?:\\sden\\s|s-?|\\s)code",
            "(?:einmal|einmaliges?\\s)(?:pin|passwort)",
            "kurzcode",
            "otp",
            "(?:sms|ident)-code",
            "(?:sms|m)?tan",
            "sms-kennwort",
    };

    /**
     * A list of potential keywords in an OTP message.
     * This alone is not a strong indicator. It is paired with a list of supporting context words.
     * A message should match at least one of the keywords from each list to be considered an OTP.
     */
    static final String[] DEPENDENT_TARGET_WORDS_PATTERNS = new String[]{
            "(?:der)?code",
            "pin",
    };

    /**
     * A list of supporting context words that are usually present in an OTP message.
     * This list is paired with a list of target words. The co-occurrence of both target and context
     * words yield a strong signal when determining an OTP.
     */
    static final String[] DEPENDENT_CONTEXT_WORDS_PATTERNS = new String[]{
            "best[aä]tig(?:en|ung)",
            "verifizier(?:en|ung)",
            // "Never/don't share"
            // Note: open-ended matching such as ".*" could lead to crashes.
            "(?:gib|geben).{1,20}(?:nicht|niemals|niemanden).{1,20}weiter",
            "teile.{1,20}(?:mit\\sniemandem|niemals)",
            "teile\\sihn\\snicht",
            "bitte\\snicht\\steilen",
            // "Requested at"
            "angefordert\\sam",
            // "Login"
            "anmelden",
            // "Valid for x minutes"
            "\\d{1,2}\\sminuten\\sgültig",
            // "Expire in x minutes"
            "läuft\\sin\\s\\d{1,2}\\sminuten\\sab",
            "gültig\\sfür\\s\\d{1,2}\\sminuten",
            // "Password reset"
            "passwortwiederherstellung",
            "zurücksetzen\\sdes\\s(pass|kenn)worte?s",
            // "If this is not you"
            "wenn\\sdu\\ses\\snicht\\sbist",
    };

    private GermanOtpPattern() {
    }
}
