package com.drini.wallet.auth.notification;

/** Sends a text message to a phone number. */
public interface SmsGateway {

    void send(String e164Phone, String text);
}
