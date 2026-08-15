package com.company.dss.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.company.dss.config.AppSecurityProperties;
import com.company.dss.persistence.entity.AppUserEntity;
import com.company.dss.persistence.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AppSecurityProperties props = new AppSecurityProperties();
    private AppUserService service;

    @BeforeEach
    void setUp() {
        service = new AppUserService(appUserRepository, passwordEncoder, props);
    }

    @Test
    void create_persistsEnabledUserRole() {
        when(appUserRepository.existsByUsernameIgnoreCase("lea")).thenReturn(false);
        when(appUserRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> {
            AppUserEntity user = inv.getArgument(0);
            user.setId(7L);
            return user;
        });

        AppUserEntity saved = service.create("lea", "Secret123");

        ArgumentCaptor<AppUserEntity> captor = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(appUserRepository).save(captor.capture());
        AppUserEntity persisted = captor.getValue();
        assertThat(persisted.getUsername()).isEqualTo("lea");
        assertThat(persisted.getRole()).isEqualTo("USER");
        assertThat(persisted.isEnabled()).isTrue();
        assertThat(persisted.isSystemUser()).isFalse();
        assertThat(passwordEncoder.matches("Secret123", persisted.getPasswordHash())).isTrue();
        assertThat(saved.getId()).isEqualTo(7L);
    }

    @Test
    void create_rejectsDuplicateUsername() {
        when(appUserRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);
        assertThatThrownBy(() -> service.create("admin", "Secret123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void create_rejectsShortPassword() {
        assertThatThrownBy(() -> service.create("lea", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 caractères");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void update_changesUsernameAndPassword() {
        AppUserEntity user = new AppUserEntity();
        user.setId(3L);
        user.setUsername("lea");
        user.setPasswordHash(passwordEncoder.encode("OldPass12"));
        user.setRole("USER");
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByUsernameIgnoreCase("lea.n")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserEntity updated = service.update(3L, "lea.n", "NewPass12", null);

        assertThat(updated.getUsername()).isEqualTo("lea.n");
        assertThat(passwordEncoder.matches("NewPass12", updated.getPasswordHash())).isTrue();
    }

    @Test
    void delete_rejectsSuperAdmin() {
        AppUserEntity superAdmin = new AppUserEntity();
        superAdmin.setId(1L);
        superAdmin.setUsername("superadmin");
        superAdmin.setRole("SUPERADMIN");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("superadmin");
        verify(appUserRepository, never()).delete(any());
    }

    @Test
    void setEnabled_rejectsSuperAdmin() {
        AppUserEntity superAdmin = new AppUserEntity();
        superAdmin.setId(1L);
        superAdmin.setUsername("superadmin");
        superAdmin.setRole("SUPERADMIN");
        superAdmin.setSystemUser(true);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> service.setEnabled(1L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("superadmin");
        verify(appUserRepository, never()).save(any());
    }
}
