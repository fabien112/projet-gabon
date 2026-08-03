package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthChallengeResponse(
        String realm,
        String randomKey,
        String encryptType,
        @JsonProperty("publickey") String publicKey
) {
}
