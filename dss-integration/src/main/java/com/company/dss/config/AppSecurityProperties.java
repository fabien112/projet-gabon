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

    /** Compte superadmin : seul à accéder à la page Config. */
    private String superadminUsername = "superadmin";

    private String superadminPassword = "SuperAdmin2026";
}
