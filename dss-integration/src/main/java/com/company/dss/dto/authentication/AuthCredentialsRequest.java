package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthCredentialsRequest(
        String mac,
        String deviceSN,
        String signature,
        @JsonProperty("userName") String userName,
        String randomKey,
        String publicKey,
        String ipAddress,
        String clientType,
        String userType,
        String secretKey,
        String secretVector,
        String loginType,
        /** 1 = reprendre / forcer la session si le compte est déjà connecté côté DSS. */
        String reused
) {
}
