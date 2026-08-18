package com.company.dss.service;

import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;

public interface AuthenticationService {

    AuthLoginResponse login();

    /**
     * Connexion initiale au démarrage : restaure la session persistée ou login avec reprises.
     */
    void initializeSessionOnStartup();

    boolean isSessionReady();

    /**
     * Garantit une session DSS valide : no-op si déjà connecté,
     * sinon relogin (si {@code dss.auto-login=true}).
     */
    AuthLoginResponse ensureLoggedIn();

    /** Force une reconnexion DSS (prend la main si le compte est déjà connecté ailleurs). */
    AuthLoginResponse forceReconnect();

    LoginTestResponse testLogin();

    void logout();

    boolean isConnected();

    String getCurrentToken();
}
