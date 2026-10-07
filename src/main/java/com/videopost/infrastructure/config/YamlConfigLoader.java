package com.videopost.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carregador e persistidor de arquivos YAML para a configuração do VideoPost.
 */
public class YamlConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlConfigLoader.class);
    private final ObjectMapper yamlMapper;

    public YamlConfigLoader() {
        this.yamlMapper = new ObjectMapper(
                new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        );
        this.yamlMapper.registerModule(new JavaTimeModule());
        this.yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public AppConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            log.info("Arquivo de configuração não encontrado em {}. Criando configuração padrão.", configPath);
            AppConfig defaultConfig = new AppConfig();
            save(configPath, defaultConfig);
            return defaultConfig;
        }
        return yamlMapper.readValue(configPath.toFile(), AppConfig.class);
    }

    public void save(Path configPath, AppConfig config) throws IOException {
        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
    }
}
