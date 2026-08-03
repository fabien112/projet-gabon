package com.company.dss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /**
     * Identifiant unique partagé par tous les utilisateurs de l'UI.
     */
    private String username = "admin";

    /**
     * Mot de passe unique partagé.
     */
    private String password = "changeme";
}
