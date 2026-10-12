package com.frotafantasma.analytics.client;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.clash.ClanApiDto;
import com.frotafantasma.analytics.dto.clash.ClanMembersApiDto;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final ClashRoyaleProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;
    private final AtomicReference<Instant> retryAllowedAt = new AtomicReference<>(Instant.MIN);

    @Autowired
    public ClashRoyaleClient(ClashRoyaleProperties properties) {
        this(properties, Clock.systemUTC(), Thread::sleep);
    }

    ClashRoyaleClient(ClashRoyaleProperties properties, Clock clock, Sleeper sleeper) {
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
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
        for (int attempt = 1; ; attempt++) {
            checkRateLimit();
            try {
                T response = restClient.get().uri(uri).retrieve().body(responseType);
                if (response == null) {
                    throw invalidResponse();
                }
                return response;
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                if (status == 429) {
                    Duration cooldown = parseRetryAfter(exception.getResponseHeaders());
                    Instant nextAttempt = clock.instant().plus(cooldown);
                    retryAllowedAt.accumulateAndGet(nextAttempt, (previous, next) -> previous.isAfter(next) ? previous : next);
                    throw rateLimited(cooldown);
                }
                boolean transientFailure = status == 500 || status == 502 || status == 503 || status == 504;
                if (!transientFailure || attempt >= properties.maxAttempts()) {
                    throw translateStatus(status);
                }
            } catch (ResourceAccessException exception) {
                if (attempt >= properties.maxAttempts()) {
                    if (hasTimeoutCause(exception)) {
                        throw new ClashRoyaleException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT",
                                "A API do Clash Royale demorou para responder.");
                    }
                    throw new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "UPSTREAM_CONNECTION_ERROR",
                            "Não foi possível conectar à API do Clash Royale.");
                }
            } catch (RestClientException exception) {
                throw invalidResponse();
            }
            pauseBeforeRetry(attempt);
        }
    }

    private void checkRateLimit() {
        Duration remaining = Duration.between(clock.instant(), retryAllowedAt.get());
        if (remaining.isPositive()) {
            throw rateLimited(remaining);
        }
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds > 0 && seconds <= 86_400) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                try {
                    Instant date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    Duration delay = Duration.between(clock.instant(), date);
                    if (delay.isPositive() && delay.compareTo(Duration.ofDays(1)) <= 0) {
                        return delay;
                    }
                } catch (java.time.format.DateTimeParseException invalidDate) {
                    // Fall back to the configured cooldown without logging external content.
                }
            }
        }
        return properties.rateLimitCooldown();
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            sleeper.sleep(properties.retryDelay().multipliedBy(attempt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClashRoyaleException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_INTERRUPTED",
                    "A consulta foi interrompida. Tente novamente mais tarde.");
        }
    }

    private ClashRoyaleException translateStatus(int status) {
        return switch (status) {
            case 404 -> new ClashRoyaleException(HttpStatus.NOT_FOUND, "CLAN_NOT_FOUND",
                    "Clã não encontrado na API do Clash Royale.");
            case 503 -> new ClashRoyaleException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                    "A API do Clash Royale está indisponível.");
            default -> new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "Falha ao consultar a API do Clash Royale.");
        };
    }

    private ClashRoyaleException invalidResponse() {
        return new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "INVALID_UPSTREAM_RESPONSE",
                "A API do Clash Royale retornou uma resposta inválida.");
    }

    private ClashRoyaleException rateLimited(Duration delay) {
        long seconds = Math.max(1, (delay.toMillis() + 999) / 1_000);
        return new ClashRoyaleException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_RATE_LIMITED",
                "A API do Clash Royale limitou as requisições. Tente novamente mais tarde.", seconds);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
