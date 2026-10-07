package com.videopost.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.videopost.application.service.AuthService;
import com.videopost.application.service.ProfileService;
import com.videopost.application.service.PublishingCoordinator;
import com.videopost.application.service.QueueService;
import com.videopost.application.service.RetryService;
import com.videopost.application.service.ScanService;
import com.videopost.application.service.ScheduleRunner;
import com.videopost.domain.model.Platform;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.infrastructure.config.YamlConfigLoader;
import com.videopost.infrastructure.filesystem.JsonMetadataParser;
import com.videopost.infrastructure.filesystem.VideoFileScanner;
import com.videopost.infrastructure.http.JdkPlatformHttpClient;
import com.videopost.infrastructure.persistence.DatabaseConnectionManager;
import com.videopost.infrastructure.persistence.SqlitePublicationRepository;
import com.videopost.infrastructure.persistence.SqliteVideoRepository;
import com.videopost.infrastructure.security.CredentialStore;
import com.videopost.infrastructure.security.SecureFileCredentialStore;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import com.videopost.publisher.facebook.FacebookConfig;
import com.videopost.publisher.facebook.FacebookPublisher;
import com.videopost.publisher.http.PlatformHttpClient;
import com.videopost.publisher.kwai.KwaiConfig;
import com.videopost.publisher.kwai.KwaiPublisher;
import com.videopost.publisher.tiktok.TikTokConfig;
import com.videopost.publisher.tiktok.TikTokPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Contêiner de injeção de dependências e contexto do VideoPost CLI.
 * Gerencia ciclo de vida dos repositórios, serviços e adaptadores de publicação.
 */
public class AppContext {

    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private final Path rootDir;
    private final ObjectMapper objectMapper;
    private final YamlConfigLoader configLoader;
    private final AppConfig config;
    private final DatabaseConnectionManager dbManager;
    private final VideoRepository videoRepository;
    private final PublicationRepository publicationRepository;
    private final CredentialStore credentialStore;
    private final PlatformHttpClient httpClient;
    private final PublisherRegistry publisherRegistry;

    private final ProfileService profileService;
    private final AuthService authService;
    private final ScanService scanService;
    private final QueueService queueService;
    private final PublishingCoordinator publishingCoordinator;
    private final RetryService retryService;
    private final ScheduleRunner scheduleRunner;

    public AppContext(Path rootDir) {
        this.rootDir = rootDir;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.configLoader = new YamlConfigLoader();

        // Inicializa diretório base se não existir
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            log.error("Erro ao criar diretório base: {}", e.getMessage());
        }

        Path configFile = rootDir.resolve("config.yml");
        AppConfig loadedConfig;
        try {
            loadedConfig = configLoader.load(configFile);
        } catch (IOException e) {
            log.warn("Falha ao carregar config.yml, utilizando padrão: {}", e.getMessage());
            loadedConfig = new AppConfig();
        }
        this.config = loadedConfig;

        // Persistência SQLite
        Path dbPath = rootDir.resolve("videopost.db");
        this.dbManager = new DatabaseConnectionManager(dbPath);
        this.videoRepository = new SqliteVideoRepository(dbManager, objectMapper);
        this.publicationRepository = new SqlitePublicationRepository(dbManager);

        // Segurança e Credenciais
        Path credentialsDir = rootDir.resolve("credentials");
        this.credentialStore = new SecureFileCredentialStore(credentialsDir, objectMapper);

        // HTTP e Registro de Publishers
        this.httpClient = new JdkPlatformHttpClient();
        this.publisherRegistry = new PublisherRegistry();

        // Serviços
        this.profileService = new ProfileService(rootDir, configLoader);
        this.authService = new AuthService(credentialStore);

        // Registra adaptadores de plataforma para o perfil ativo
        initPublishers();

        JsonMetadataParser metadataParser = new JsonMetadataParser(objectMapper);
        VideoFileScanner scanner = new VideoFileScanner(metadataParser);

        this.scanService = new ScanService(scanner, videoRepository);
        this.queueService = new QueueService(videoRepository, publicationRepository);
        this.publishingCoordinator = new PublishingCoordinator(videoRepository, publicationRepository, publisherRegistry);
        this.retryService = new RetryService(videoRepository, publicationRepository, publisherRegistry);
        this.scheduleRunner = new ScheduleRunner(scanService, publishingCoordinator, videoRepository, publisherRegistry, config);
    }

    public static AppContext defaultContext() {
        return new AppContext(Path.of(".videopost"));
    }

    public void initPublishers() {
        String activeProfile = profileService.getActiveProfile();

        // 1. TikTok Publisher
        Optional<Map<String, String>> tiktokCreds = credentialStore.getCredentials(activeProfile, Platform.TIKTOK);
        TikTokConfig tiktokConfig = null;
        if (tiktokCreds.isPresent()) {
            tiktokConfig = new TikTokConfig(
                    tiktokCreds.get().get("accessToken"),
                    tiktokCreds.get().get("privacyLevel"),
                    false, false, false
            );
        }
        publisherRegistry.register(new TikTokPublisher(tiktokConfig, httpClient, objectMapper));

        // 2. Facebook Publisher
        Optional<Map<String, String>> fbCreds = credentialStore.getCredentials(activeProfile, Platform.FACEBOOK);
        FacebookConfig fbConfig = null;
        if (fbCreds.isPresent()) {
            fbConfig = new FacebookConfig(
                    fbCreds.get().get("pageId"),
                    fbCreds.get().get("pageAccessToken"),
                    "v20.0"
            );
        }
        publisherRegistry.register(new FacebookPublisher(fbConfig, httpClient, objectMapper));

        // 3. Kwai Publisher
        Optional<Map<String, String>> kwaiCreds = credentialStore.getCredentials(activeProfile, Platform.KWAI);
        KwaiConfig kwaiConfig = null;
        if (kwaiCreds.isPresent()) {
            kwaiConfig = new KwaiConfig(
                    kwaiCreds.get().get("appId"),
                    kwaiCreds.get().get("accessToken")
            );
        }
        publisherRegistry.register(new KwaiPublisher(kwaiConfig, httpClient, objectMapper));
    }

    public Path getRootDir() {
        return rootDir;
    }

    public AppConfig getConfig() {
        return config;
    }

    public YamlConfigLoader getConfigLoader() {
        return configLoader;
    }

    public VideoRepository getVideoRepository() {
        return videoRepository;
    }

    public PublicationRepository getPublicationRepository() {
        return publicationRepository;
    }

    public CredentialStore getCredentialStore() {
        return credentialStore;
    }

    public PublisherRegistry getPublisherRegistry() {
        return publisherRegistry;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public ScanService getScanService() {
        return scanService;
    }

    public QueueService getQueueService() {
        return queueService;
    }

    public PublishingCoordinator getPublishingCoordinator() {
        return publishingCoordinator;
    }

    public RetryService getRetryService() {
        return retryService;
    }

    public ScheduleRunner getScheduleRunner() {
        return scheduleRunner;
    }
}
