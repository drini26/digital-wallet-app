package com.drini.wallet.auth.notification;

import com.drini.wallet.auth.config.AuthProperties;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.LinkPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Delivers a sign-in link: by email for email users, by SMS (SmsGateway) for phone users. */
@Component
public class LinkSender {

    private static final Logger log = LoggerFactory.getLogger(LinkSender.class);

    private final JavaMailSender mailSender;
    private final SmsGateway smsGateway;
    private final AuthProperties properties;

    public LinkSender(JavaMailSender mailSender, SmsGateway smsGateway, AuthProperties properties) {
        this.mailSender = mailSender;
        this.smsGateway = smsGateway;
        this.properties = properties;
    }

    public void send(Identifier identifier, String rawToken, LinkPurpose purpose) {
        String link = buildLink(identifier, rawToken);
        try {
            if (identifier.isEmail()) {
                sendEmail(identifier.getValue(), subjectFor(purpose), textFor(purpose, link));
            } else {
                smsGateway.send(identifier.getValue(), "Digital Wallet: " + textFor(purpose, link));
            }
        } catch (MailException e) {
            log.warn("Could not deliver the sign-in link ({}): {}", purpose, e.getMessage());
        }
    }

    /** Percent-encode the identifier: the "+" of a phone number would otherwise become a space. */
    String buildLink(Identifier identifier, String rawToken) {
        return UriComponentsBuilder.fromUriString(properties.getFrontendUrl())
                .path("/auth/callback")
                .queryParam("token", "{token}")
                .queryParam("identifier", "{identifier}")
                .encode()
                .buildAndExpand(rawToken, identifier.getValue())
                .toUriString();
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private String subjectFor(LinkPurpose purpose) {
        if (purpose == LinkPurpose.WELCOME) {
            return "Welcome to Digital Wallet";
        }
        if (purpose == LinkPurpose.ALREADY_REGISTERED) {
            return "You already have a Digital Wallet account";
        }
        return "Your sign-in link";
    }

    private String textFor(LinkPurpose purpose, String link) {
        String validity = " The link works once and expires in " + properties.getMagicLinkTtl().toMinutes() + " minutes.";
        if (purpose == LinkPurpose.WELCOME) {
            return "Your account is ready. Sign in here: " + link + validity;
        }
        if (purpose == LinkPurpose.ALREADY_REGISTERED) {
            return "Someone tried to create an account with this address, but you already have one. "
                    + "If it was you, sign in here: " + link + validity;
        }
        return "Sign in here: " + link + validity;
    }
}
