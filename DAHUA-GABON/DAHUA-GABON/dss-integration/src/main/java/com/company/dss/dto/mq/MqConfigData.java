package com.company.dss.dto.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MqConfigData(
        String enableTls,
        String userName,
        String mqtt,
        String amqp,
        String stomp,
        String wss,
        String addr,
        String password
) {
}
