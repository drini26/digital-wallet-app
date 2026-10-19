package com.drini.wallet.auth.notification;

import com.drini.wallet.auth.config.AuthProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Demo SMS delivery: the text is sent as an email to "<phone>@sms.wallet.local", so it shows up in Mailpit. */
@Component
public class MailpitSmsGateway implements SmsGateway {

    public static final String SMS_DOMAIN = "sms.wallet.local";

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    public MailpitSmsGateway(JavaMailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String e164Phone, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(e164Phone + "@" + SMS_DOMAIN);
        message.setSubject("[SMS] " + e164Phone);
        message.setText(text);
        mailSender.send(message);
    }
}
