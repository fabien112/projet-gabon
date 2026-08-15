package com.company.dss.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.user.AppUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/users")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(appUserService.list().stream()
                .map(AppUserService::toDto)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateUserRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppUserService.toDto(appUserService.create(body.username(), body.password())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PatchUserRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        return ResponseEntity.ok(AppUserService.toDto(
                appUserService.update(id, body.username(), body.password(), body.enabled())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        appUserService.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record CreateUserRequest(String username, String password) {
    }

    public record PatchUserRequest(String username, String password, Boolean enabled) {
    }
}
