package com.company.dss.mq;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.company.dss.client.DssClient;
import com.company.dss.common.DssApiPaths;
import com.company.dss.dto.mq.MqConfigApiResponse;
import com.company.dss.dto.mq.MqConfigData;
import com.company.dss.exception.DssClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqConfigClient {

    private final DssClient dssClient;

    public MqConfigData fetchConfig() {
        log.info(">>> [MQ] Récupération GetMqConfig...");

        MqConfigApiResponse response = dssClient.post(
                DssApiPaths.GET_MQ_CONFIG,
                Map.of(),
                MqConfigApiResponse.class
        );
        if (response == null || !response.isSuccess()) {
            String desc = response != null ? response.desc() : "réponse nulle";
            throw new DssClientException("GetMqConfig échoué : " + desc);
        }
        log.info(">>> [MQ] GetMqConfig OK");
        return response.data();
    }
}
