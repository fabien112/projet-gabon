package com.company.dss.dto.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MqConfigApiResponse(
        Integer code,
        String desc,
        MqConfigData data
) {

    public boolean isSuccess() {
        return code != null && code == 1000 && data != null;
    }
}
