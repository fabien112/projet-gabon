package com.company.dss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.dto.authentication.LoginTestResponse;
import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final AuthenticationService authenticationService;

    @GetMapping("/login")
    public LoginTestResponse testLogin() {
        return authenticationService.testLogin();
    }
}
