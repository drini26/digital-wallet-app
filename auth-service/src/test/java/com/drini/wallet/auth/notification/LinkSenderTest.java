package com.drini.wallet.auth.notification;

import com.drini.wallet.auth.config.AuthProperties;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.LinkPurpose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Checks how sign-in links are built and delivered (email vs SMS), without a real mail server. */
class LinkSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmsGateway smsGateway = mock(SmsGateway.class);
    private final LinkSender linkSender = new LinkSender(mailSender, smsGateway, new AuthProperties());

    @Test
    void emailUserGetsAnEmail() {
        linkSender.send(Identifier.parse("ana@wallet.local"), "abc123", LinkPurpose.WELCOME);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("ana@wallet.local");
        assertThat(message.getValue().getSubject()).isEqualTo("Welcome to Digital Wallet");
        assertThat(message.getValue().getText())
                .contains("http://localhost:4200/auth/callback?token=abc123&identifier=ana%40wallet.local");
    }

    @Test
    void phoneUserGetsAnSmsWithEncodedPlus() {
        linkSender.send(Identifier.parse("+355691234567"), "abc123", LinkPurpose.SIGN_IN);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(smsGateway).send(org.mockito.ArgumentMatchers.eq("+355691234567"), text.capture());
        assertThat(text.getValue()).contains("identifier=%2B355691234567");
    }

    @Test
    void alreadyRegisteredHasItsOwnText() {
        linkSender.send(Identifier.parse("ana@wallet.local"), "abc", LinkPurpose.ALREADY_REGISTERED);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getSubject()).contains("already have");
    }

    @Test
    void mailProblemIsNotFatal() {
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

        linkSender.send(Identifier.parse("ana@wallet.local"), "abc", LinkPurpose.SIGN_IN);
    }

    @Test
    void mailpitSmsGatewaySendsToTheSmsDomain() {
        MailpitSmsGateway gateway = new MailpitSmsGateway(mailSender, new AuthProperties());

        gateway.send("+355691234567", "hello");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("+355691234567@sms.wallet.local");
        assertThat(message.getValue().getText()).isEqualTo("hello");
    }
}
