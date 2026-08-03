package com.company.dss.authentication;

import org.springframework.stereotype.Component;

import com.company.dss.client.DssClient;
import com.company.dss.common.DssApiPaths;
import com.company.dss.config.DssProperties;
import com.company.dss.dto.authentication.AuthChallengeRequest;
import com.company.dss.dto.authentication.AuthChallengeResponse;
import com.company.dss.dto.authentication.AuthCredentialsRequest;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.DssApiResponse;
import com.company.dss.exception.DssAuthenticationException;
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

        log.info("DSS — déclenchement du challenge d'authentification pour l'utilisateur {}", dssProperties.getUsername());

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

        String signature = DssSignatureUtils.computeSignature(
                dssProperties.getUsername(),
                dssProperties.getPassword(),
                challenge.realm(),
                challenge.randomKey()
        );

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
                "",
                "",
                DssApiPaths.LOGIN_TYPE
        );

        log.info("DSS — soumission des identifiants (randomKey valide 10 secondes)");

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
        tokenHolder.setToken(loginResponse.token(), durationMinutes);

        log.info("DSS — authentification réussie pour {}", loginResponse.userName());
        return loginResponse;
    }

    public void keepAlive() {
        tokenHolder.getToken().ifPresent(token -> {
            log.debug("DSS — envoi keep-alive");
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
