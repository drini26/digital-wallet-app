package com.drini.wallet.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends the "money is frozen" alert email to the operations team. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final JavaMailSender mailSender;
    private final String alertEmail;

    public AlertService(JavaMailSender mailSender, @Value("${wallet.ledger.alert-email}") String alertEmail) {
        this.mailSender = mailSender;
        this.alertEmail = alertEmail;
    }

    public void sendFreezeAlert(String details) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("alerts@wallet.local");
        message.setTo(alertEmail);
        message.setSubject("ALERT: reconciliation mismatch, outgoing money frozen");
        message.setText("The reconciliation found a difference. Withdrawals and sweeps are frozen until a manager "
                + "unfreezes them.\n\n" + details);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Could not send the freeze alert email: {}", e.getMessage());
        }
    }
}
