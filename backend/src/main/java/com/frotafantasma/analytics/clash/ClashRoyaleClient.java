package com.frotafantasma.analytics.clash;

import com.frotafantasma.analytics.clash.dto.ClanApiDto;
import com.frotafantasma.analytics.clash.dto.ClanMembersApiDto;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ClashRoyaleClient {
    private final RestClient restClient;
    private final URI clanUri;

    public ClashRoyaleClient(ClashRoyaleProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                .build();
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        String encodedTag = URLEncoder.encode(properties.clanTag(), StandardCharsets.UTF_8);
        this.clanUri = URI.create(baseUrl + "/clans/" + encodedTag);
    }

    public ClanApiDto getClan() {
        return get(clanUri, ClanApiDto.class);
    }

    public ClanMembersApiDto getMembers() {
        return get(URI.create(clanUri + "/members"), ClanMembersApiDto.class);
    }

    private <T> T get(URI uri, Class<T> responseType) {
        try {
            T response = restClient.get().uri(uri).retrieve().body(responseType);
            if (response == null) {
                throw new ClashRoyaleException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "INVALID_UPSTREAM_RESPONSE",
                        "A API do Clash Royale retornou uma resposta vazia."
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) {
                throw new ClashRoyaleException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "CLAN_NOT_FOUND",
                        "Clã não encontrado na API do Clash Royale."
                );
            }
            if (status == 429) {
                throw new ClashRoyaleException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "UPSTREAM_RATE_LIMITED",
                        "A API do Clash Royale limitou as requisições. Tente novamente mais tarde."
                );
            }
            if (status == 503) {
                throw new ClashRoyaleException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "UPSTREAM_UNAVAILABLE",
                        "A API do Clash Royale está indisponível."
                );
            }
            throw new ClashRoyaleException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "UPSTREAM_ERROR",
                    "Falha ao consultar a API do Clash Royale."
            );
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ClashRoyaleException(
                        org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                        "UPSTREAM_TIMEOUT",
                        "A API do Clash Royale demorou para responder."
                );
            }
            throw new ClashRoyaleException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "UPSTREAM_CONNECTION_ERROR",
                    "Não foi possível conectar à API do Clash Royale."
            );
        } catch (RestClientException exception) {
            throw new ClashRoyaleException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "INVALID_UPSTREAM_RESPONSE",
                    "A API do Clash Royale retornou uma resposta inválida."
            );
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
