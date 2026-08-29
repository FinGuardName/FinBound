package io.finguard.agent.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.finguard.agent.config.AgentProperties;
import reactor.core.publisher.Mono;

@Component
public class InternalCredentialWebFilter implements WebFilter {
    public static final String INTERNAL_CREDENTIAL_HEADER = "X-FinGuard-Internal-Credential";

    private static final byte[] UNAUTHORIZED_BODY = (
            "{\"errorCode\":\"INTERNAL_CREDENTIAL_INVALID\","
                    + "\"message\":\"A valid internal service credential is required\"}"
    ).getBytes(StandardCharsets.UTF_8);

    private final byte[] expectedCredential;

    public InternalCredentialWebFilter(AgentProperties properties) {
        expectedCredential = properties.internalCredential().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/internal/")) {
            return chain.filter(exchange);
        }

        String presentedCredential = exchange.getRequest().getHeaders()
                .getFirst(INTERNAL_CREDENTIAL_HEADER);
        if (credentialMatches(presentedCredential)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED_BODY);
        return exchange.getResponse().writeWith(Mono.just(body));
    }

    private boolean credentialMatches(String presentedCredential) {
        if (presentedCredential == null) {
            return false;
        }
        byte[] presentedBytes = presentedCredential.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedCredential, presentedBytes);
    }
}
