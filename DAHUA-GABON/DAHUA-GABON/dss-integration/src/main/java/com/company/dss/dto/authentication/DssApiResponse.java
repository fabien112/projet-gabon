package com.company.dss.dto.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DssApiResponse(
        Integer code,
        String desc,
        Object data
) {

    public boolean isSuccess() {
        return code != null && code == 1000;
    }
}
