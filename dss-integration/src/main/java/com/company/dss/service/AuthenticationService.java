package com.company.dss.service;

import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;

public interface AuthenticationService {

    AuthLoginResponse login();

    /**
     * Garantit une session DSS valide : no-op si déjà connecté,
     * sinon relogin (si {@code dss.auto-login=true}).
     */
    AuthLoginResponse ensureLoggedIn();

    LoginTestResponse testLogin();

    void logout();

    boolean isConnected();

    String getCurrentToken();
}
