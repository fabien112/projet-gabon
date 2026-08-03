package com.company.dss.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.config.DssProperties;
import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final DssProperties dssProperties;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login() {
        var response = authenticationService.login();
        return ResponseEntity.ok(Map.of(
                "connected", true,
                "token", response.token(),
                "userName", response.userName() != null ? response.userName() : "",
                "duration", response.duration() != null ? response.duration() : 30
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        authenticationService.logout();
        return ResponseEntity.ok(Map.of("connected", false));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "connected", authenticationService.isConnected(),
                "configured", dssProperties.isConfigured(),
                "host", dssProperties.getHost() != null ? dssProperties.getHost() : "",
                "loginType", dssProperties.getLoginType() != null ? dssProperties.getLoginType() : ""
        ));
    }
}
