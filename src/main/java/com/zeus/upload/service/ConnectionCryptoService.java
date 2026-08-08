package com.zeus.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.EncryptedConnectionSecrets;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConnectionCryptoService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final ObjectMapper objectMapper;
    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public ConnectionCryptoService(ObjectMapper objectMapper, AppProperties appProperties) {
        this(objectMapper, appProperties.getConnectionMasterKey());
    }

    ConnectionCryptoService(ObjectMapper objectMapper, String encodedMasterKey) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.masterKey = parseKey(encodedMasterKey);
    }

    public boolean isConfigured() {
        return masterKey != null;
    }

    public EncryptedConnectionSecrets encrypt(Map<String, String> secrets, String associatedData) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(new LinkedHashMap<>(secrets)));
            return new EncryptedConnectionSecrets(encode(iv), encode(ciphertext));
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            throw new IllegalStateException("Could not encrypt connection secrets", ex);
        }
    }

    public Map<String, String> decrypt(EncryptedConnectionSecrets encrypted, String associatedData) {
        requireConfigured();
        Objects.requireNonNull(encrypted, "encrypted secrets must not be null");
        try {
            byte[] iv = Base64.getDecoder().decode(encrypted.getIv());
            byte[] ciphertext = Base64.getDecoder().decode(encrypted.getCiphertext());
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            return new LinkedHashMap<>(objectMapper.readValue(cipher.doFinal(ciphertext), new TypeReference<>() { }));
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt connection secrets", ex);
        }
    }

    private SecretKeySpec parseKey(String encodedMasterKey) {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) return null;
        try {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey.trim());
            if (key.length != 32) throw new IllegalArgumentException("Connection master key must decode to 32 bytes");
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Connection master key must be Base64 encoded and 256 bit", ex);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Encrypted connection profiles require app.connection master key or ZEUS_CONNECTION_MASTER_KEY");
        }
    }

    private byte[] aad(String associatedData) {
        return Objects.requireNonNull(associatedData, "associatedData must not be null")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
