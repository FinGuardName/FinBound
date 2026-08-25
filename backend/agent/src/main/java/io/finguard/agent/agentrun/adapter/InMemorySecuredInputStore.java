package io.finguard.agent.agentrun.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import io.finguard.agent.agentrun.domain.SecuredInputReference;
import io.finguard.agent.agentrun.port.SecuredInputStore;

@Repository
public class InMemorySecuredInputStore implements SecuredInputStore {
    private final Map<String, String> securedInputs = new ConcurrentHashMap<>();

    @Override
    public SecuredInputReference store(String inputText) {
        String inputRef = "INPUT-" + UUID.randomUUID();
        securedInputs.put(inputRef, inputText);
        return new SecuredInputReference(inputRef, sha256(inputText));
    }

    private String sha256(String inputText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inputText.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
