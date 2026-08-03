package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        String loginType
) {
}
