package com.drini.wallet.auth.domain;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;

import java.util.Locale;
import java.util.regex.Pattern;

/** An email address or a phone number, cleaned up so that the same person always gives the same value. */
public class Identifier {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-().]");

    private final boolean email;
    private final String value;

    private Identifier(boolean email, String value) {
        this.email = email;
        this.value = value;
    }

    public static Identifier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String trimmed = raw.trim();

        if (trimmed.contains("@")) {
            String emailValue = trimmed.toLowerCase(Locale.ROOT);
            if (emailValue.length() > 254 || !EMAIL.matcher(emailValue).matches()) {
                throw invalid();
            }
            return new Identifier(true, emailValue);
        }

        String phoneValue = PHONE_SEPARATORS.matcher(trimmed).replaceAll("");
        if (!E164_PHONE.matcher(phoneValue).matches()) {
            throw invalid();
        }
        return new Identifier(false, phoneValue);
    }

    public boolean isEmail() {
        return email;
    }

    public boolean isPhone() {
        return !email;
    }

    public String getValue() {
        return value;
    }

    private static WalletException invalid() {
        return new WalletException(ErrorCode.VALIDATION_FAILED,
                "identifier must be an email address or a phone number in international format, e.g. +355691234567");
    }
}
