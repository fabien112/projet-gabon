package com.company.dss.client;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.common.DssApiPaths;
import com.company.dss.exception.DssClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client HTTP unique pour toutes les API Dahua DSS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DssClient {

    private final WebClient dssWebClient;
    private final TokenHolder tokenHolder;

    public <T> T get(String path, Class<T> responseType) {
        return get(path, Map.of(), responseType, true);
    }

    public <T> T get(String path, Map<String, ?> queryParams, Class<T> responseType) {
        return get(path, queryParams, responseType, true);
    }

    public <T> T get(String path, Map<String, ?> queryParams, Class<T> responseType, boolean withToken) {
        return exchange(HttpMethod.GET, path, queryParams, null, responseType, withToken);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return post(path, body, responseType, true);
    }

    public <T> T post(String path, Object body, Class<T> responseType, boolean withToken) {
        return exchange(HttpMethod.POST, path, Map.of(), body, responseType, withToken);
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        return put(path, body, responseType, true);
    }

    public <T> T put(String path, Object body, Class<T> responseType, boolean withToken) {
        return exchange(HttpMethod.PUT, path, Map.of(), body, responseType, withToken);
    }

    public <T> T delete(String path, Class<T> responseType) {
        return delete(path, Map.of(), responseType);
    }

    public <T> T delete(String path, Map<String, ?> queryParams, Class<T> responseType) {
        return exchange(HttpMethod.DELETE, path, queryParams, null, responseType, true);
    }

    /**
     * POST sans token — utilisé pour l'authentification initiale.
     * Accepte HTTP 401 comme réponse valide (challenge DSS).
     */
    public <T> T postAllowUnauthorized(String path, Object body, Class<T> responseType, int... acceptedStatuses) {
        try {
            return dssWebClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .exchangeToMono(response -> decodeResponse(response, responseType, acceptedStatuses))
                    .block();
        } catch (WebClientResponseException ex) {
            throw toDssClientException(ex);
        } catch (Exception ex) {
            throw new DssClientException("Erreur POST DSS sur " + path, ex);
        }
    }

    private <T> T exchange(
            HttpMethod method,
            String path,
            Map<String, ?> queryParams,
            Object body,
            Class<T> responseType,
            boolean withToken
    ) {
        try {
            WebClient.RequestBodyUriSpec requestSpec = dssWebClient.method(method);
            WebClient.RequestHeadersSpec<?> headersSpec = requestSpec.uri(uriBuilder -> {
                uriBuilder.path(path);
                queryParams.forEach(uriBuilder::queryParam);
                return uriBuilder.build();
            });

            if (withToken) {
                headersSpec = applyToken(headersSpec);
            }

            WebClient.ResponseSpec responseSpec = body != null
                    ? ((WebClient.RequestBodySpec) headersSpec).bodyValue(body).retrieve()
                    : headersSpec.retrieve();

            return responseSpec
                    .onStatus(status -> status.isError(), this::mapError)
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException ex) {
            throw toDssClientException(ex);
        } catch (DssClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DssClientException("Erreur " + method + " DSS sur " + path, ex);
        }
    }

    private WebClient.RequestHeadersSpec<?> applyToken(WebClient.RequestHeadersSpec<?> spec) {
        Optional<String> token = tokenHolder.getToken();
        if (token.isEmpty()) {
            throw new DssClientException("Aucun token DSS disponible — authentification requise");
        }
        return spec.header(DssApiPaths.TOKEN_HEADER, token.get());
    }

    private <T> Mono<T> decodeResponse(ClientResponse response, Class<T> responseType, int... acceptedStatuses) {
        int status = response.statusCode().value();
        boolean accepted = status < 400;
        for (int acceptedStatus : acceptedStatuses) {
            if (status == acceptedStatus) {
                accepted = true;
                break;
            }
        }
        if (!accepted) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new DssClientException(
                            "Réponse DSS inattendue HTTP " + status + " : " + body, status)));
        }
        return response.bodyToMono(responseType);
    }

    private Mono<? extends Throwable> mapError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new DssClientException(
                        "Erreur HTTP DSS " + response.statusCode().value() + " : " + body,
                        response.statusCode().value()));
    }

    private DssClientException toDssClientException(WebClientResponseException ex) {
        return new DssClientException(
                "Erreur HTTP DSS " + ex.getStatusCode().value() + " : " + ex.getResponseBodyAsString(),
                ex.getStatusCode().value());
    }

}
