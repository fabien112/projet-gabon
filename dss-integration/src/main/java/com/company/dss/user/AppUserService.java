package com.company.dss.user;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.config.AppSecurityProperties;
import com.company.dss.persistence.entity.AppUserEntity;
import com.company.dss.persistence.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppUserService {

    static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSecurityProperties securityProperties;

    @Transactional(readOnly = true)
    public List<AppUserEntity> list() {
        return appUserRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public AppUserEntity create(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        if (appUserRepository.existsByUsernameIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Cet identifiant existe déjà");
        }
        validatePassword(rawPassword);

        AppUserEntity user = new AppUserEntity();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setEnabled(true);
        user.setSystemUser(false);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUserEntity update(Long id, String username, String rawPassword, Boolean enabled) {
        AppUserEntity user = requireUser(id);
        boolean changed = false;

        if (StringUtils.hasText(username)) {
            String normalized = normalizeUsername(username);
            if (!normalized.equalsIgnoreCase(user.getUsername())) {
                appUserRepository.findByUsernameIgnoreCase(normalized)
                        .filter(other -> !other.getId().equals(id))
                        .ifPresent(other -> {
                            throw new IllegalArgumentException("Cet identifiant existe déjà");
                        });
                user.setUsername(normalized);
                changed = true;
            }
        }

        if (StringUtils.hasText(rawPassword)) {
            validatePassword(rawPassword);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            changed = true;
        }

        if (enabled != null && enabled != user.isEnabled()) {
            applyEnabled(user, enabled);
            changed = true;
        }

        if (!changed) {
            throw new IllegalArgumentException("Aucune modification à enregistrer");
        }
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUserEntity setEnabled(Long id, boolean enabled) {
        AppUserEntity user = requireUser(id);
        applyEnabled(user, enabled);
        return appUserRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        AppUserEntity user = requireUser(id);
        if (isSuperAdmin(user)) {
            throw new IllegalArgumentException("Le superadmin ne peut pas être supprimé");
        }
        appUserRepository.delete(user);
    }

    private AppUserEntity requireUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }

    private void applyEnabled(AppUserEntity user, boolean enabled) {
        if (isSuperAdmin(user) && !enabled) {
            throw new IllegalArgumentException("Le superadmin ne peut pas être désactivé");
        }
        user.setEnabled(enabled);
    }

    private static boolean isSuperAdmin(AppUserEntity user) {
        return "SUPERADMIN".equalsIgnoreCase(user.getRole());
    }

    @Transactional
    public void seedConfiguredAccounts() {
        upsertSystemUser(
                securityProperties.getSuperadminUsername(),
                securityProperties.getSuperadminPassword(),
                "SUPERADMIN"
        );
        if (securityProperties.getUsername() != null
                && !securityProperties.getUsername().equalsIgnoreCase(securityProperties.getSuperadminUsername())) {
            upsertSystemUser(
                    securityProperties.getUsername(),
                    securityProperties.getPassword(),
                    "USER"
            );
        }
    }

    private void upsertSystemUser(String username, String rawPassword, String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            return;
        }
        String normalized = username.trim();
        if (appUserRepository.findByUsernameIgnoreCase(normalized).isPresent()) {
            return;
        }
        AppUserEntity user = new AppUserEntity();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user.setSystemUser(true);
        appUserRepository.save(user);
    }

    static String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Identifiant requis");
        }
        String normalized = username.trim();
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Identifiant : 3 à 64 caractères (lettres, chiffres, . _ -)");
        }
        return normalized;
    }

    static void validatePassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Mot de passe : 8 caractères minimum");
        }
        if (rawPassword.length() > 128) {
            throw new IllegalArgumentException("Mot de passe trop long");
        }
    }

    public static Map<String, Object> toDto(AppUserEntity user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("role", user.getRole());
        dto.put("superAdmin", "SUPERADMIN".equalsIgnoreCase(user.getRole()));
        dto.put("enabled", user.isEnabled());
        dto.put("systemUser", user.isSystemUser());
        dto.put("createdAt", user.getCreatedAt());
        return dto;
    }
}
