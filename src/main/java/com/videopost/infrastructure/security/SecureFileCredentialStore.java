package com.videopost.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Armazenamento seguro de credenciais em disco criptografado com AES-256-GCM.
 * Cada perfil possui um subdiretório isolado.
 * Arquivos são salvos com permissões restritas (POSIX 0600) quando suportado.
 */
public class SecureFileCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(SecureFileCredentialStore.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 100_000;

    private final Path credentialsRoot;
    private final ObjectMapper objectMapper;
    private final String masterPassphrase;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecureFileCredentialStore(Path credentialsRoot, ObjectMapper objectMapper) {
        this.credentialsRoot = credentialsRoot;
        this.objectMapper = objectMapper;
        this.masterPassphrase = resolveMasterKey();
    }

    private String resolveMasterKey() {
        String envKey = System.getenv("VIDEOPOST_SECRET_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }

        // Recupera ou cria uma chave mestre local na home da usuária (~/.videopost_key)
        Path localKeyPath = Path.of(System.getProperty("user.home"), ".videopost_key");
        try {
            if (Files.exists(localKeyPath)) {
                return Files.readString(localKeyPath, StandardCharsets.UTF_8).trim();
            } else {
                byte[] seed = new byte[32];
                secureRandom.nextBytes(seed);
                StringBuilder sb = new StringBuilder();
                for (byte b : seed) {
                    sb.append(String.format("%02x", b));
                }
                String generated = sb.toString();
                Files.writeString(localKeyPath, generated, StandardCharsets.UTF_8);
                restrictPermissions(localKeyPath);
                return generated;
            }
        } catch (Exception e) {
            log.warn("Não foi possível acessar ~/.videopost_key, utilizando seed derivada do sistema.");
            return System.getProperty("user.name") + ":" + System.getProperty("os.name");
        }
    }

    @Override
    public void saveCredentials(String profile, Platform platform, Map<String, String> credentials) {
        try {
            Path profileDir = credentialsRoot.resolve(cleanName(profile));
            Files.createDirectories(profileDir);
            Path credFile = profileDir.resolve(platform.name().toLowerCase() + ".enc");

            String json = objectMapper.writeValueAsString(credentials);
            byte[] encrypted = encrypt(json.getBytes(StandardCharsets.UTF_8));

            Files.write(credFile, encrypted);
            restrictPermissions(credFile);
            log.info("Credenciais seguras salvas para o perfil '{}' na plataforma '{}'.", profile, platform);
        } catch (Exception e) {
            log.error("Erro ao criptografar/salvar credenciais: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao salvar credenciais com segurança", e);
        }
    }

    @Override
    public Optional<Map<String, String>> getCredentials(String profile, Platform platform) {
        Path credFile = credentialsRoot.resolve(cleanName(profile)).resolve(platform.name().toLowerCase() + ".enc");
        if (!Files.exists(credFile)) {
            return Optional.empty();
        }

        try {
            byte[] fileBytes = Files.readAllBytes(credFile);
            byte[] decrypted = decrypt(fileBytes);
            Map<String, String> creds = objectMapper.readValue(decrypted, new TypeReference<>() {});
            return Optional.of(creds);
        } catch (Exception e) {
            log.error("Erro ao descriptografar credenciais para o perfil '{}' e plataforma '{}': {}",
                    profile, platform, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean hasCredentials(String profile, Platform platform) {
        Path credFile = credentialsRoot.resolve(cleanName(profile)).resolve(platform.name().toLowerCase() + ".enc");
        return Files.exists(credFile);
    }

    @Override
    public void deleteCredentials(String profile, Platform platform) {
        Path credFile = credentialsRoot.resolve(cleanName(profile)).resolve(platform.name().toLowerCase() + ".enc");
        try {
            Files.deleteIfExists(credFile);
            log.info("Credenciais removidas para o perfil '{}' e plataforma '{}'.", profile, platform);
        } catch (IOException e) {
            log.error("Erro ao deletar credenciais: {}", e.getMessage(), e);
        }
    }

    private byte[] encrypt(byte[] plainBytes) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        SecretKey secretKey = deriveKey(masterPassphrase.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        byte[] cipherBytes = cipher.doFinal(plainBytes);

        ByteBuffer byteBuffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + cipherBytes.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(cipherBytes);
        return byteBuffer.array();
    }

    private byte[] decrypt(byte[] encryptedBytes) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);

        byte[] salt = new byte[SALT_LENGTH];
        byteBuffer.get(salt);

        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] cipherBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherBytes);

        SecretKey secretKey = deriveKey(masterPassphrase.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        return cipher.doFinal(cipherBytes);
    }

    private SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Em sistemas de arquivos sem suporte a POSIX (ex: Windows NTFS comum), ajusta via File API
            File file = path.toFile();
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        } catch (Exception ignored) {
        }
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "default";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
