package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthChallengeRequest(
        @JsonProperty("userName") String userName,
        String ipAddress,
        String clientType
) {
}
