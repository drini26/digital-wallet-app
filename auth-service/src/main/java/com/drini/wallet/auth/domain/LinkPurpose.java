package com.drini.wallet.auth.domain;

/** Why a sign-in link is being sent; only the wording of the message changes. */
public enum LinkPurpose {
    WELCOME,
    SIGN_IN,
    ALREADY_REGISTERED
}
