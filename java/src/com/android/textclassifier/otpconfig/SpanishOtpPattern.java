package com.android.textclassifier.otpconfig;

final class SpanishOtpPattern {
    /**
     * Matches and captures 4-8 digits, allowing dashes or spaces in the middle of 6-digit OTPs.
     * Examples: "1234", "12345678", "123-456", and "123 456"
     */
    static final String OTP_PATTERN = "\\b(\\d{4,8}|\\d{3}[-\\s]\\d{3})\\b";

    /**
     * Matches and captures dates or phone numbers or currency amounts.
     * Examples: "123 123 123", "12-12-1234", "12/12/2012", "$1234"
     */
    static final String FALSE_POSITIVE_PATTERN = "(\\d{2,4}(?:[-/\\s]\\d{2,5}){2,5}|\\$\\d{4,8})";

    /**
     * A list of regular expressions representing words found in an OTP context.
     */
    static final String[] STRONG_CONTEXT_WORDS_PATTERNS = new String[]{
            "(?:clave|c[oó]digo)\\sde\\s(?:acceso|activaci[oó]n|autenticaci[oó]n|confirmaci[oó]n"
                    + "|firma|inicio|seguridad|validaci[oó]n|verificaci[oó]n|\\d\\sd[ií]gitos)",
            "(?:clave|c[oó]digo)\\s(?:generado|pin|sms)",
            "no\\s(l[oa]\\s)?compartas?",
            "otp",
            "para\\s(?:restablecer\\sla\\scontraseña|verificar\\stu)",
            "utiliza\\sel\\s(?:clave|c[oó]digo)",
            "1\\ssolo\\suso",
    };

    /**
     * A list of potential keywords in an OTP message.
     * This alone is not a strong indicator. It is paired with a list of supporting context words.
     * A message should match at least one of the keywords from each list to be considered an OTP.
     */
    static final String[] DEPENDENT_TARGET_WORDS_PATTERNS = new String[]{
            "clave",
            "c[oó]digo",
            "pin",
    };

    /**
     * A list of supporting context words that are usually present in an OTP message.
     * This list is paired with a list of target words. The co-occurrence of both target and context
     * words yield a strong signal when determining an OTP.
     */
    static final String[] DEPENDENT_CONTEXT_WORDS_PATTERNS = new String[]{
            "activar",
            "cuenta",
            "(?:expira|efectivo)\\sen",
            "no\\sreconoces\\sesta\\soperacion",
            "nunca\\s(?:lo\\s)?compartas",
            "para\\s(?:acceder|autenticar|autorizar|completar|confirmar|continuar|finalizar|firmar"
                    + "|la\\sfirma|registrar|restablecer|revalidar|verificar)",
            "seguridad",
            "validacion",
            "v[aá]lido\\s(?:durante|por)",
    };

    private SpanishOtpPattern() {
    }
}
