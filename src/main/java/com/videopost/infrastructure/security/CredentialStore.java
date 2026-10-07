package com.videopost.infrastructure.security;

import com.videopost.domain.model.Platform;

import java.util.Map;
import java.util.Optional;

/**
 * Contrato para armazenamento e recuperação segura de credenciais por perfil e plataforma.
 */
public interface CredentialStore {

    void saveCredentials(String profile, Platform platform, Map<String, String> credentials);

    Optional<Map<String, String>> getCredentials(String profile, Platform platform);

    boolean hasCredentials(String profile, Platform platform);

    void deleteCredentials(String profile, Platform platform);
}
