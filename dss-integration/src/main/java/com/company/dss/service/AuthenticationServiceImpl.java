package com.company.dss.service;

import org.springframework.stereotype.Service;

import com.company.dss.authentication.AuthenticationClient;
import com.company.dss.authentication.TokenHolder;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationClient authenticationClient;
    private final TokenHolder tokenHolder;

    @Override
    public AuthLoginResponse login() {
        return authenticationClient.login();
    }

    @Override
    public LoginTestResponse testLogin() {
        AuthLoginResponse response = authenticationClient.login();
        return new LoginTestResponse(true, response.token());
    }

    @Override
    public void logout() {
        authenticationClient.logout();
    }

    @Override
    public boolean isConnected() {
        return tokenHolder.hasValidToken();
    }

    @Override
    public String getCurrentToken() {
        return tokenHolder.getToken().orElse(null);
    }
}
