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
    /** Active l'abonnement ActiveMQ après login (push temps réel). */
    private boolean mqEnabled = true;
    /** 1 = login normal, 2 = multi-site (utile si session déjà ouverte). */
    private String loginType = "2";
    /** Login DSS + MQ automatiquement au démarrage de l'application. */
    private boolean autoLogin = true;
    /** Poll HTTP périodique des stats People Counting (comme l'UI/Excel DSS). */
    private boolean passengerFlowPollEnabled = true;
    private Duration passengerFlowPollInterval = Duration.ofSeconds(45);

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
