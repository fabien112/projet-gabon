package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthLoginResponse(
        String token,
        Integer duration,
        Integer tokenRate,
        String credential,
        String userId,
        String userName,
        String userGroupId,
        @JsonProperty("code") Integer errorCode,
        String desc
) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
