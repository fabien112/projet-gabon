package com.company.dss.config;

import java.time.Duration;

import javax.net.ssl.SSLException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DssWebClientConfig {

    private final DssProperties dssProperties;
    private final ObjectMapper objectMapper;

    @Bean
    WebClient dssWebClient() throws SSLException {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(dssProperties.getReadTimeout())
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) dssProperties.getConnectTimeout().toMillis());

        if (!dssProperties.isVerifySsl()) {
            log.warn("Vérification SSL DSS désactivée (DSS_VERIFY_SSL=false) — à utiliser uniquement en environnement de confiance");
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
        }

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                    configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
                })
                .build();

        return WebClient.builder()
                .baseUrl(dssProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json;charset=UTF-8")
                .exchangeStrategies(strategies)
                .build();
    }
}
