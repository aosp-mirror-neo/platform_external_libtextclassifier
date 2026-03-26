package com.android.textclassifier.otpconfig;

import static java.lang.String.format;

final class EnglishOtpPattern {
    /**
     * A regex matching a line start, open paren, arrow, colon (not proceeded by a digit), dash
     * preceded by a letter, open/close square bracket, equals sign, double or single quote,
     * ideographic char, or a space that is not preceded by a number.
     * It will not consume the start char (meaning START won't be included in the matched string).
     */
    private static final String START =
            "(?:^|(?<=(?:^|[^0-9])\\s|[>\\](\"'=\\[\\p{IsIdeographic}]|[^0-9]:|[A-Za-z]-))";

    /**
     * A regex matching a line end, a space that is not followed by a number, an ideographic char,
     * or a period, close paren, close square bracket, single or double quote, exclamation point,
     * question mark, or comma. It will not consume the end char.
     */
    private static final String END = "(?=\\s[^0-9]|$|\\p{IsIdeographic}|[.?!,)'\\]\"])";

    static final String OTP_PATTERN;

    static {
        /*
         * One single OTP char. Currently, we only support numerical OTPs.
         * We may support alphanumerical OTPs in the future.
         */
        final String OTP_CHAR = "\\d";

        /* One OTP char, followed by an optional dash. */
        final String OTP_CHAR_WITH_DASH = format("(?:%s-?)", OTP_CHAR);

        /*
         * Matches between 4 and 8 otp chars, with dashes in between. Here, we are assuming an OTP
         * code is 4-8 characters long. The last char must not be followed by a dash.
         */
        final String FOUR_TO_EIGHT_DIGITS_POTENTIALLY_WITH_DASH = format("%s{3,7}%s",
                OTP_CHAR_WITH_DASH, OTP_CHAR);

        /* A regex matching two pairs of 3 digits (ex "123 456") */
        final String SIX_DIGITS_WITH_SPACE = "\\d{3}\\s\\d{3}";

        /*
         * Combining the regular expressions above, we get an OTP regex that does the following:
         * 1. Search for START,
         * 2. Match ONE of:
         *    a. Numeric sequence of length 4-8 with optional dashes, or
         *    b. Pair of 3-digit codes separated by a space,
         * 3. Match END.
         * Examples: "6454", " 345 678.", "[YDT-3456]"
         */
        OTP_PATTERN = format("%s(%s|%s)%s",
                START, FOUR_TO_EIGHT_DIGITS_POTENTIALLY_WITH_DASH, SIX_DIGITS_WITH_SPACE, END);
    }

    static final String FALSE_POSITIVE_PATTERN;

    static {
        /*
         * Regular expression for short-form and long-form month names in English.
         */
        final String MONTH_PATTERN =
                "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?"
                        + "|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)";

        /*
         * A Date regular expression. Looks for dates with the month, day, and year separated by
         * dashes, slashes, or periods. Handles one and two digit months and days, and two or four
         * digit years. It makes the following assumptions: Dates and months will never be higher
         * than 39. If a four-digit year is used, the leading digit will be 1 or 2.
         */
        final String DATE_PATTERN = format(
                // DD/MM/(YY)YY, MM/DD/(YY)YY, DD-MM-(YY)YY, MM-DD-(YY)YY, DD.MM.YYYY, MM.DD.YYYY
                "(?:[0-3]?\\d[-/\\.][0-3]?\\d[-/\\.](?:[12]\\d)?\\d{2}" +
                        // YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD
                        "|(?:[12]\\d)?\\d{2}[-/\\.][0-3]?\\d[-/\\.][0-3]?\\d" +
                        // DD MMM (YY)YY, DD-MMM-(YY)YY, DD/MMM/(YY)YY
                        "|[0-3]?\\d[-\\s/]%s[-\\s/](?:[12]\\d)?\\d{2}" +
                        // MMM DD(,) (YY)YY
                        "|%s\\s[0-3]?\\d,?\\s(?:[12]\\d)?\\d{2})",
                MONTH_PATTERN, MONTH_PATTERN);

        /*
         * Matches phone numbers.
         * Supports optional parentheses around the area code, and an optional dash or space in
         * between the rest of the numbers. This format registers as an OTP match due to the space
         * between the area code and the rest, but shouldn't.
         */
        final String PHONE_NUMBER_PATTERN =
                "(?:\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4}"
                        + "|\\d{4}[\\s-]\\d{4}"
                        + "|(?:call|calling|dialing|dial)\\s?\\d{2,8}([\\s-]\\d{2,8}){0,3})";

        /*
         * Matches currency amounts, indicated by symbols.
         */
        final String CURRENCY_AMOUNT_PATTERN = "[\\$€£]\\s?\\d{4,8}";

        /*
         * Matches informational last digits of identifiers (e.g. last 4 digits of credit card,
         * bank account, etc.).
         */
        final String LAST_DIGITS_OF_IDENTIFIER = "(?:ending(?:\\swith)?|card|id|number)\\s\\d{3,8}";

        /*
         * A combination of common false positives. These matches are expected to be longer than
         * (or equal in length to) OTP matches.
         */
        FALSE_POSITIVE_PATTERN = format("%s(%s|%s|%s|%s)%s",
                START, DATE_PATTERN, PHONE_NUMBER_PATTERN, CURRENCY_AMOUNT_PATTERN,
                LAST_DIGITS_OF_IDENTIFIER, END);
    }

    /**
     * A list of regular expressions representing words found in an OTP context (non-case-sensitive)
     * Note: TAN is short for Transaction Authentication Number
     */
    static final String[] CONTEXT_WORDS_PATTERNS = new String[]{
            "pin",
            "pass[-\\s]?(?:code|word)",
            "TAN",
            "otp",
            "2fa",
            "(?:two|2)[-\\s]?factor",
            "log[-\\s]?in",
            "auth(?:enticat(?:e|ion))?",
            // Match 'code' word that is not followed by alphanumerics, because they tend to be
            // vouchers or promo code (e.g., code 743a, or code:bzy6)
            "code(?!:?\\s?(?:[a-zA-Z]{1,10}\\d|\\d{1,10}[a-zA-Z]))",
            "secret",
            "verif(?:y|ication)",
            "one[-\\s]?time",
            "access",
            "validat(?:e|ion)",
    };

    private EnglishOtpPattern() {
    }
}
