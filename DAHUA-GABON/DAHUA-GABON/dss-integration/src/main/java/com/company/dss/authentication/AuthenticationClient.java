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

    private final DssClient dssClient;
    private final DssProperties dssProperties;
    private final TokenHolder tokenHolder;

    public AuthLoginResponse login() {
        dssProperties.validateForLogin();

        log.info("------------------------------------------------------------");
        log.info(">>> [LOGIN] Étape 1/2 — challenge pour utilisateur '{}'", dssProperties.getUsername());

        AuthChallengeRequest challengeRequest = new AuthChallengeRequest(
                dssProperties.getUsername(),
                "",
                DssApiPaths.CLIENT_TYPE
        );

        AuthChallengeResponse challenge = dssClient.postAllowUnauthorized(
                DssApiPaths.AUTHORIZE,
                challengeRequest,
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

        AuthCredentialsRequest credentialsRequest = new AuthCredentialsRequest(
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
                StringUtils.hasText(dssProperties.getLoginType())
                        ? dssProperties.getLoginType()
                        : DssApiPaths.LOGIN_TYPE
        );

        log.info(">>> [LOGIN] Étape 2/2 — soumission credentials (loginType={})",
                StringUtils.hasText(dssProperties.getLoginType())
                        ? dssProperties.getLoginType()
                        : DssApiPaths.LOGIN_TYPE);

        AuthLoginResponse loginResponse = dssClient.post(
                DssApiPaths.AUTHORIZE,
                credentialsRequest,
                AuthLoginResponse.class,
                false
        );

        if (!loginResponse.hasToken()) {
            String detail = loginResponse.desc() != null ? loginResponse.desc() : "token absent";
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

        log.info(">>> [LOGIN] SUCCÈS — user={}, userId={}, userGroupId={}, durée={} min",
                loginResponse.userName(), loginResponse.userId(), loginResponse.userGroupId(), durationMinutes);
        return loginResponse;
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
            log.debug("DSS — renouvellement du token");
            AuthLoginResponse response = dssClient.post(
                    DssApiPaths.UPDATE_TOKEN,
                    java.util.Map.of("token", token),
                    AuthLoginResponse.class
            );
            if (response.hasToken()) {
                int durationMinutes = response.duration() != null ? response.duration() : 30;
                tokenHolder.setToken(response.token(), durationMinutes);
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
        log.info("DSS — déconnexion effectuée");
    }
}
