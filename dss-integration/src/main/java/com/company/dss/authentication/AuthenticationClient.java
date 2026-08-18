package com.company.dss.authentication;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.company.dss.client.DssClient;
import com.company.dss.common.DssApiPaths;
import com.company.dss.config.DssProperties;
import com.company.dss.dto.authentication.AuthChallengeRequest;
import com.company.dss.dto.authentication.AuthChallengeResponse;
import com.company.dss.dto.authentication.AuthCredentialsRequest;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.DssApiResponse;
import com.company.dss.exception.DssAuthenticationException;
import com.company.dss.util.DssCryptoUtils;
import com.company.dss.util.DssSignatureUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client bas niveau pour les endpoints d'authentification BRMS DSS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationClient {

    private static final int MAX_TAKEOVER_ATTEMPTS = 6;
    private static final long TAKEOVER_PAUSE_MS = 2_000L;

    private final DssClient dssClient;
    private final DssProperties dssProperties;
    private final TokenHolder tokenHolder;
    private final DssSessionPersistence sessionPersistence;

    public AuthLoginResponse login() {
        return loginWithRecovery(false);
    }

    /**
     * Login avec reprises automatiques si le compte est déjà connecté ailleurs sur DSS.
     */
    public AuthLoginResponse loginWithRecovery(boolean forceTakeover) {
        dssProperties.validateForLogin();
        DssAuthenticationException last = null;
        int attempts = forceTakeover ? MAX_TAKEOVER_ATTEMPTS : 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String loginType = resolveLoginType(attempt);
                String reused = forceTakeover && attempt > 1 ? "1" : null;
                return loginOnce(loginType, reused);
            } catch (DssAuthenticationException ex) {
                last = ex;
                if (!forceTakeover || !isAlreadyLoggedIn(ex) || attempt >= attempts) {
                    throw ex;
                }
                log.warn(">>> [LOGIN] Compte déjà connecté sur DSS — reprise {}/{} dans {}s…",
                        attempt, attempts, TAKEOVER_PAUSE_MS / 1000);
                sleepQuietly(TAKEOVER_PAUSE_MS);
            }
        }
        throw last != null ? last : new DssAuthenticationException("Échec du login DSS");
    }

    private AuthLoginResponse loginOnce(String loginType, String reused) {
        log.info("------------------------------------------------------------");
        log.info(">>> [LOGIN] Étape 1/2 — challenge pour utilisateur '{}'", dssProperties.getUsername());

        AuthChallengeResponse challenge = dssClient.postAllowUnauthorized(
                DssApiPaths.AUTHORIZE,
                new AuthChallengeRequest(dssProperties.getUsername(), "", DssApiPaths.CLIENT_TYPE),
                AuthChallengeResponse.class,
                401
        );

        if (challenge.realm() == null || challenge.randomKey() == null) {
            throw new DssAuthenticationException(
                    "Challenge DSS invalide : realm ou randomKey manquant dans la réponse");
        }
        log.info(">>> [LOGIN] Challenge OK (realm={}, publickey={})",
                challenge.realm(), StringUtils.hasText(challenge.publicKey()) ? "oui" : "non");

        String signature = DssSignatureUtils.computeSignature(
                dssProperties.getUsername(),
                dssProperties.getPassword(),
                challenge.realm(),
                challenge.randomKey()
        );

        DssCryptoUtils.AesCredentials aes = DssCryptoUtils.generateAesCredentials();
        String encryptedSecretKey = "";
        String encryptedSecretVector = "";

        if (StringUtils.hasText(challenge.publicKey())) {
            encryptedSecretKey = DssCryptoUtils.encryptForPlatform(aes.secretKey(), challenge.publicKey());
            encryptedSecretVector = DssCryptoUtils.encryptForPlatform(aes.secretVector(), challenge.publicKey());
        } else {
            log.warn(">>> [LOGIN] publickey absente — déchiffrement MQ impossible");
        }

        log.info(">>> [LOGIN] Étape 2/2 — soumission credentials (loginType={}, reused={})",
                loginType, reused != null ? reused : "non");

        AuthLoginResponse loginResponse = dssClient.post(
                DssApiPaths.AUTHORIZE,
                new AuthCredentialsRequest(
                        "",
                        "",
                        signature,
                        dssProperties.getUsername(),
                        challenge.randomKey(),
                        "",
                        "",
                        DssApiPaths.CLIENT_TYPE,
                        DssApiPaths.USER_TYPE,
                        encryptedSecretKey,
                        encryptedSecretVector,
                        loginType,
                        reused
                ),
                AuthLoginResponse.class,
                false
        );

        if (!loginResponse.hasToken()) {
            String detail = loginResponse.desc() != null ? loginResponse.desc() : "token absent";
            Integer code = loginResponse.errorCode();
            if (code != null) {
                throw new DssAuthenticationException("Échec du login DSS (code " + code + ") : " + detail);
            }
            throw new DssAuthenticationException("Échec du login DSS : " + detail);
        }

        int durationMinutes = loginResponse.duration() != null ? loginResponse.duration() : 30;
        tokenHolder.setSession(
                loginResponse.token(),
                durationMinutes,
                loginResponse.userId(),
                loginResponse.userGroupId(),
                StringUtils.hasText(challenge.publicKey()) ? aes.secretKey() : null,
                StringUtils.hasText(challenge.publicKey()) ? aes.secretVector() : null
        );
        sessionPersistence.save(tokenHolder.snapshot().orElse(null));

        log.info(">>> [LOGIN] SUCCÈS — user={}, userId={}, userGroupId={}, durée={} min",
                loginResponse.userName(), loginResponse.userId(), loginResponse.userGroupId(), durationMinutes);
        return loginResponse;
    }

    static boolean isAlreadyLoggedIn(DssAuthenticationException ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("logged in") || msg.contains("code 4") || msg.contains("code 2004");
    }

    private String resolveLoginType(int attempt) {
        if (attempt <= 2) {
            return StringUtils.hasText(dssProperties.getLoginType())
                    ? dssProperties.getLoginType()
                    : DssApiPaths.LOGIN_TYPE;
        }
        return attempt % 2 == 0 ? DssApiPaths.LOGIN_TYPE : DssApiPaths.LOGIN_TYPE_MULTI_SITE;
    }

    public boolean validatePersistedSession() {
        if (!tokenHolder.hasValidToken()) {
            return false;
        }
        try {
            keepAlive();
            tokenHolder.extendExpiry(30);
            sessionPersistence.save(tokenHolder.snapshot().orElse(null));
            return true;
        } catch (Exception ex) {
            log.warn(">>> [LOGIN] Session persistée refusée par DSS : {}", ex.getMessage());
            tokenHolder.clear();
            return false;
        }
    }

    public void keepAlive() {
        tokenHolder.getToken().ifPresent(token -> {
            dssClient.put(
                    DssApiPaths.KEEP_ALIVE,
                    java.util.Map.of("token", token),
                    DssApiResponse.class
            );
        });
    }

    public void updateToken() {
        tokenHolder.getToken().ifPresent(token -> {
            log.debug(">>> [LOGIN] Renouvellement token DSS");
            AuthLoginResponse response = dssClient.post(
                    DssApiPaths.UPDATE_TOKEN,
                    java.util.Map.of("token", token),
                    AuthLoginResponse.class
            );
            if (response.hasToken()) {
                int durationMinutes = response.duration() != null ? response.duration() : 30;
                tokenHolder.setToken(response.token(), durationMinutes);
                sessionPersistence.save(tokenHolder.snapshot().orElse(null));
            }
        });
    }

    public void logout() {
        tokenHolder.getToken().ifPresent(token -> {
            try {
                dssClient.post(
                        DssApiPaths.UNAUTHORIZE,
                        java.util.Map.of("token", token),
                        DssApiResponse.class
                );
            } finally {
                tokenHolder.clear();
            }
        });
        if (!tokenHolder.hasValidToken()) {
            tokenHolder.clear();
        }
        sessionPersistence.clear();
        log.info("DSS — déconnexion effectuée");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
