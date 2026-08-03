package com.company.dss.service;

import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;

public interface AuthenticationService {

    AuthLoginResponse login();

    LoginTestResponse testLogin();

    void logout();

    boolean isConnected();

    String getCurrentToken();
}
