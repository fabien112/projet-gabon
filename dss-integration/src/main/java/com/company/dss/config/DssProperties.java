package com.company.dss.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Paramètres de connexion au serveur Dahua DSS Professional.
 * Toutes les valeurs sont injectées depuis les variables d'environnement (voir application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "dss")
public class DssProperties {

    private String protocol = "https";
    private String host;
    private int port = 443;
    private String username;
    private String password;
    private boolean verifySsl = false;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(15);
    private Duration keepAliveInterval = Duration.ofSeconds(25);

    public String getBaseUrl() {
        return protocol + "://" + host + ":" + port;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(host)
                && StringUtils.hasText(username)
                && StringUtils.hasText(password);
    }

    public void validateForLogin() {
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("DSS_HOST est requis (variable d'environnement manquante)");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("DSS_USERNAME est requis (variable d'environnement manquante)");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("DSS_PASSWORD est requis (variable d'environnement manquante)");
        }
    }
}
