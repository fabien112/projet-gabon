package com.company.dss.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AppUserSeeder implements ApplicationRunner {

    private final AppUserService appUserService;

    @Override
    public void run(ApplicationArguments args) {
        appUserService.seedConfiguredAccounts();
        log.info(">>> [USERS] Comptes système (admin / superadmin) prêts");
    }
}
