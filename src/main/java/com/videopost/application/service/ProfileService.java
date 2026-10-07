package com.videopost.application.service;

import com.videopost.infrastructure.config.AppConfig;
import com.videopost.infrastructure.config.YamlConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Serviço de gerenciamento de múltiplos perfis de redes sociais.
 */
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final String DEFAULT_PROFILE = "default";

    private final Path rootDir;
    private final YamlConfigLoader configLoader;

    public ProfileService(Path rootDir, YamlConfigLoader configLoader) {
        this.rootDir = rootDir;
        this.configLoader = configLoader;
    }

    public record ProfileInfo(String name, boolean isActive) {}

    public void createProfile(String profileName) throws IOException {
        String clean = sanitize(profileName);
        Path profileDir = getProfilesDir().resolve(clean);
        Files.createDirectories(profileDir);

        Path credentialsDir = getCredentialsDir().resolve(clean);
        Files.createDirectories(credentialsDir);

        log.info("Perfil '{}' criado com sucesso em {}", clean, profileDir);
    }

    public List<ProfileInfo> listProfiles() {
        List<ProfileInfo> list = new ArrayList<>();
        String currentActive = getActiveProfile();

        Path profilesDir = getProfilesDir();
        if (Files.exists(profilesDir) && Files.isDirectory(profilesDir)) {
            try (Stream<Path> stream = Files.list(profilesDir)) {
                List<String> names = stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .toList();

                for (String name : names) {
                    list.add(new ProfileInfo(name, name.equalsIgnoreCase(currentActive)));
                }
            } catch (IOException e) {
                log.error("Erro ao listar perfis em {}: {}", profilesDir, e.getMessage());
            }
        }

        if (list.isEmpty()) {
            list.add(new ProfileInfo(DEFAULT_PROFILE, true));
        }

        return list;
    }

    public void useProfile(String profileName) throws IOException {
        String clean = sanitize(profileName);
        Path activeFile = rootDir.resolve("active_profile");
        Files.writeString(activeFile, clean, StandardCharsets.UTF_8);

        // Atualiza também no config.yml se existir
        Path configFile = rootDir.resolve("config.yml");
        if (Files.exists(configFile)) {
            AppConfig config = configLoader.load(configFile);
            config.setActiveProfile(clean);
            configLoader.save(configFile, config);
        }

        log.info("Perfil ativo alterado para '{}'", clean);
    }

    public String getActiveProfile() {
        Path activeFile = rootDir.resolve("active_profile");
        if (Files.exists(activeFile)) {
            try {
                String active = Files.readString(activeFile, StandardCharsets.UTF_8).trim();
                if (!active.isBlank()) {
                    return active;
                }
            } catch (IOException ignored) {
            }
        }

        Path configFile = rootDir.resolve("config.yml");
        if (Files.exists(configFile)) {
            try {
                AppConfig config = configLoader.load(configFile);
                if (config.getActiveProfile() != null && !config.getActiveProfile().isBlank()) {
                    return config.getActiveProfile().trim();
                }
            } catch (Exception ignored) {
            }
        }

        return DEFAULT_PROFILE;
    }

    private Path getProfilesDir() {
        return rootDir.resolve("profiles");
    }

    private Path getCredentialsDir() {
        return rootDir.resolve("credentials");
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome de perfil não pode ser vazio.");
        }
        return name.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
