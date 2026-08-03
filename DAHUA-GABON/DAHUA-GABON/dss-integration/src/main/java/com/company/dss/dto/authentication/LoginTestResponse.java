package com.company.dss.dto.authentication;

public record LoginTestResponse(
        boolean connected,
        String token
) {
}
