package com.drini.wallet.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Settings of the auth-service, read from application.properties (prefix "wallet.auth"). */
@Component
@ConfigurationProperties(prefix = "wallet.auth")
public class AuthProperties {

    private String frontendUrl = "http://localhost:4200";

    private Duration magicLinkTtl = Duration.ofMinutes(15);

    private Duration jwtTtl = Duration.ofMinutes(30);

    private String jwtSecret;

    private String mailFrom = "no-reply@wallet.local";

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public Duration getMagicLinkTtl() {
        return magicLinkTtl;
    }

    public void setMagicLinkTtl(Duration magicLinkTtl) {
        this.magicLinkTtl = magicLinkTtl;
    }

    public Duration getJwtTtl() {
        return jwtTtl;
    }

    public void setJwtTtl(Duration jwtTtl) {
        this.jwtTtl = jwtTtl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }
}
